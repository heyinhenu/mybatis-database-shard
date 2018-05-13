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
package com.gimc.mybatis.db.shard.datasources.ha.support;

import com.gimc.mybatis.db.shard.datasources.DefaultShardDataSourceService;
import javax.sql.DataSource;

/**
 * A Callback Interface that can be used to hook in custom cross-cutting concerns for your DataSource.<br>
 * currently, there is no need to use it yet.
 *
 * @author fujohnwang
 * @see    DefaultShardDataSourceService
 */
public interface IDataSourcePostProcessor {

    DataSource postProcess(DataSource dataSource);
}
