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
package com.gimc.mybatis.db.shard.router.config.support;

import com.gimc.mybatis.db.shard.router.DefaultShardInternalRouter;
import com.gimc.mybatis.db.shard.router.rules.IRoutingRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisNamespaceRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisNamespaceShardingRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisSqlActionRule;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisSqlActionShardingRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;
import com.gimc.mybatis.db.shard.support.utils.MapUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import com.gimc.mybatis.db.shard.router.config.vo.InternalRule;

public class InternalRuleLoader4DefaultInternalRouter {

    public void loadRulesAndEquipRouter(List<InternalRule> rules, DefaultShardInternalRouter router, Map<String, Object> functionsMap) {
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
                List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>> ruleSequence = setUpRuleSequenceContainerIfNecessary(router, namespace);

                if (StringUtils.isEmpty(shardingExpression)) {

                    ruleSequence.get(3).add(new MyBatisNamespaceRule(namespace, destinations));
                } else {
                    MyBatisNamespaceShardingRule insr = new MyBatisNamespaceShardingRule(namespace, destinations, shardingExpression);
                    if (MapUtils.isNotEmpty(functionsMap)) {
                        insr.setFunctionMap(functionsMap);
                    }
                    ruleSequence.get(2).add(insr);
                }
            }
            if (StringUtils.isNotEmpty(sqlAction)) {
                List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>> ruleSequence = setUpRuleSequenceContainerIfNecessary(router, StringUtils
                    .substringBeforeLast(sqlAction, "."));

                if (StringUtils.isEmpty(shardingExpression)) {
                    ruleSequence.get(1).add(new MyBatisSqlActionRule(sqlAction, destinations));
                } else {
                    MyBatisSqlActionShardingRule issr = new MyBatisSqlActionShardingRule(sqlAction, destinations, shardingExpression);
                    if (MapUtils.isNotEmpty(functionsMap)) {
                        issr.setFunctionMap(functionsMap);
                    }
                    ruleSequence.get(0).add(issr);
                }
            }
        }
    }

    private List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>> setUpRuleSequenceContainerIfNecessary(
        DefaultShardInternalRouter routerToUse, String namespace) {
        List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>> ruleSequence = routerToUse.getRulesGroupByNamespaces().get(namespace);
        if (CollectionUtils.isEmpty(ruleSequence)) {
            ruleSequence = new ArrayList<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>>();
            Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionShardingRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
            Set<IRoutingRule<MyBatisRoutingFact, List<String>>> sqlActionRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
            Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceShardingRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
            Set<IRoutingRule<MyBatisRoutingFact, List<String>>> namespaceRules = new HashSet<IRoutingRule<MyBatisRoutingFact, List<String>>>();
            ruleSequence.add(sqlActionShardingRules);
            ruleSequence.add(sqlActionRules);
            ruleSequence.add(namespaceShardingRules);
            ruleSequence.add(namespaceRules);
            routerToUse.getRulesGroupByNamespaces().put(namespace, ruleSequence);
        }
        return ruleSequence;
    }
}
