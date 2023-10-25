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

import com.hedera.mirror.common.domain.balance.TokenBalance;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenBalanceRepository extends CrudRepository<TokenBalance, TokenBalance.Id> {

    /**
     * Retrieves the token balance of a specific token for a specific account at the last consensus timestamp
     * before оr equal to the provided block timestamp.
     *
     * @param tokenId         the ID of the token whose balance is to be retrieved.
     * @param accountId       the ID of the account whose balance is to be retrieved.
     * @param blockTimestamp  the block timestamp used as the upper limit to retrieve the token balance.
     *                        The method will retrieve the last token balance before or equal to this timestamp.
     * @return an Optional containing the balance if found, or an empty Optional if no matching balance
     *         entry is found before the given block timestamp.
     */
    @Query(
            value =
                    """
                select * from token_balance
                where
                    token_id = ?1 and
                    account_id = ?2 and
                    consensus_timestamp <= ?3
                order by consensus_timestamp desc
                limit 1
                """,
            nativeQuery = true)
    Optional<TokenBalance> findByIdAndTimestampLessThan(long tokenId, long accountId, long blockTimestamp);

    /**
     * Finds the historical token balance for a given token ID and account ID combination based on a specific block timestamp.
     * This method calculates the historical balance by summing the token transfers and adding the sum to the initial balance
     * found at a timestamp less than the given block timestamp. If no token_balance is found for the given token_id,
     * account_id, and consensus timestamp, a balance of 0 will be returned.
     *
     * @param tokenId         the ID of the token.
     * @param accountId       the ID of the account.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the historical balance at the specified timestamp.
     *         If there are no token transfers between the consensus_timestamp of token_balance and the block timestamp,
     *         the method will return the balance present at consensus_timestamp.
     */
    @Query(
            value =
                    """
                    with balance_snapshot as (
                      select consensus_timestamp
                      from account_balance_file
                      where consensus_timestamp <= ?3
                      order by consensus_timestamp desc
                      limit 1
                    ), base as (
                        select balance
                        from token_balance as tb, balance_snapshot as s
                        where
                          tb.consensus_timestamp = s.consensus_timestamp and
                          token_id = ?1 and
                          account_id = ?2
                    ), change as (
                        select sum(amount) as amount
                        from token_transfer as tt, balance_snapshot as s
                        where
                          token_id = ?1 and
                          account_id = ?2 and
                          tt.consensus_timestamp > s.consensus_timestamp and
                          tt.consensus_timestamp <= ?3
                    )
                    select coalesce((select balance from base), 0) + coalesce((select amount from change), 0)
                    """,
            nativeQuery = true)
    Optional<Long> findHistoricalTokenBalanceUpToTimestamp(long tokenId, long accountId, long blockTimestamp);
}
