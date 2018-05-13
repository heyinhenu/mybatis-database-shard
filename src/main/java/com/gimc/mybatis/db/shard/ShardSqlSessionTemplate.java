/**
 * Copyright 1999-2011 Alibaba Group <p> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at <p> http://www.apache.org/licenses/LICENSE-2.0 <p> Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.gimc.mybatis.db.shard;

import com.gimc.mybatis.db.shard.datasources.ShardDataSourceDescriptor;
import com.gimc.mybatis.db.shard.transaction.MultipleDataSourcesTransactionManager;
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

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

import com.gimc.mybatis.db.shard.audit.ISqlAuditor;
import com.gimc.mybatis.db.shard.datasources.MyShardDataSourceService;
import com.gimc.mybatis.db.shard.exception.UncategorizedCobarClientException;
import com.gimc.mybatis.db.shard.merger.IMerger;
import com.gimc.mybatis.db.shard.router.IShardRouter;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import com.gimc.mybatis.db.shard.support.execution.ConcurrentRequest;
import com.gimc.mybatis.db.shard.support.execution.DefaultConcurrentRequestProcessor;
import com.gimc.mybatis.db.shard.support.execution.IConcurrentRequestProcessor;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;
import com.gimc.mybatis.db.shard.support.utils.MapUtils;
import com.gimc.mybatis.db.shard.support.utils.Predicate;
import com.gimc.mybatis.db.shard.support.vo.BatchInsertTask;
import com.gimc.mybatis.db.shard.support.vo.ShardMRBase;


public class ShardSqlSessionTemplate extends SqlSessionTemplate {


    private transient Logger logger = LoggerFactory.getLogger(ShardSqlSessionTemplate.class);

    private static final String DEFAULT_DATASOURCE_IDENTITY = "_ShardSqlSessionTemplate_default_data_source_name";

    private String defaultDataSourceName = DEFAULT_DATASOURCE_IDENTITY;

    private List<ExecutorService> internalExecutorServiceRegistry = new ArrayList<ExecutorService>();
    /**
     * if we want to access multiple database partitions, we need a collection of data source dependencies.<br> {@link MyShardDataSourceService} is a
     * consistent way to get a collection of data source dependencies for @{link ShardSqlSessionTemplate} and {@link
     * MultipleDataSourcesTransactionManager}.<br> If a router is injected, a dataSourceLocator dependency should be injected too. <br>
     */
    private MyShardDataSourceService myShardDataSourceService;

    /**
     * To enable database partitions access, an {@link IShardRouter} is a must dependency.<br> if no router is found, the ShardSqlSessionTemplate will
     * act with behaviors like its parent, the SqlMapClientTemplate.
     */
    private IShardRouter<MyBatisRoutingFact> router;

    /**
     * if you want to do SQL auditing, inject an {@link ISqlAuditor} for use.<br> a sibling ExecutorService would be prefered too, which will be used
     * to execute {@link ISqlAuditor} asynchronously.
     */
    private ISqlAuditor sqlAuditor;
    private ExecutorService sqlAuditorExecutor;

    /**
     * setup ExecutorService for data access requests on each data sources.<br> map key(String) is the identity of DataSource; map
     * value(ExecutorService) is the ExecutorService that will be used to execute query requests on the key's data source.
     */
    private Map<String, ExecutorService> dataSourceSpecificExecutors = new HashMap<String, ExecutorService>();

    private IConcurrentRequestProcessor concurrentRequestProcessor;

    /**
     * timeout threshold to indicate how long the concurrent data access request should time out.<br> time unit in milliseconds.<br>
     */
    private int defaultQueryTimeout = 100;
    /**
     * indicator to indicate whether to log/profile long-time-running SQL
     */
    private boolean profileLongTimeRunningSql = false;
    private long longTimeRunningSqlIntervalThreshold;

    /**
     * In fact, application can do data-merging in their application code after getting the query result, but they can let {@link
     * ShardSqlSessionTemplate} do this for them too, as long as they provide a relationship mapping between the sql action and the merging logic
     * provider.
     */
    private Map<String, IMerger<Object, Object>> mergers = new HashMap<String, IMerger<Object, Object>>();


    /**
     * 自己添加的构造函数
     */
    public ShardSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        super(sqlSessionFactory);
    }

    /**
     * 自己添加的构造函数
     */
    public ShardSqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType) {
        super(sqlSessionFactory, executorType);
    }

    /**
     * 自己添加的构造函数
     */
    public ShardSqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
        PersistenceExceptionTranslator exceptionTranslator) {
        super(sqlSessionFactory, executorType, exceptionTranslator);
    }


    protected SortedMap<String, DataSource> lookupDataSourcesByRouter(final String statementName, final Object parameterObject) {
        SortedMap<String, DataSource> resultMap = new TreeMap<String, DataSource>();

        if (getRouter() != null && getMyShardDataSourceService() != null) {
            List<String> dsSet = getRouter().doRoute(new MyBatisRoutingFact(statementName, parameterObject)).getResourceIdentities();
            if (CollectionUtils.isNotEmpty(dsSet)) {
                Collections.sort(dsSet);
                for (String dsName : dsSet) {
                    resultMap.put(dsName, getMyShardDataSourceService().getDataSources().get(dsName));
                }
            }
        }
        return resultMap;
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
     * If more than one data sources are involved in a data access request, we need a collection of executors to execute the request on these data
     * sources in parallel.<br> But in case the users forget to inject a collection of executors for this purpose, we need to setup a default
     * one.<br>
     */
    private void setupDefaultExecutorServicesIfNecessary() {
        if (isPartitioningBehaviorEnabled()) {
            if (MapUtils.isEmpty(getDataSourceSpecificExecutors())) {
                Set<ShardDataSourceDescriptor> dataSourceDescriptors = getMyShardDataSourceService().getDataSourceDescriptors();
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
        final ExecutorService executor = createCustomExecutorService(descriptor.getPoolSize(), "specificDataSource-" + identity + " data source");
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

    /**
     * if a router and a data source locator is provided, it means data access on different databases is enabled.<br>
     */
    protected boolean isPartitioningBehaviorEnabled() {
        return ((router != null) && (getMyShardDataSourceService() != null));
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

    public void setMyShardDataSourceService(MyShardDataSourceService myShardDataSourceService) {
        this.myShardDataSourceService = myShardDataSourceService;
    }

    public MyShardDataSourceService getMyShardDataSourceService() {
        return myShardDataSourceService;
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
                Thread t = new Thread(r, "ShardSqlSessionTemplate[" + method + "]");
                t.setDaemon(true);
                return t;
            }
        };
        BlockingQueue<Runnable> queueToUse = new LinkedBlockingQueue<Runnable>(coreSize);
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(coreSize, poolSize, 60, TimeUnit.SECONDS, queueToUse, tf, new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
