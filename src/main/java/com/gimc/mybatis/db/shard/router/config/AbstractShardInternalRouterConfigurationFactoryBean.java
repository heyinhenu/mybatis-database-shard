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
package com.gimc.mybatis.db.shard.router.config;

import com.gimc.mybatis.db.shard.router.ShardInternalRouter;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.util.ObjectUtils;

import com.gimc.mybatis.db.shard.router.rules.IRoutingRule;

public abstract class AbstractShardInternalRouterConfigurationFactoryBean implements FactoryBean, InitializingBean {

    private ShardInternalRouter router;

    private boolean enableCache;
    private int cacheSize;

    private Resource configLocation;
    private Resource[] configLocations;

    private Map<String, Object> functionsMap = new HashMap<String, Object>();

    public Object getObject() throws Exception {
        return this.router;
    }

    @SuppressWarnings("unchecked")
    public Class getObjectType() {
        return ShardInternalRouter.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public void afterPropertiesSet() throws Exception {
        if (enableCache) {
            if (cacheSize <= 0) {
                setCacheSize(10000);
            }
        }
        this.router = new ShardInternalRouter(enableCache, cacheSize);

        final Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionShardingRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
        final Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
        final Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceShardingRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
        final Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();

        if (getConfigLocation() != null) {
            assembleRulesForRouter(this.router, getConfigLocation(), sqlActionShardingRules, sqlActionRules, namespaceShardingRules, namespaceRules);
        }

        if (!ObjectUtils.isEmpty(getConfigLocations())) {
            for (Resource res : getConfigLocations()) {
                assembleRulesForRouter(this.router, res, sqlActionShardingRules, sqlActionRules, namespaceShardingRules, namespaceRules);
            }
        }

        List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>> ruleSequences = new ArrayList<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>>() {
            private static final long serialVersionUID = 1493353938640646578L;

            {
                add(sqlActionShardingRules);
                add(sqlActionRules);
                add(namespaceShardingRules);
                add(namespaceRules);
            }
        };

        router.setRuleSequences(ruleSequences);
    }

    /**
     * Subclass just needs to read in rule configurations and assemble the
     * router with the rules read from configurations.
     *
     * @param router
     * @param namespaceRules
     * @param namespaceShardingRules
     * @param sqlActionRules
     * @param sqlActionShardingRules
     */
    protected abstract void assembleRulesForRouter(ShardInternalRouter router, Resource configLocation,
        Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionShardingRules, Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionRules,
        Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceShardingRules, Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceRules)
        throws IOException;

    public void setConfigLocation(Resource configLocation) {
        this.configLocation = configLocation;
    }

    public Resource getConfigLocation() {
        return configLocation;
    }

    public void setConfigLocations(Resource[] configLocations) {
        this.configLocations = configLocations;
    }

    public Resource[] getConfigLocations() {
        return configLocations;
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setFunctionsMap(Map<String, Object> functionMaps) {
        if (functionMaps == null) {
            return;
        }
        this.functionsMap = functionMaps;
    }

    public Map<String, Object> getFunctionsMap() {
        return functionsMap;
    }

}
