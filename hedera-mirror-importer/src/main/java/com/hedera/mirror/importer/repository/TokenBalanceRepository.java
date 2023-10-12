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

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TokenBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<TokenBalance, TokenBalance.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value =
                    """
        insert into token_balance (account_id, balance, consensus_timestamp, token_id)
        select account_id, balance, :consensusTimestamp, token_id
        from token_account
        where associated is true
        order by account_id, token_id
        """)
    @Transactional
    int balanceSnapshot(long consensusTimestamp);

    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    """
        with token_balance_snapshot as (
          select account_id, balance, token_id from token_balance
          where (account_id, token_id, consensus_timestamp)
            in (select account_id, token_id, max(consensus_timestamp)
                from token_balance
                where consensus_timestamp >= :lowerRangeTimestamp and consensus_timestamp < :upperRangeTimestamp
                group by account_id, token_id)
        )
        insert into token_balance (account_id, balance, consensus_timestamp, token_id)
        select ta.account_id, ta.balance, :consensusTimestamp, ta.token_id
        from token_account ta left join token_balance_snapshot tbs on ta.account_id = tbs.account_id and
          ta.token_id = tbs.token_id
        where ta.associated is true and
          (tbs is null or tbs.balance is null or ta.balance <> tbs.balance)
        order by ta.account_id, ta.token_id\s
        """)
    @Transactional
    int updateBalanceSnapshot(long lowerRangeTimestamp, long upperRangeTimestamp, long consensusTimestamp);

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from token_balance where consensus_timestamp <= ?1")
    int prune(long consensusTimestamp);
}
