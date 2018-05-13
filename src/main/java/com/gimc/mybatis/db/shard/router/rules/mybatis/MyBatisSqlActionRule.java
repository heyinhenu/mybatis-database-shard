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
package com.gimc.mybatis.db.shard.router.rules.mybatis;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;

/**
 * iBatis SQL-Map specific vertical partitioning rule.<br>
 *
 * @author fujohnwang
 * @since 1.0
 */
public class MyBatisSqlActionRule extends AbstractIBatisOrientedRule {

    public MyBatisSqlActionRule(String pattern, String action) {
        super(pattern, action);
    }

    public boolean isDefinedAt(MyBatisRoutingFact routeFact) {
        Validate.notNull(routeFact);
        return StringUtils.equals(getTypePattern(), routeFact.getAction());
    }

    @Override
    public String toString() {
        return "MyBatisSqlActionRule [getAction()=" + getAction() + ", getTypePattern()=" + getTypePattern() + "]";
    }

}
