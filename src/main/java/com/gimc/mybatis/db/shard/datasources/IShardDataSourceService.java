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
package com.gimc.mybatis.db.shard.datasources;

import com.gimc.mybatis.db.shard.transaction.MultipleDataSourcesTransactionManager;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

/**
 * A ICobarDataSourceLocator is responsible for constructing a mapping of data sources and their identities 
 * so that {@link MultipleDataSourcesTransactionManager} and {@link ShardSqlSessionTemplate} can get a collection of data source dependencies in a consistent way.
 * <br>
 * The implementations of this interface can assemble such a mapping relationship as per data source references in a spring container, 
 * or read data source service configuration information from some location and then assemble data sources for usage later.
 *
 * @author fujohnwang
 * @since 1.0
 */
public interface IShardDataSourceService {

    Map<String, DataSource> getDataSources();

    Set<ShardDataSourceDescriptor> getDataSourceDescriptors();
}
