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
import com.gimc.mybatis.db.shard.router.config.vo.InternalRules;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisNamespaceRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisSqlActionRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisSqlActionShardingRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisNamespaceShardingRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import com.gimc.mybatis.db.shard.support.utils.MapUtils;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.core.io.Resource;

import com.gimc.mybatis.db.shard.router.config.vo.InternalRule;
import com.gimc.mybatis.db.shard.router.rules.IRoutingRule;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;
import com.thoughtworks.xstream.XStream;

public class ShardInteralRouterXmlFactoryBean extends AbstractShardInternalRouterConfigurationFactoryBean {

    @Override
    protected void assembleRulesForRouter(ShardInternalRouter router, Resource configLocation,
        Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionShardingRules, Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionRules,
        Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceShardingRules, Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceRules)
        throws IOException {
        XStream xstream = new XStream();
        xstream.alias("rules", InternalRules.class);
        xstream.alias("rule", InternalRule.class);
        xstream.addImplicitCollection(InternalRules.class, "rules");
        xstream.useAttributeFor(InternalRule.class, "merger");

        InternalRules internalRules = (InternalRules) xstream.fromXML(configLocation.getInputStream());
        List<InternalRule> rules = internalRules.getRules();
        if (CollectionUtils.isEmpty(rules)) {
            return;
        }

        for (InternalRule rule : rules) {
            String namespace = StringUtils.trimToEmpty(rule.getNamespace());
            String sqlAction = StringUtils.trimToEmpty(rule.getSqlmap());
            String shardingExpression = StringUtils.trimToEmpty(rule.getShardingExpression());
            String destinations = StringUtils.trimToEmpty(rule.getShards());

            Validate.notEmpty(destinations, "destination shards must be given explicitly.");

            if (StringUtils.isEmpty(namespace) && StringUtils.isEmpty(sqlAction)) {
                throw new IllegalArgumentException("at least one of 'namespace' or 'sqlAction' must be given.");
            }
            if (StringUtils.isNotEmpty(namespace) && StringUtils.isNotEmpty(sqlAction)) {
                throw new IllegalArgumentException("'namespace' and 'sqlAction' are alternatives, can't guess which one to use if both of them are provided.");
            }

            if (StringUtils.isNotEmpty(namespace)) {
                if (StringUtils.isEmpty(shardingExpression)) {
                    namespaceRules.add(new MyBatisNamespaceRule(namespace, destinations));
                } else {
                    MyBatisNamespaceShardingRule insr = new MyBatisNamespaceShardingRule(namespace, destinations, shardingExpression);
                    if (MapUtils.isNotEmpty(getFunctionsMap())) {
                        insr.setFunctionMap(getFunctionsMap());
                    }
                    namespaceShardingRules.add(insr);
                }
            }
            if (StringUtils.isNotEmpty(sqlAction)) {
                if (StringUtils.isEmpty(shardingExpression)) {
                    sqlActionRules.add(new MyBatisSqlActionRule(sqlAction, destinations));
                } else {
                    MyBatisSqlActionShardingRule issr = new MyBatisSqlActionShardingRule(sqlAction, destinations, shardingExpression);
                    if (MapUtils.isNotEmpty(getFunctionsMap())) {
                        issr.setFunctionMap(getFunctionsMap());
                    }
                    sqlActionShardingRules.add(issr);
                }
            }
        }

    }

}
