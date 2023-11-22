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

    List<TokenAllowance> findByOwner(long owner);

    // TODO
    Optional<TokenAllowance> findByIdAndTimestamp(long spender, long tokenId, long timestamp);
    /**
     * Retrieves the most recent state of the token allowances by their owner id up to a given block timestamp.
     * It takes into account the token transfers that happened up to the given block timestamp, sums them up
     * and decreases the token allowances' amounts with the transfers that occurred.
     *
     * @param owner the owner ID of the token allowance to be retrieved.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return a list containing the token allowances' states for the specified owner at the specified timestamp.
     *         If there is no record found for the given criteria, an empty list is returned.
     */
    @Query(
            value =
                    """
                            with token_allowances as (
                                select *
                                from
                                (
                                    select *, row_number() over (
                                        partition by token_id, spender
                                        order by lower(timestamp_range) desc
                                    ) as row_number
                                    from
                                    (
                                        (
                                            select *
                                            from token_allowance
                                            where owner = :owner
                                                and lower(timestamp_range) <= :blockTimestamp
                                        )
                                        union all
                                        (
                                            select *
                                            from token_allowance_history
                                            where owner = :owner
                                                and lower(timestamp_range) <= :blockTimestamp
                                        )
                                    ) as all_token_allowances
                                ) as grouped_token_allowances
                                where row_number = 1
                            ), transfers as (
                                select tt.token_id, account_id, sum(tt.amount) as amount
                                from token_transfer tt join token_allowances ta on tt.account_id = ta.spender
                                where account_id in (select spender from token_allowances)
                                    and tt.token_id in (select token_id from token_allowances)
                                    and is_approval = true
                             		and consensus_timestamp <= :blockTimestamp
                             		and consensus_timestamp > lower((select timestamp_range from token_allowance))
                                group by tt.token_id, account_id
                            )
                            select amount_granted, owner, payer_account_id, spender, timestamp_range, token_id, coalesce(amount - coalesce((select amount from transfers tr where tr.token_id = ta.token_id and tr.account_id = ta.spender), 0), 0) as amount
                            from token_allowances ta
                            """,
            nativeQuery = true)
    List<TokenAllowance> findByOwnerAndTimestamp(long owner, long blockTimestamp);
}
