/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_AIRDROP;

import com.hedera.mirror.common.domain.token.AbstractTokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropRepository extends CrudRepository<TokenAirdrop, AbstractTokenAirdrop.Id> {

    @Cacheable(cacheNames = CACHE_NAME_TOKEN_AIRDROP, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    @Query(
            value =
                    """
                    select *
                    from token_airdrop
                    where sender_account_id = :senderId
                        and receiver_account_id = :receiverId
                        and token_id = :tokenId
                        and state = 'PENDING'
                    """,
            nativeQuery = true)
    Optional<TokenAirdrop> findById(long senderId, long receiverId, long tokenId);

    /**
     * Retrieves the most recent state of a token airdrop by its ID up to a given block timestamp.
     * The method considers both the current state of the token airdrop and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param senderId the ID of the sender account
     * @param receiverId the ID of the receiver account
     * @param tokenId the ID of the token
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the token airdrop's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    select *
                    from (
                            (
                        select *
                        from token_airdrop
                        where sender_account_id = :senderId
                            and receiver_account_id = :receiverId
                            and token_id = :tokenId
                            and state = 'PENDING'
                            and lower(timestamp_range) <= :blockTimestamp
                            )
                            union all
                            (
                        select *
                        from token_airdrop_history
                        where sender_account_id = :senderId
                            and receiver_account_id = :receiverId
                            and token_id = :tokenId
                            and state = 'PENDING'
                            and lower(timestamp_range) <= :blockTimestamp
                        order by lower(timestamp_range) desc
                        limit 1
                            )
                            order by timestamp_range desc
                            limit 1
                    )
                    """,
            nativeQuery = true)
    Optional<TokenAirdrop> findByIdAndTimestamp(long senderId, long receiverId, long tokenId, long blockTimestamp);
}
