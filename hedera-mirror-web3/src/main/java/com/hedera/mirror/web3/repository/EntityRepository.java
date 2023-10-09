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

    /**
     * Retrieves the most recent state of an entity by its evm address up to a given block timestamp.
     *
     * @param evmAddress      the evm address of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Cacheable(
            cacheNames = "entityEvmAddressUnionCache",
            key = "#evmAddress + '-' + #blockTimestamp",
            cacheManager = CACHE_MANAGER_ENTITY,
            unless = "#result == null")
    @Query(
            value =
                    """
                    select *
                    from entity
                    where evm_address = ?1 and lower(timestamp_range) <= ?2
                    and deleted = false
                    order by timestamp_range desc
                    limit 1
                    """,
            nativeQuery = true)
    Optional<Entity> findByEvmAddressAndTimestampAndDeletedIsFalse(byte[] evmAddress, long blockTimestamp);

    /**
     * Retrieves the most recent state of an entity by its ID up to a given block timestamp.
     * The method considers both the current state of the entity and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     * It performs a UNION operation between the 'entity' and 'entity_history' tables,
     * filters the combined result set to get the records with a timestamp range
     * less than or equal to the provided block timestamp and then returns the most recent record.
     *
     * @param id              the ID of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    (
                        select *
                        from entity
                        where id = ?1 and lower(timestamp_range) <= ?2
                        and deleted = false
                    )
                    union all
                    (
                        select *
                        from entity_history
                        where id = ?1 and lower(timestamp_range) <= ?2
                        and deleted = false
                        order by lower(timestamp_range) desc
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """,
            nativeQuery = true)
    Optional<Entity> findByIdAndTimestampAndDeletedIsFalse(long id, long blockTimestamp);
}
