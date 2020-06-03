package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.util.Optional;
import javax.transaction.Transactional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.Entities;

@CacheConfig(cacheNames = "entities", cacheManager = CacheConfiguration.EXPIRE_AFTER_30M)
public interface EntityRepository extends PagingAndSortingRepository<Entities, Long>, EntityRepositoryCustom {

    @Cacheable(key = "{#p0}", sync = true)
    Optional<Entities> findById(long id);

    @CachePut(key = "{#p0.entityShard, #p0.entityRealm, #p0.entityNum}")
    @Override
    <S extends Entities> S save(S entity);

    // TODO: add test
    @Modifying
    @Query(value = "insert into t_entities (id, entity_shard, entity_realm, entity_num, fk_entity_type_id) " +
            "values (?1, ?2, ?3, ?4, ?5) on conflict do nothing", nativeQuery = true)
    @Transactional
    void insertEntityIdDoNothingOnConflict(long id, long shard, long realm, long num, long type);
}
