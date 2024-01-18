/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

class UtilPrngTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<Prng> pseudoRandomGenerates;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new UtilPrngTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder.prng(0).build().getTransactionBody().toBuilder();
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionRandomNumber() {
        // given
        int range = 8;
        var recordItem = recordItemBuilder.prng(range).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();
        int randomNumber = recordItem.getTransactionRecord().getPrngNumber();
        var expected = Prng.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .payerAccountId(recordItem.getPayerAccountId().getId())
                .prngNumber(randomNumber)
                .range(range)
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onPrng(pseudoRandomGenerates.capture());
        assertThat(pseudoRandomGenerates.getAllValues()).containsOnly(expected);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionRandomBytes() {
        // given
        int range = 0;
        var recordItem = recordItemBuilder.prng(range).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();
        byte[] randomBytes = recordItem.getTransactionRecord().getPrngBytes().toByteArray();
        var expected = Prng.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .payerAccountId(recordItem.getPayerAccountId().getId())
                .prngBytes(randomBytes)
                .range(range)
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onPrng(pseudoRandomGenerates.capture());
        assertThat(pseudoRandomGenerates.getAllValues()).containsOnly(expected);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionFailedTransaction() {
        // given
        var recordItem = recordItemBuilder
                .prng(1)
                .status(ResponseCodeEnum.DUPLICATE_TRANSACTION)
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionEntropyNotSet() {
        // given
        var recordItem = recordItemBuilder
                .prng(1)
                .record(TransactionRecord.Builder::clearEntropy)
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
