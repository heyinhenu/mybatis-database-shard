package com.gimc.mybatis.db.shard;


import com.gimc.mybatis.db.shard.audit.ISqlAuditor;
import com.gimc.mybatis.db.shard.datasources.IShardDataSourceService;
import com.gimc.mybatis.db.shard.datasources.ShardDataSourceDescriptor;
import com.gimc.mybatis.db.shard.merger.IMerger;
import com.gimc.mybatis.db.shard.router.IShardRouter;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import com.gimc.mybatis.db.shard.support.execution.ConcurrentRequest;
import com.gimc.mybatis.db.shard.support.execution.DefaultConcurrentRequestProcessor;
import com.gimc.mybatis.db.shard.support.execution.IConcurrentRequestProcessor;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;
import com.gimc.mybatis.db.shard.support.utils.MapUtils;
import com.gimc.mybatis.db.shard.support.vo.BatchInsertTask;
import com.gimc.mybatis.db.shard.support.vo.ShardMRBase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;


public class ShardSqlSessionTemplate extends SqlSessionTemplate implements InitializingBean {

    private transient Logger logger = LoggerFactory.getLogger(ShardSqlSessionTemplate.class);

    private static final String DEFAULT_DATASOURCE_IDENTITY = "_ShardSqlSessionTemplate_default_data_source_name";

    private String defaultDataSourceName = DEFAULT_DATASOURCE_IDENTITY;

    private List<ExecutorService> internalExecutorServiceRegistry = new ArrayList<ExecutorService>();

    private IShardDataSourceService shardDataSourceService;

    private IShardRouter<MyBatisRoutingFact> router;

    private ISqlAuditor sqlAuditor;
    private ExecutorService sqlAuditorExecutor;

    /**
     * setup ExecutorService for data access requests on each data sources.<br>
     * map key(String) is the identity of DataSource; map value(ExecutorService)
     * is the ExecutorService that will be used to execute query requests on the
     * key's data source.
     */
    private Map<String, ExecutorService> dataSourceSpecificExecutors = new HashMap<String, ExecutorService>();

    private IConcurrentRequestProcessor concurrentRequestProcessor;

    /**
     * timeout threshold to indicate how long the concurrent data access request
     * should time out.<br>
     * time unit in milliseconds.<br>
     */
    private int defaultQueryTimeout = 100;
    /**
     * indicator to indicate whether to log/profile long-time-running SQL
     */
    private boolean profileLongTimeRunningSql = false;
    private long longTimeRunningSqlIntervalThreshold;

    /**
     * In fact, application can do data-merging in their application code after
     * getting the query result, but they can let
     * {@link ShardSqlSessionTemplate} do this for them too, as long as they
     * provide a relationship mapping between the sql action and the merging
     * logic provider.
     */
    private Map<String, IMerger<Object, Object>> mergers = new HashMap<String, IMerger<Object, Object>>();

    public ShardSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        super(sqlSessionFactory);
    }

    public ShardSqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType) {
        super(sqlSessionFactory, executorType);
    }

    public ShardSqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
        PersistenceExceptionTranslator exceptionTranslator) {
        super(sqlSessionFactory, executorType, exceptionTranslator);
    }

    @Override
    public int delete(String statementName) throws DataAccessException {
        return this.delete(statementName, null);
    }

    /**
     * @param statementName heyin
     * @param parameterObject heyin
     */
    @Override
    public int delete(final String statementName, final Object parameterObject) {
        auditSqlIfNecessary(statementName, parameterObject);
        long startTimestamp = System.currentTimeMillis();
        try {
            if (isPartitioningBehaviorEnabled()) {
                SortedMap<String, DataSource> dsMap = lookupDataSourcesByRouter(statementName, parameterObject);
                if (!MapUtils.isEmpty(dsMap)) {
                    SqlSessionCallBack action = new SqlSessionCallBack() {
                        @Override
                        public Object execute(SqlSession session) {
                            return session.delete(statementName, parameterObject);
                        }
                    };
                    if (dsMap.size() == 1) {
                        DataSource dataSource = dsMap.get(dsMap.firstKey());
                        return (Integer) executeWith(dataSource, action);
                    } else {
                        List<Object> results = executeInConcurrency(dsMap, action);
                        Integer rowAffacted = 0;
                        for (Object i : results) {
                            rowAffacted += (Integer) i;
                        }
                        return rowAffacted;
                    }
                }
            }
            // end if for partitioning status checking
            return super.delete(statementName, parameterObject);
        } finally {
            if (isProfileLongTimeRunningSql()) {
                long interval = System.currentTimeMillis() - startTimestamp;
                if (interval > getLongTimeRunningSqlIntervalThreshold()) {
                    logger
                        .warn("SQL Statement [{}] with parameter object [{}] ran out of the normal time range, it consumed [{}] milliseconds.", new Object[]{
                            statementName, parameterObject, interval});
                }
            }
        }
    }


    @Override
    public int insert(String statementName) throws DataAccessException {
        return this.insert(statementName, null);
    }

    /**
     * We support insert in 3 ways here:<br>
     *
     * <pre>
     *      1- if no partitioning requirement is found:
     *          the insert will be delegated to the default insert behavior of {@link SqlSession};
     *      2- if partitioning support is enabled and 'parameterObject' is NOT a type of collection:
     *          we will search for routing rules against it and execute insertion as per the rule if found,
     *          if no rule is found, the default data source will be used.
     *      3- if partitioning support is enabled and 'parameterObject' is a type of {@link BatchInsertTask}:
     *           this is a specific solution, mainly aimed for "insert into ..values(), (), ()" style insertion.
     *           In this situation, we will regroup the entities in the original collection into several sub-collections as per routing rules,
     *           and submit the regrouped sub-collections to their corresponding target data sources.
     *           One thing to NOTE: in this situation, although we return a object as the result of insert, but it doesn't mean any thing to you,
     *           because, "insert into ..values(), (), ()" style SQL doesn't return you a sensible primary key in this way.
     *           this, function is optional, although we return a list of sub-insert result, but don't guarantee precise semantics.
     * </pre>
     *
     * we can't just decide the execution branch on the Collection<?> type of
     * the 'parameterObject', because sometimes, maybe the application does want
     * to do insertion as per the parameterObject of its own.<br>
     */
    @Override
    public int insert(final String statementName, final Object parameterObject) throws DataAccessException {
        auditSqlIfNecessary(statementName, parameterObject);
        long startTimestamp = System.currentTimeMillis();
        try {
            if (isPartitioningBehaviorEnabled()) {
                /**
                 * sometimes, client will submit batch insert request like
                 * "insert into ..values(), (), ()...", it's a rare situation,
                 * but does exist, so we will create new executor on this kind
                 * of request processing, and map each values to their target
                 * data source and then reduce to sub-collection, finally,
                 * submit each sub-collection of entities to executor to
                 * execute.
                 */
                if (parameterObject != null && parameterObject instanceof BatchInsertTask) {
                    // map collection into mapping of data source and sub collection of entities
                    logger.info("start to prepare batch insert operation with parameter type of:{}.", parameterObject.getClass());
                    return batchInsertAfterReordering(statementName, parameterObject);
                } else {
                    DataSource targetDataSource = null;
                    SqlSessionCallBack action = new SqlSessionCallBack() {
                        public Object execute(SqlSession session) throws SQLException {
                            return session.insert(statementName, parameterObject);
                        }
                    };
                    SortedMap<String, DataSource> resultDataSources = lookupDataSourcesByRouter(statementName, parameterObject);
                    if (MapUtils.isEmpty(resultDataSources) || resultDataSources.size() == 1) {
                        // fall back to default data source.
                        targetDataSource = getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource();
                        if (resultDataSources.size() == 1) {
                            targetDataSource = resultDataSources.values().iterator().next();
                        }
                        return (Integer) executeWith(targetDataSource, action);
                    } else {
                        int counter = 0;
                        List<Object> resultInt = executeInConcurrency(resultDataSources, action);
                        for (Object obj : resultInt) {
                            counter += (Integer) obj;
                        }
                        return counter;
                    }
                }
            }
            return super.insert(statementName, parameterObject);
        } finally {
            if (isProfileLongTimeRunningSql()) {
                long interval = System.currentTimeMillis() - startTimestamp;
                if (interval > getLongTimeRunningSqlIntervalThreshold()) {
                    logger
                        .warn("SQL Statement [{}] with parameter object [{}] ran out of the normal time range, it consumed [{}] milliseconds.", new Object[]{
                            statementName, parameterObject, interval});
                }
            }
        }
    }

    /**
     * we reorder the collection of entities in concurrency and commit them in
     * sequence, because we have to conform to the infrastructure of spring's
     * transaction management layer.
     */
    private int batchInsertAfterReordering(final String statementName, final Object parameterObject) {
        Set<String> keys = new HashSet<String>();
        keys.add(getDefaultDataSourceName());
        keys.addAll(getShardDataSourceService().getDataSources().keySet());
        final ShardMRBase mrbase = new ShardMRBase(keys);
        ExecutorService executor = createCustomExecutorService(Runtime.getRuntime().availableProcessors(), "batchInsertAfterReordering");
        try {
            final StringBuffer exceptionStaktrace = new StringBuffer();
            Collection<?> paramCollection = ((BatchInsertTask) parameterObject).getEntities();
            final CountDownLatch latch = new CountDownLatch(paramCollection.size());
            Iterator<?> iter = paramCollection.iterator();
            while (iter.hasNext()) {
                final Object entity = iter.next();
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                            SortedMap<String, DataSource> dsMap = lookupDataSourcesByRouter(statementName, entity);
                            if (MapUtils.isEmpty(dsMap)) {
                                logger
                                    .info("can't find routing rule for {} with parameter {}, so use default data source for it.", statementName, entity);
                                mrbase.emit(getDefaultDataSourceName(), entity);
                            } else {
                                if (dsMap.size() > 1) {
                                    throw new IllegalArgumentException(
                                        "unexpected routing result, found more than 1 target data source for current entity:" + entity);
                                }
                                mrbase.emit(dsMap.firstKey(), entity);
                            }
                        } catch (Throwable t) {
                            exceptionStaktrace.append(ExceptionUtils.getFullStackTrace(t));
                        } finally {
                            latch.countDown();
                        }
                    }
                };
                executor.execute(task);
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new ConcurrencyFailureException("unexpected interruption when re-arranging parameter collection into sub-collections ", e);
            }
            if (exceptionStaktrace.length() > 0) {
                throw new ConcurrencyFailureException(
                    "unpected exception when re-arranging parameter collection, check previous log for details.\n" + exceptionStaktrace);
            }
        } finally {
            executor.shutdown();
        }
        List<ConcurrentRequest> requests = new ArrayList<ConcurrentRequest>();
        for (Map.Entry<String, List<Object>> entity : mrbase.getResources().entrySet()) {
            final List<Object> paramList = entity.getValue();
            if (CollectionUtils.isEmpty(paramList)) {
                continue;
            }
            String identity = entity.getKey();
            final DataSource dataSourceToUse = findDataSourceToUse(entity.getKey());
            final SqlSessionCallBack callback = new SqlSessionCallBack() {
                public Object execute(SqlSession session) throws SQLException {
                    return session.insert(statementName, paramList);
                }
            };
            ConcurrentRequest request = new ConcurrentRequest();
            request.setDataSource(dataSourceToUse);
            request.setAction(callback);
            request.setExecutor(getDataSourceSpecificExecutors().get(identity));
            requests.add(request);
        }
        List<Object> resultObject = getConcurrentRequestProcessor().process(requests);
        int counter = 0;
        for (Object i : resultObject) {
            counter += (Integer) i;
        }
        return counter;
    }

    private DataSource findDataSourceToUse(String key) {
        DataSource dataSourceToUse = null;
        if (StringUtils.equals(key, getDefaultDataSourceName())) {
            dataSourceToUse = getConfiguration().getEnvironment().getDataSource();
        } else {
            dataSourceToUse = getShardDataSourceService().getDataSources().get(key);
        }
        return dataSourceToUse;
    }

    @Override
    public List selectList(final String statementName, final Object parameterObject, RowBounds rowBounds) {
        auditSqlIfNecessary(statementName, parameterObject);
        long startTimestamp = System.currentTimeMillis();
        try {
            if (isPartitioningBehaviorEnabled()) {
                SortedMap<String, DataSource> dsMap = lookupDataSourcesByRouter(statementName, parameterObject);
                if (!MapUtils.isEmpty(dsMap)) {
                    SqlSessionCallBack callback = null;
                    if (rowBounds == null) {
                        callback = new SqlSessionCallBack() {
                            public Object execute(SqlSession session) throws SQLException {
                                return session.selectList(statementName, parameterObject);
                            }
                        };
                    } else {
                        callback = new SqlSessionCallBack() {
                            public Object execute(SqlSession session) throws SQLException {
                                return session.selectList(statementName, parameterObject, rowBounds);
                            }
                        };
                    }
                    List<Object> originalResultList = executeInConcurrency(dsMap, callback);
                    if (MapUtils.isNotEmpty(getMergers()) && getMergers().containsKey(statementName)) {
                        IMerger<Object, Object> merger = getMergers().get(statementName);
                        if (merger != null) {
                            return (List) merger.merge(originalResultList);
                        }
                    }
                    List<Object> resultList = new ArrayList<Object>();
                    for (Object item : originalResultList) {
                        resultList.addAll((List) item);
                    }
                    return resultList;
                }
            } // end if for partitioning status checking
            if (rowBounds == null) {
                return super.selectList(statementName, parameterObject);
            } else {
                return super.selectList(statementName, parameterObject, rowBounds);
            }
        } finally {
            if (isProfileLongTimeRunningSql()) {
                long interval = System.currentTimeMillis() - startTimestamp;
                if (interval > getLongTimeRunningSqlIntervalThreshold()) {
                    logger
                        .warn("SQL Statement [{}] with parameter object [{}] ran out of the normal time range, it consumed [{}] milliseconds.", new Object[]{
                            statementName, parameterObject, interval});
                }
            }
        }
    }

    @Override
    public List selectList(final String statementName, final Object parameterObject) throws DataAccessException {
        return this.selectList(statementName, parameterObject, null);
    }

    @Override
    public List selectList(String statementName) throws DataAccessException {
        return this.selectList(statementName, null);
    }

    @Override
    public Object queryForObject(final String statementName, final Object parameterObject, final Object resultObject) throws DataAccessException {
        auditSqlIfNecessary(statementName, parameterObject);
        long startTimestamp = System.currentTimeMillis();
        try {
            if (isPartitioningBehaviorEnabled()) {
                SortedMap<String, DataSource> dsMap = lookupDataSourcesByRouter(statementName, parameterObject);
                if (!MapUtils.isEmpty(dsMap)) {
                    SqlMapClientCallback callback = null;
                    if (resultObject == null) {
                        callback = new SqlMapClientCallback() {
                            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                                return executor.queryForObject(statementName, parameterObject);
                            }
                        };
                    } else {
                        callback = new SqlMapClientCallback() {
                            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                                return executor.queryForObject(statementName, parameterObject, resultObject);
                            }
                        };
                    }
                    List<Object> resultList = executeInConcurrency(callback, dsMap);
                    @SuppressWarnings("unchecked") Collection<Object> filteredResultList = CollectionUtils.select(resultList, new Predicate() {
                        public boolean evaluate(Object item) {
                            return item != null;
                        }
                    });
                    if (filteredResultList.size() > 1) {
                        throw new IncorrectResultSizeDataAccessException(1);
                    }
                    if (CollectionUtils.isEmpty(filteredResultList)) {
                        return null;
                    }
                    return filteredResultList.iterator().next();
                }
            } // end if for partitioning status checking
            if (resultObject == null) {
                return super.queryForObject(statementName, parameterObject);
            } else {
                return super.queryForObject(statementName, parameterObject, resultObject);
            }
        } finally {
            if (isProfileLongTimeRunningSql()) {
                long interval = System.currentTimeMillis() - startTimestamp;
                if (interval > getLongTimeRunningSqlIntervalThreshold()) {
                    logger
                        .warn("SQL Statement [{}] with parameter object [{}] ran out of the normal time range, it consumed [{}] milliseconds.", new Object[]{
                            statementName, parameterObject, interval});
                }
            }
        }
    }

    @Override
    public Object queryForObject(final String statementName, final Object parameterObject) throws DataAccessException {
        return this.queryForObject(statementName, parameterObject, null);
    }

    @Override
    public Object queryForObject(String statementName) throws DataAccessException {
        return this.queryForObject(statementName, null);
    }

    @Override
    public void update(String statementName, Object parameterObject, int requiredRowsAffected) throws DataAccessException {
        int rowAffected = this.update(statementName, parameterObject);
        if (rowAffected != requiredRowsAffected) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException(statementName, requiredRowsAffected, rowAffected);
        }
    }

    @Override
    public int update(final String statementName, final Object parameterObject) throws DataAccessException {
        auditSqlIfNecessary(statementName, parameterObject);

        long startTimestamp = System.currentTimeMillis();
        try {
            if (isPartitioningBehaviorEnabled()) {
                SortedMap<String, DataSource> dsMap = lookupDataSourcesByRouter(statementName, parameterObject);
                if (!MapUtils.isEmpty(dsMap)) {
                    SqlSessionCallBack action = new SqlSessionCallBack() {
                        public Object execute(SqlSession session) throws SQLException {
                            return session.update(statementName, parameterObject);
                        }
                    };

                    List<Object> results = executeInConcurrency(action, dsMap);
                    Integer rowAffacted = 0;

                    for (Object item : results) {
                        rowAffacted += (Integer) item;
                    }
                    return rowAffacted;
                }
            } // end if for partitioning status checking
            return super.update(statementName, parameterObject);
        } finally {
            if (isProfileLongTimeRunningSql()) {
                long interval = System.currentTimeMillis() - startTimestamp;
                if (interval > getLongTimeRunningSqlIntervalThreshold()) {
                    logger
                        .warn("SQL Statement [{}] with parameter object [{}] ran out of the normal time range, it consumed [{}] milliseconds.", new Object[]{
                            statementName, parameterObject, interval});
                }
            }
        }
    }

    @Override
    public int update(String statementName) throws DataAccessException {
        return this.update(statementName, null);
    }

    protected SortedMap<String, DataSource> lookupDataSourcesByRouter(final String statementName, final Object parameterObject) {
        SortedMap<String, DataSource> resultMap = new TreeMap<String, DataSource>();

        if (getRouter() != null && getShardDataSourceService() != null) {
            List<String> dsSet = getRouter().doRoute(new MyBatisRoutingFact(statementName, parameterObject)).getResourceIdentities();
            if (CollectionUtils.isNotEmpty(dsSet)) {
                Collections.sort(dsSet);
                for (String dsName : dsSet) {
                    resultMap.put(dsName, getShardDataSourceService().getDataSources().get(dsName));
                }
            }
        }
        return resultMap;
    }

    protected String getSqlByStatementName(String statementName, Object parameterObject) {
        MappedStatement statement = getSqlSessionFactory().getConfiguration().getMappedStatement(statementName);
        SqlSource sqlSource = statement.getSqlSource();
        if (sqlSource instanceof StaticSqlSource) {
            return sqlSource.getBoundSql(parameterObject).getSql();
        } else {
            logger.info("dynamic sql can only return sql id.");
            return statementName;
        }
    }

    protected SqlSession getSqlSession(DataSource dataSource) {
        SqlSession session = null;
        boolean transactionAware = (dataSource instanceof TransactionAwareDataSourceProxy);
        try {
            Connection springCon = (transactionAware ? dataSource.getConnection() : DataSourceUtils.doGetConnection(dataSource));
            session = getSqlSessionFactory().openSession(springCon);
            return session;
        } catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Could not get JDBC Connection", ex);
        }
    }

    protected Object executeWith(DataSource dataSource, SqlSessionCallBack callBack) {
        SqlSession session = null;
        boolean transactionAware = (dataSource instanceof TransactionAwareDataSourceProxy);
        try {
            Connection springCon = (transactionAware ? dataSource.getConnection() : DataSourceUtils.doGetConnection(dataSource));
            session = getSqlSessionFactory().openSession(springCon);
            return callBack.execute(session);
        } catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Could not get JDBC Connection", ex);
        }
    }

    public List<Object> executeInConcurrency(SortedMap<String, DataSource> dsMap, SqlSessionCallBack callBack) {
        List<ConcurrentRequest> requests = new ArrayList<ConcurrentRequest>();
        for (Map.Entry<String, DataSource> entry : dsMap.entrySet()) {
            ConcurrentRequest request = new ConcurrentRequest();
            request.setAction(callBack);
            request.setDataSource(entry.getValue());
            request.setExecutor(getDataSourceSpecificExecutors().get(entry.getKey()));
            requests.add(request);
        }
        List<Object> results = getConcurrentRequestProcessor().process(requests);
        return results;
    }


    @Override
    public void destroy() throws Exception {
        if (CollectionUtils.isNotEmpty(internalExecutorServiceRegistry)) {
            logger.info("shutdown executors of ShardSqlSessionTemplate...");
            for (ExecutorService executor : internalExecutorServiceRegistry) {
                if (executor != null) {
                    try {
                        executor.shutdown();
                        executor.awaitTermination(5, TimeUnit.MINUTES);
                        executor = null;
                    } catch (InterruptedException e) {
                        logger.warn("interrupted when shuting down the query executor:\n{}", e);
                    }
                }
            }
            getDataSourceSpecificExecutors().clear();
            logger.info("all of the executor services in ShardSqlSessionTemplate are disposed.");
        }
    }

    /**
     * if a SqlAuditor is injected and a sqlAuditorExecutor is NOT provided
     * together, we need to setup a sqlAuditorExecutor so that the SQL auditing
     * actions can be performed asynchronously. <br>
     * otherwise, the data access process may be blocked by auditing SQL.<br>
     * Although an external ExecutorService can be injected for use, normally,
     * it's not so necessary.<br>
     * Most of the time, you should inject an proper {@link ISqlAuditor} which
     * will do SQL auditing in a asynchronous way.<br>
     */
    private void setUpDefaultSqlAuditorExecutorIfNecessary() {
        if (sqlAuditor != null && sqlAuditorExecutor == null) {
            sqlAuditorExecutor = createCustomExecutorService(1, "setUpDefaultSqlAuditorExecutorIfNecessary");
            // 1. register executor for disposing later explicitly
            internalExecutorServiceRegistry.add(sqlAuditorExecutor);
            // 2. dispose executor implicitly 
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    if (sqlAuditorExecutor == null) {
                        return;
                    }
                    try {
                        sqlAuditorExecutor.shutdown();
                        sqlAuditorExecutor.awaitTermination(5, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        logger.warn("interrupted when shuting down the query executor:\n{}", e);
                    }
                }
            });
        }
    }

    /**
     * If more than one data sources are involved in a data access request, we
     * need a collection of executors to execute the request on these data
     * sources in parallel.<br>
     * But in case the users forget to inject a collection of executors for this
     * purpose, we need to setup a default one.<br>
     */
    private void setupDefaultExecutorServicesIfNecessary() {
        if (isPartitioningBehaviorEnabled()) {

            if (MapUtils.isEmpty(getDataSourceSpecificExecutors())) {

                Set<ShardDataSourceDescriptor> dataSourceDescriptors = getShardDataSourceService().getDataSourceDescriptors();
                for (ShardDataSourceDescriptor descriptor : dataSourceDescriptors) {
                    ExecutorService executor = createExecutorForSpecificDataSource(descriptor);
                    getDataSourceSpecificExecutors().put(descriptor.getIdentity(), executor);
                }
            }

            addDefaultSingleThreadExecutorIfNecessary();
        }
    }

    private ExecutorService createExecutorForSpecificDataSource(ShardDataSourceDescriptor descriptor) {
        final String identity = descriptor.getIdentity();
        final ExecutorService executor = createCustomExecutorService(descriptor.getPoolSize(),
            "createExecutorForSpecificDataSource-" + identity + " data source");
        // 1. register executor for disposing explicitly
        internalExecutorServiceRegistry.add(executor);
        // 2. dispose executor implicitly
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (executor == null) {
                    return;
                }
                try {
                    executor.shutdown();
                    executor.awaitTermination(5, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    logger.warn("interrupted when shuting down the query executor:\n{}", e);
                }
            }
        });
        return executor;
    }

    private void addDefaultSingleThreadExecutorIfNecessary() {
        String identity = getDefaultDataSourceName();
        ShardDataSourceDescriptor descriptor = new ShardDataSourceDescriptor();
        descriptor.setIdentity(identity);
        descriptor.setPoolSize(Runtime.getRuntime().availableProcessors() * 5);
        getDataSourceSpecificExecutors().put(identity, createExecutorForSpecificDataSource(descriptor));
    }

    protected void auditSqlIfNecessary(final String statementName, final Object parameterObject) {
        if (getSqlAuditor() != null) {
            getSqlAuditorExecutor().execute(new Runnable() {
                public void run() {
                    getSqlAuditor().audit(statementName, getSqlByStatementName(statementName, parameterObject), parameterObject);
                }
            });
        }
    }

    /**
     * if a router and a data source locator is provided, it means data access
     * on different databases is enabled.<br>
     */
    protected boolean isPartitioningBehaviorEnabled() {
        return ((router != null) && (getShardDataSourceService() != null));
    }

    public void setSqlAuditor(ISqlAuditor sqlAuditor) {
        this.sqlAuditor = sqlAuditor;
    }

    public ISqlAuditor getSqlAuditor() {
        return sqlAuditor;
    }

    public void setSqlAuditorExecutor(ExecutorService sqlAuditorExecutor) {
        this.sqlAuditorExecutor = sqlAuditorExecutor;
    }

    public ExecutorService getSqlAuditorExecutor() {
        return sqlAuditorExecutor;
    }

    public void setDataSourceSpecificExecutors(Map<String, ExecutorService> dataSourceSpecificExecutors) {
        if (MapUtils.isEmpty(dataSourceSpecificExecutors)) {
            return;
        }
        this.dataSourceSpecificExecutors = dataSourceSpecificExecutors;
    }

    public Map<String, ExecutorService> getDataSourceSpecificExecutors() {
        return dataSourceSpecificExecutors;
    }

    public void setDefaultQueryTimeout(int defaultQueryTimeout) {
        this.defaultQueryTimeout = defaultQueryTimeout;
    }

    public int getDefaultQueryTimeout() {
        return defaultQueryTimeout;
    }

    public void setShardDataSourceService(IShardDataSourceService shardDataSourceService) {
        this.shardDataSourceService = shardDataSourceService;
    }

    public IShardDataSourceService getShardDataSourceService() {
        return shardDataSourceService;
    }

    public void setProfileLongTimeRunningSql(boolean profileLongTimeRunningSql) {
        this.profileLongTimeRunningSql = profileLongTimeRunningSql;
    }

    public boolean isProfileLongTimeRunningSql() {
        return profileLongTimeRunningSql;
    }

    public void setLongTimeRunningSqlIntervalThreshold(long longTimeRunningSqlIntervalThreshold) {
        this.longTimeRunningSqlIntervalThreshold = longTimeRunningSqlIntervalThreshold;
    }

    public long getLongTimeRunningSqlIntervalThreshold() {
        return longTimeRunningSqlIntervalThreshold;
    }

    public void setDefaultDataSourceName(String defaultDataSourceName) {
        this.defaultDataSourceName = defaultDataSourceName;
    }

    public String getDefaultDataSourceName() {
        return defaultDataSourceName;
    }

    public void setRouter(IShardRouter<MyBatisRoutingFact> router) {
        this.router = router;
    }

    public IShardRouter<MyBatisRoutingFact> getRouter() {
        return router;
    }

    public void setConcurrentRequestProcessor(IConcurrentRequestProcessor concurrentRequestProcessor) {
        this.concurrentRequestProcessor = concurrentRequestProcessor;
    }

    public IConcurrentRequestProcessor getConcurrentRequestProcessor() {
        return concurrentRequestProcessor;
    }

    public void setMergers(Map<String, IMerger<Object, Object>> mergers) {
        this.mergers = mergers;
    }

    public Map<String, IMerger<Object, Object>> getMergers() {
        return mergers;
    }

    private ExecutorService createCustomExecutorService(int poolSize, final String method) {
        int coreSize = Runtime.getRuntime().availableProcessors();
        if (poolSize < coreSize) {
            coreSize = poolSize;
        }
        ThreadFactory tf = new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "thread created at ShardSqlSessionTemplate method [" + method + "]");
                t.setDaemon(true);
                return t;
            }
        };
        BlockingQueue<Runnable> queueToUse = new LinkedBlockingQueue<Runnable>(coreSize);
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(coreSize, poolSize, 60, TimeUnit.SECONDS, queueToUse, tf, new ThreadPoolExecutor.CallerRunsPolicy());

        return executor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (isProfileLongTimeRunningSql()) {
            if (longTimeRunningSqlIntervalThreshold <= 0) {
                throw new IllegalArgumentException("'longTimeRunningSqlIntervalThreshold' should have a positive value if 'profileLongTimeRunningSql' is set to true");
            }
        }
        setupDefaultExecutorServicesIfNecessary();
        setUpDefaultSqlAuditorExecutorIfNecessary();
        if (getConcurrentRequestProcessor() == null) {
            setConcurrentRequestProcessor(new DefaultConcurrentRequestProcessor(getSqlSessionFactory()));
        }
    }
}
