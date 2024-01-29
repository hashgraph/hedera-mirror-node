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

package com.hedera.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionRepositoryTest extends GrpcIntegrationTest {

    private final DomainBuilder domainBuilder;
    private final TransactionRepository transactionRepository;

    @Test
    void findSuccessfulTransactionsByTypeAfterTimestamp() {
        // given
        var consensusSubmitMessageType = TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId();
        var transaction1 = domainBuilder.transaction().persist();
        var transaction2 = domainBuilder
                .transaction()
                .customize(t -> t.type(consensusSubmitMessageType))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.type(consensusSubmitMessageType).result(10))
                .persist();
        var transaction4 = domainBuilder.transaction().persist();

        // when, then
        assertThat(transactionRepository.findSuccessfulTransactionsByTypeAfterTimestamp(
                        transaction1.getConsensusTimestamp() - 1, 10, transaction1.getType()))
                .containsExactlyInAnyOrder(transaction1, transaction4);
        assertThat(transactionRepository.findSuccessfulTransactionsByTypeAfterTimestamp(
                        transaction1.getConsensusTimestamp() - 1, 1, transaction1.getType()))
                .containsExactly(transaction1);
        assertThat(transactionRepository.findSuccessfulTransactionsByTypeAfterTimestamp(
                        transaction2.getConsensusTimestamp() - 1, 2, consensusSubmitMessageType))
                .containsExactly(transaction2);
    }
}
