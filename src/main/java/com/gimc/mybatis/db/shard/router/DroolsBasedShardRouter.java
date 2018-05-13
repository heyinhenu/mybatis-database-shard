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

import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import com.gimc.mybatis.db.shard.router.support.RoutingResult;

/**
 * TODO when rule numbers increase incredibly, we can introduce a rule engine like drools to enhance the performance of rule matching.<br>
 *
 * @author fujohnwang
 *
 */
public class DroolsBasedShardRouter implements IShardRouter<MyBatisRoutingFact> {

    public RoutingResult doRoute(MyBatisRoutingFact routingFact) throws RoutingException {
        // TODO Auto-generated method stub
        return null;
    }

}
