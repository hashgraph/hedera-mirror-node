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

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AccountBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<AccountBalance, AccountBalance.Id> {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value =
                    """
        insert into account_balance (account_id, balance, consensus_timestamp)
        select id, balance, :consensusTimestamp
        from entity
        where balance is not null and
          (deleted is not true or balance_timestamp > (
              select coalesce(max(consensus_timestamp), 0)
              from account_balance
              where account_id = 2 and consensus_timestamp > :consensusTimestamp - 2592000000000000
            ))
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
           balance_timestamp > :maxConsensusTimestamp)
        order by id
        """)
    @Transactional
    int balanceSnapshotDeduplicate(long maxConsensusTimestamp, long consensusTimestamp);

    @Query(
            nativeQuery = true,
            value =
                    """
          select max(consensus_timestamp) as consensus_timestamp
          from account_balance
          where account_id = 2 and consensus_timestamp >= :lowerRangeTimestamp and consensus_timestamp < :upperRangeTimestamp
        """)
    Optional<Long> getMaxConsensusTimestampInRange(long lowerRangeTimestamp, long upperRangeTimestamp);

    @Override
    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findAll();

    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findByIdConsensusTimestamp(long consensusTimestamp);
}
