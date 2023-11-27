/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_ALLOWANCE;

import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance.Id;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAllowanceRepository extends CrudRepository<TokenAllowance, AbstractTokenAllowance.Id> {

    @Override
    @Cacheable(cacheNames = CACHE_NAME_TOKEN_ALLOWANCE, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<TokenAllowance> findById(Id id);

    /**
     * Retrieves the most recent state of token allowance by its id up to a given block timestamp.
     * The method considers both the current state of the token allowance and its historical states
     * and returns the latest valid just before or equal to the provided block timestamp.
     *
     * @param owner
     * @param spender
     * @param tokenId
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the token allowance state at the specified timestamp.
     * If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    with crypto_allowances as (
                        select *
                        from
                        (
                            select *, row_number() over (
                                partition by spender
                                order by lower(timestamp_range) desc
                            ) as row_number
                            from
                            (
                                (
                                    select *
                                    from crypto_allowance
                                    where token_id = :tokenId
                                        and owner = :owner
                                        and spender = :spender
                                        and lower(timestamp_range) <= :blockTimestamp
                                )
                                union all
                                (
                                    select *
                                    from crypto_allowance_history
                                    where token_id = :tokenId
                                        and owner = :owner
                                        and spender = :spender
                                        and lower(timestamp_range) <= :blockTimestamp
                                )
                            ) as all_crypto_allowances
                        ) as grouped_crypto_allowances
                        where row_number = 1 and amount_granted > 0
                        ), transfers as (
                        select spender, sum(ct.amount) as amount
                        from crypto_transfer ct
                            join crypto_allowances ca
                            on ct.entity_id = ca.owner
                                and ct.payer_account_id = ca.spender
                        where is_approval is true
                            and consensus_timestamp <= :blockTimestamp
                            and consensus_timestamp > lower(ca.timestamp_range)
                        group by spender
                        )
                    select *
                    from (
                        select amount_granted, owner, payer_account_id, spender, timestamp_range, amount_granted + coalesce((select amount from transfers tr where tr.spender = ca.spender), 0) as amount
                        from crypto_allowances ca
                    ) result
                    where amount > 0
                    limit 1
                    """,
            nativeQuery = true)
    Optional<TokenAllowance> findByIdAndTimestamp(long owner, long spender, long tokenId, long blockTimestamp);

    List<TokenAllowance> findByOwner(long owner);
}
