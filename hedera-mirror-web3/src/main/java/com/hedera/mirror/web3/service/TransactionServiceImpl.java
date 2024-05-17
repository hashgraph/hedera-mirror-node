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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.web3.repository.TransactionRepository;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    public static final Duration MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE = Duration.ofMinutes(35);

    private final TransactionRepository transactionRepository;

    @Override
    public Optional<Transaction> findByTransactionId(TransactionID transactionId) {
        final var accountID = Objects.requireNonNull(transactionId.getAccountID());
        final var transactionValidStart = Objects.requireNonNull(transactionId.getTransactionValidStart());

        final var payerAccountId = EntityId.of(accountID);
        final var validStartNs = convertToNanosMax(transactionValidStart.getSeconds(), transactionValidStart.getNanos());
        final var maxConsensusTimestampNs = validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE.toNanos();

        return transactionRepository.findByPayerAccountIdAndValidStartNsAndConsensusTimestampBefore(
                payerAccountId,
                validStartNs,
                maxConsensusTimestampNs);
    }

    @Override
    public Optional<Transaction> findByConsensusTimestamp(long consensusTimestamp) {
        return transactionRepository.findById(consensusTimestamp);
    }
}
