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
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_ACCOUNT;

import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, AbstractTokenAccount.Id> {

    @Override
    @Cacheable(cacheNames = CACHE_NAME_TOKEN_ACCOUNT, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<TokenAccount> findById(AbstractTokenAccount.Id id);

    @Query(
            value = "select count(*) as tokenCount, balance>0 as isPositiveBalance from token_account "
                    + "where account_id = ?1 and associated is true group by balance>0",
            nativeQuery = true)
    List<TokenAccountAssociationsCount> countByAccountIdAndAssociatedGroupedByBalanceIsPositive(long accountId);

    /**
     * Retrieves the most recent state of number of associated tokens (and if their balance is positive)
     * by accountId up to a given block timestamp.
     * The method considers both the current state of the token account and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param accountId the ID of the account
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return List of {@link TokenAccountAssociationsCount}
     */
    @Query(
            value =
                    """
                    select count(*) as tokenCount, balance>0 as isPositiveBalance
                    from (
                        (
                            select account_id
                            from token_account
                            where account_id = :accountId
                                and associated is true
                                and lower(timestamp_range) <= :blockTimestamp
                            group by balance>0
                        )
                        union all
                        (
                            select account_id
                            from token_account_history
                            where account_id = :accountId
                                and associated is true
                                and lower(timestamp_range) <= :blockTimestamp
                            group by balance>0
                        )
                    )
                    """,
            nativeQuery = true)
    List<TokenAccountAssociationsCount> countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
            long accountId, long blockTimestamp);

    /**
     * Retrieves the most recent state of a token account by its ID up to a given block timestamp.
     * The method considers both the current state of the token account and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param accountId the ID of the account
     * @param tokenId the ID of the token
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the token account's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    (
                        select *
                        from token_account
                        where account_id = :accountId
                            and token_id = :tokenId
                            and lower(timestamp_range) <= :blockTimestamp
                    )
                    union all
                    (
                        select *
                        from token_account_history
                        where account_id = :accountId
                            and token_id = :tokenId
                            and lower(timestamp_range) <= :blockTimestamp
                        order by lower(timestamp_range) desc
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """,
            nativeQuery = true)
    Optional<TokenAccount> findByIdAndTimestamp(long accountId, long tokenId, long blockTimestamp);
}
