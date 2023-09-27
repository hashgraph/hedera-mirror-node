/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_ENTITY;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Cacheable(
            cacheNames = "entity.id_and_deleted_is_false",
            cacheManager = CACHE_MANAGER_ENTITY,
            unless = "#result == null")
    Optional<Entity> findByIdAndDeletedIsFalse(Long entityId);

    Optional<Entity> findByEvmAddressAndDeletedIsFalse(byte[] alias);

    @Cacheable(
            cacheNames = "entityEvmAddressHistoricalUnionCache",
            key = "#evmAddress + '-' + #createdTimestamp",
            cacheManager = CACHE_MANAGER_ENTITY,
            unless = "#result == null")
    @Query(
            value = "(SELECT * FROM entity "
                    + "WHERE evm_address = ?1 "
                    + "AND created_timestamp = ?2 "
                    + "AND deleted = false "
                    + "UNION ALL "
                    + "SELECT * FROM entity_history "
                    + "WHERE evm_address = ?1 "
                    + "AND created_timestamp = ?2 "
                    + "AND deleted = false) "
                    + "LIMIT 1",
            nativeQuery = true)
    Optional<Entity> findByEvmAddressAndTimestampAndDeletedIsFalse(byte[] evmAddress, Long createdTimestamp);

    @Cacheable(
            cacheNames = "entityIdHistoricalUnionCache",
            key = "#id + '-' + #createdTimestamp",
            cacheManager = CACHE_MANAGER_ENTITY,
            unless = "#result == null")
    @Query(
            value = "(SELECT * FROM entity "
                    + "WHERE id = ?1 "
                    + "AND created_timestamp = ?2 "
                    + "AND deleted = false "
                    + "UNION ALL "
                    + "SELECT * FROM entity_history "
                    + "WHERE id = ?1 "
                    + "AND created_timestamp = ?2 "
                    + "AND deleted = false) "
                    + "LIMIT 1",
            nativeQuery = true)
    Optional<Entity> findByIdAndTimestampAndDeletedIsFalse(Long id, Long createdTimestamp);
}
