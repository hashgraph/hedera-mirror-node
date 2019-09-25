package com.hedera.mirror.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.mirror.domain.Entities;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface EntityRepository extends CrudRepository<Entities, Long> {
	@Query(value= "SELECT id FROM t_entities WHERE entity_shard = :entity_shard AND entity_realm = :entity_realm and entity_num = :entity_num", nativeQuery = true)
	Long findEntityByShardRealmNum(@Param("entity_shard") Long entityShard, @Param("entity_realm") Long entityRealm, @Param("entity_num") Long entityNum);
}