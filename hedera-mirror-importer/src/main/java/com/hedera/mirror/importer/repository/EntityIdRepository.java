package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

import java.util.Collection;
import java.util.Optional;

import com.hedera.mirror.importer.domain.EntityId;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.hedera.mirror.importer.config.CacheConfiguration;

import org.springframework.data.repository.PagingAndSortingRepository;

@CacheConfig(cacheNames = "entity_ids", cacheManager = CacheConfiguration.BIG_LRU_CACHE)
public interface EntityIdRepository extends PagingAndSortingRepository<EntityId, Long> {

    @Cacheable(key = "{#p0, #p1, #p2}", sync = true)
    @Query("from EntityId where entityShard = ?1 and entityRealm = ?2 and entityNum = ?3")
    Optional<EntityId> findByNativeIds(long entityShard, long entityRealm, long entityNum);

    @Override
    Page<EntityId> findAll(Pageable pageable);

    @CachePut(key = "{#p0.entityShard, #p0.entityRealm, #p0.entityNum}")
    @Override
    <S extends EntityId> S save(S entity);

    @CachePut(key = "{#p0.entityShard, #p0.entityRealm, #p0.entityNum}")
    default <S extends EntityId> S cache(S entity) {
        return entity;
    }
}
