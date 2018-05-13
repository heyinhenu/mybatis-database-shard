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

import com.gimc.mybatis.db.shard.router.DefaultShardInternalRouter;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import com.gimc.mybatis.db.shard.router.IShardRouter;
import com.gimc.mybatis.db.shard.router.aspects.RoutingResultCacheAspect;
import com.gimc.mybatis.db.shard.router.config.support.InternalRuleLoader4DefaultInternalRouter;
import com.gimc.mybatis.db.shard.router.config.vo.InternalRule;
import com.gimc.mybatis.db.shard.support.LRUMap;

/**
 * Top super class used to configure DefaultShardInternalRouter instances.<br>
 *
 * @author fujohnwang
 * @see DefaultShardInternalRouter
 * @see DefaultShardClientInternalRouterXmlFactoryBean
 * @see StaticShardClientInternalRouterFactoryBean
 */
public abstract class AbstractShardClientInternalRouterFactoryBean implements FactoryBean, InitializingBean {

    private IShardRouter<MyBatisRoutingFact> router;

    private Map<String, Object> functionsMap = new HashMap<String, Object>();

    private InternalRuleLoader4DefaultInternalRouter ruleLoader = new InternalRuleLoader4DefaultInternalRouter();

    private boolean enableCache;
    private int cacheSize = -1;

    public Object getObject() throws Exception {
        return router;
    }

    @SuppressWarnings("unchecked")
    public Class getObjectType() {
        return IShardRouter.class;
    }

    public boolean isSingleton() {
        return true;
    }

    @SuppressWarnings("unchecked")
    public void afterPropertiesSet() throws Exception {

        DefaultShardInternalRouter routerToUse = new DefaultShardInternalRouter();

        List<InternalRule> rules = loadRulesFromExternal();

        getRuleLoader().loadRulesAndEquipRouter(rules, routerToUse, getFunctionsMap());

        if (isEnableCache()) {
            ProxyFactory proxyFactory = new ProxyFactory(routerToUse);
            proxyFactory.setInterfaces(new Class[]{IShardRouter.class});
            RoutingResultCacheAspect advice = new RoutingResultCacheAspect();
            if (cacheSize > 0) {
                advice.setInternalCache(new LRUMap(cacheSize));
            }
            proxyFactory.addAdvice(advice);
            this.router = (IShardRouter<MyBatisRoutingFact>) proxyFactory.getProxy();
        } else {
            this.router = routerToUse;
        }
    }

    protected abstract List<InternalRule> loadRulesFromExternal() throws Exception;

    public IShardRouter<MyBatisRoutingFact> getRouter() {
        return router;
    }

    public void setRouter(IShardRouter<MyBatisRoutingFact> router) {
        this.router = router;
    }

    public Map<String, Object> getFunctionsMap() {
        return functionsMap;
    }

    public void setFunctionsMap(Map<String, Object> functionsMap) {
        this.functionsMap = functionsMap;
    }

    public InternalRuleLoader4DefaultInternalRouter getRuleLoader() {
        return ruleLoader;
    }

    public void setRuleLoader(InternalRuleLoader4DefaultInternalRouter ruleLoader) {
        this.ruleLoader = ruleLoader;
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

}
