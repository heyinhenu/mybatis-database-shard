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
package com.gimc.mybatis.db.shard.router;

import com.gimc.mybatis.db.shard.router.config.DefaultShardClientInternalRouterXmlFactoryBean;
import com.gimc.mybatis.db.shard.router.rules.IRoutingRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import com.gimc.mybatis.db.shard.router.support.RoutingResult;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DefaultShardInternalRouter} receive a map which will maintain a
 * group of rules as per SQ-map namespaces.<br>
 * it will evaluate the rules as per namesapce and a sequence from specific
 * rules to more generic rules.<br>
 * usually, the users don't need to care about these internal details, to use
 * {@link DefaultShardInternalRouter}, just turn to
 * {@link DefaultShardClientInternalRouterXmlFactoryBean} for instantiation.<br>
 *
 * @author fujohnwang
 * @since 1.0
 * @see DefaultShardClientInternalRouterXmlFactoryBean
 */
public class DefaultShardInternalRouter implements IShardRouter<MyBatisRoutingFact> {

    private transient final Logger logger = LoggerFactory.getLogger(DefaultShardInternalRouter.class);

    private Map<String, List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>>> rulesGroupByNamespaces = new HashMap<String, List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>>>();

    public RoutingResult doRoute(MyBatisRoutingFact routingFact) throws RoutingException {
        Validate.notNull(routingFact);
        String action = routingFact.getAction();
        Validate.notEmpty(action);
        String namespace = StringUtils.substringBeforeLast(action, ".");
        List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>> rules = getRulesGroupByNamespaces().get(namespace);

        RoutingResult result = new RoutingResult();
        result.setResourceIdentities(new ArrayList<String>());

        if (!CollectionUtils.isEmpty(rules)) {
            IRoutingRule<MyBatisRoutingFact, List<String>> ruleToUse = null;
            for (Set<IRoutingRule<MyBatisRoutingFact, List<String>>> ruleSet : rules) {
                ruleToUse = searchMatchedRuleAgainst(ruleSet, routingFact);
                if (ruleToUse != null) {
                    break;
                }
            }

            if (ruleToUse != null) {
                logger.info("matched with rule:{} with fact:{}", ruleToUse, routingFact);
                result.getResourceIdentities().addAll(ruleToUse.action());
            } else {
                logger.info("No matched rule found for routing fact:{}", routingFact);
            }
        }

        return result;
    }

    private IRoutingRule<MyBatisRoutingFact, List<String>> searchMatchedRuleAgainst(Set<IRoutingRule<MyBatisRoutingFact, List<String>>> rules,
        MyBatisRoutingFact routingFact) {
        if (CollectionUtils.isEmpty(rules)) {
            return null;
        }
        for (IRoutingRule<MyBatisRoutingFact, List<String>> rule : rules) {
            if (rule.isDefinedAt(routingFact)) {
                return rule;
            }
        }
        return null;
    }

    public void setRulesGroupByNamespaces(Map<String, List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>>> rulesGroupByNamespaces) {
        this.rulesGroupByNamespaces = rulesGroupByNamespaces;
    }

    public Map<String, List<Set<IRoutingRule<MyBatisRoutingFact, List<String>>>>> getRulesGroupByNamespaces() {
        return rulesGroupByNamespaces;
    }

}
