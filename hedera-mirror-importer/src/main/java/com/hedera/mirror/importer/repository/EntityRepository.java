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
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;

@CacheConfig(cacheNames = "entities", cacheManager = CacheConfiguration.EXPIRE_AFTER_30M)
public interface EntityRepository extends PagingAndSortingRepository<Entities, Long>, EntityRepositoryCustom {

    @Cacheable(key = "{#p0, #p1, #p2}", sync = true)
    @Query("from Entities where entityShard = ?1 and entityRealm = ?2 and entityNum = ?3")
    Optional<Entities> findByPrimaryKey(long entityShard, long entityRealm, long entityNum);

    @CachePut(key = "{#p0.entityShard, #p0.entityRealm, #p0.entityNum}")
    @Override
    <S extends Entities> S save(S entity);

    @Cacheable(key = "{#p0, #p1, #p2}", cacheNames = "entity_ids",
            cacheManager = CacheConfiguration.BIG_LRU_CACHE, unless = "#result == null")
    @Query("select id from Entities where entityShard = ?1 and entityRealm = ?2 and entityNum = ?3")
    Optional<Long> findEntityIdByNativeIds(long entityShard, long entityRealm, long entityNum);

    @CachePut(key = "{#p0.shardNum, #p0.realmNum, #p0.entityNum}", cacheNames = "entity_ids",
            cacheManager = CacheConfiguration.BIG_LRU_CACHE)
    default <S extends EntityId> Long cache(S entity) {
        return entity.getId();
    }
}
