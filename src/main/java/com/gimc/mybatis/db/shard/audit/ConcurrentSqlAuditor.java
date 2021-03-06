/**
 * Copyright 1999-2011 Alibaba Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gimc.mybatis.db.shard.audit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

public class ConcurrentSqlAuditor implements ISqlAuditor {

    private ExecutorService executorService;

    /**
     * simple map-reduce results holder
     * it's not the final abstraction yet, may be refactored later.
     */
    private ConcurrentMap<String, Long> statementStatistics = new ConcurrentHashMap<String, Long>();

    public void audit(String id, String sql, Object sqlContext) {
        // TODO
        // implement application-specific profiling logic here.
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setStatementStatistics(ConcurrentMap<String, Long> statementStatistics) {
        this.statementStatistics = statementStatistics;
    }

    public ConcurrentMap<String, Long> getStatementStatistics() {
        return statementStatistics;
    }

}
