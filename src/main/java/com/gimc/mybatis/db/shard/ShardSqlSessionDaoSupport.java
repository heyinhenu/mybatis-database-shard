package com.gimc.mybatis.db.shard;

import java.util.Collection;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.dao.DataAccessException;


public class ShardSqlSessionDaoSupport extends SqlSessionDaoSupport {

    public int batchInsert(final String statementName, final Collection<?> entities) throws DataAccessException {

        ExecutorType executorType = ((SqlSessionTemplate) getSqlSession()).getSqlSessionFactory().getConfiguration().getDefaultExecutorType();
        System.out.println("前=================" + executorType);

        int counter = 0;
        DataAccessException lastEx = null;
        if (isPartitionBehaviorEnabled()) {
            for (Object parameterObject : entities) {
                try {
                    getSqlSession().insert(statementName, parameterObject);
                    counter++;
                } catch (DataAccessException e) {
                    lastEx = e;
                }
            }
            if (lastEx != null) {
                throw lastEx;
            }
        } else {
            ((SqlSessionTemplate) getSqlSession()).getSqlSessionFactory().openSession(ExecutorType.BATCH);
            SqlSession sqlSession = getSqlSession();
            for (Object parameterObject : entities) {
                try {
                    sqlSession.insert(statementName, parameterObject);
                    counter++;
                } catch (DataAccessException e) {
                    lastEx = e;
                }
            }
            sqlSession.flushStatements();
            sqlSession.commit();
            if (lastEx != null) {
                throw lastEx;
            }
            System.out.println("后=================" + executorType);
        }
        return counter;
    }

    public int batchDelete(final String statementName, final Collection<?> entities) throws DataAccessException {
        int counter = 0;
        DataAccessException lastEx = null;
        if (isPartitionBehaviorEnabled()) {
            for (Object entity : entities) {
                try {
                    counter += getSqlSession().delete(statementName, entity);
                } catch (DataAccessException e) {
                    lastEx = e;
                }
            }
            if (lastEx != null) {
                throw lastEx;
            }
        } else {
            ((SqlSessionTemplate) getSqlSession()).getSqlSessionFactory().openSession(ExecutorType.BATCH);
            SqlSession sqlSession = getSqlSession();
            for (Object entity : entities) {
                try {
                    counter += sqlSession.delete(statementName, entity);
                } catch (DataAccessException e) {
                    lastEx = e;
                }
            }
            if (lastEx != null) {
                throw lastEx;
            }
        }
        return counter;
    }

    public int batchUpdate(final String statementName, final Collection<?> entities) throws DataAccessException {
        int counter = 0;
        DataAccessException lastEx = null;
        if (isPartitionBehaviorEnabled()) {
            for (Object parameterObject : entities) {
                try {
                    counter += getSqlSession().update(statementName, parameterObject);
                } catch (DataAccessException e) {
                    lastEx = e;
                }
            }
            if (lastEx != null) {
                throw lastEx;
            }
        } else {
            ((SqlSessionTemplate) getSqlSession()).getSqlSessionFactory().openSession(ExecutorType.BATCH);
            SqlSession sqlSession = getSqlSession();
            for (Object parameterObject : entities) {
                try {
                    counter += sqlSession.update(statementName, parameterObject);
                } catch (DataAccessException e) {
                    lastEx = e;
                }
            }
            if (lastEx != null) {
                throw lastEx;
            }
        }
        return counter;
    }

    protected boolean isPartitionBehaviorEnabled() {
        if (getSqlSession() instanceof ShardSqlSessionTemplate) {
            return ((ShardSqlSessionTemplate) getSqlSession()).isPartitioningBehaviorEnabled();
        }
        return false;
    }
}
