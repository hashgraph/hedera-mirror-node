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

import com.hedera.mirror.common.domain.balance.AccountBalance;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AccountBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<AccountBalance, AccountBalance.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value =
                    """
        insert into account_balance (account_id, balance, consensus_timestamp)
        select id, balance, :consensusTimestamp
        from entity
        where deleted is not true and balance is not null
        order by id
        """)
    @Transactional
    int balanceSnapshot(long consensusTimestamp);

    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    """
        with account_balance_snapshot as (
          select account_id, balance from account_balance
          where (account_id, consensus_timestamp)
            in (select account_id, max(consensus_timestamp)
                from account_balance
                where consensus_timestamp >= :lowerRangeTimestamp and consensus_timestamp < :upperRangeTimestamp
                group by account_id)
        )
        insert into account_balance (account_id, balance, consensus_timestamp)
        select e.id, e.balance, :consensusTimestamp
        from entity e left join account_balance_snapshot abs on e.id = abs.account_id
        where
            e.id = 2 or
            (e.balance is not null and
             e.deleted is not true and
             (abs is null or abs.balance is null or e.balance <> abs.balance))
        order by e.id
        """)
    @Transactional
    int updateBalanceSnapshot(long lowerRangeTimestamp, long upperRangeTimestamp, long consensusTimestamp);

    @Override
    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findAll();

    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findByIdConsensusTimestamp(long consensusTimestamp);

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from account_balance where consensus_timestamp <= ?1")
    int prune(long consensusTimestamp);
}
