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

    @Override
    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    """
        insert into account_balance (account_id, balance, consensus_timestamp)
        select id, balance, :consensusTimestamp
        from entity
        where
          id = 2 or
          (balance is not null and
           deleted is not true and
           balance_timestamp > :maxConsensusTimestamp and
           balance_timestamp < :upperRangeTimestamp)
        order by id
        """)
    @Transactional
    int updateBalanceSnapshot(long maxConsensusTimestamp, long upperRangeTimestamp, long consensusTimestamp);

    @Query(
            nativeQuery = true,
            value =
                    """
          select coalesce(max(consensus_timestamp), 0) as consensus_timestamp
          from account_balance
          where account_id = 2 and consensus_timestamp >= :lowerRangeTimestamp and consensus_timestamp < :upperRangeTimestamp
        """)
    @Transactional
    long getMaxConsensusTimestampInRange(long lowerRangeTimestamp, long upperRangeTimestamp);

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
