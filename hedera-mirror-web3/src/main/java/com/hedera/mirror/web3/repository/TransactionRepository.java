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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.Transaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    @Query("select t from Transaction t " +
            "where t.payerAccountId = :payerAccountId " +
            "and t.validStartNs = :validStartNs " +
            "and t.consensusTimestamp >= :validStartNs and t.consensusTimestamp <= :maxConsensusNs")
    Optional<Transaction> findByPayerAccountIdAndValidStartNsAndConsensusTimestampBefore(
            @Param("payerAccountId") EntityId payerAccountId,
            @Param("validStartNs") long validStartNs,
            @Param("maxConsensusNs") long maxConsensusNs);
}