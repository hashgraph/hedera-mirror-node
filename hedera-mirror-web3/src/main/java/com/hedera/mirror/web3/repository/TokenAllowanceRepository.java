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
                        where row_number = 1 and amount_granted > 0
                    ), transfers as (
                        select tt.token_id, tt.payer_account_id, tt.consensus_timestamp, sum(tt.amount) as amount
                        from token_transfer tt
                            join token_allowances ta on tt.account_id = ta.owner
                                and tt.payer_account_id = ta.spender
                                and tt.token_id = ta.token_id
                        where is_approval is true
                            and consensus_timestamp <= :blockTimestamp
                            and consensus_timestamp > lower(ta.timestamp_range)
                        group by tt.token_id, tt.payer_account_id, tt.consensus_timestamp
                    ), contract_results_filtered as (
                        select sender_id, consensus_timestamp
                        from contract_result cr
                        where cr.consensus_timestamp <= :blockTimestamp
                            and cr.consensus_timestamp in (
                                select consensus_timestamp
                                from token_transfer
                            )
                    ), contract_call_transfers as (
                        select cr.sender_id, tt.consensus_timestamp, tt.token_id, sum(tt.amount) as amount
                        from token_transfer tt
                            join token_allowances ta on tt.account_id = ta.owner
                                and tt.token_id = ta.token_id,
                                contract_results_filtered cr
                        where tt.is_approval is true
                            and cr.sender_id = ta.spender
                            and tt.consensus_timestamp = cr.consensus_timestamp
                            and tt.consensus_timestamp <= :blockTimestamp
                            and tt.consensus_timestamp > lower(ta.timestamp_range)
                        group by cr.sender_id, tt.token_id, tt.consensus_timestamp
                    )
                    select *
                    from (
                        select amount_granted, owner, payer_account_id, spender, timestamp_range, token_id, amount_granted + coalesce(
                            (
                                select sum(amount)
                                from contract_call_transfers cct
                                where cct.token_id = ta.token_id
                                    and cct.sender_id = ta.spender
                            ),
                            0)
                            +  coalesce(
                            (
                                select sum(amount)
                                from transfers tr
                                where tr.token_id = ta.token_id
                                    and tr.payer_account_id = ta.spender
                                    and tr.consensus_timestamp not in (
                                        select consensus_timestamp
                                        from contract_call_transfers
                                    )
                            ),
                            0
                        ) as amount
                        from token_allowances ta
                    ) result
                    where amount > 0
                    """,
            nativeQuery = true)
    List<TokenAllowance> findByOwnerAndTimestamp(long owner, long blockTimestamp);
}
