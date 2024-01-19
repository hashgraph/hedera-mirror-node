/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.hedera.mirror.common.domain.contract.ContractState;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractStateRepository extends CrudRepository<ContractState, Long> {

    @Query(value = "select value from contract_state where contract_id = ?1 and slot =?2", nativeQuery = true)
    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_CONTRACT_STATE, unless = "#result == null")
    Optional<byte[]> findStorage(final Long contractId, final byte[] key);

    /**
     * This method retrieves the most recent contract state storage value up to given block timestamp.
     *
     * <p>The method queries contract_state_change table for the most recent contract state storage value
     * before or equal to the specified block timestamp.
     *
     * <p>The result of the query is then ordered by timestamp in descending order
     * to get the most recent value.
     *
     * @param id             The ID of the contract.
     * @param slot           The slot in the contract's storage.
     * @param blockTimestamp The block timestamp up to which to retrieve the storage value.
     * @return An {@code Optional} containing the byte array of the storage value if found, or an empty {@code Optional} if not.
     */
    @Query(
            value =
                    """
            select
                coalesce(value_written, value_read) as value
            from contract_state_change
            where contract_id = ?1
            and slot = ?2
            and consensus_timestamp <= ?3
            order by consensus_timestamp desc
            limit 1
            """,
            nativeQuery = true)
    Optional<byte[]> findStorageByBlockTimestamp(long id, byte[] slot, long blockTimestamp);
}
