package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.UtilRandomGenerate;

class RandomGenerateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<UtilRandomGenerate> randomGenerates;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new RandomGenerateTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setRandomGenerate(RandomGenerateTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionRandomNumber() {
        // given
        int range = 8;
        var recordItem = recordItemBuilder.randomGenerate(range).build();
        int randomNumber = recordItem.getRecord().getPseudorandomNumber();
        var expectedRandomGenerate =
                getExpectedRandomGenerate(recordItem.getConsensusTimestamp(), range, null, randomNumber);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onRandomGenerate(randomGenerates.capture());
        assertThat(randomGenerates.getAllValues()).containsOnly(expectedRandomGenerate);
    }

    @Test
    void updateTransactionRandomBytes() {
        // given
        int range = 0;
        var recordItem = recordItemBuilder.randomGenerate(range).build();
        byte[] randomBytes = recordItem.getRecord().getPseudorandomBytes().toByteArray();
        var expectedRandomGenerate =
                getExpectedRandomGenerate(recordItem.getConsensusTimestamp(), range, randomBytes, null);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onRandomGenerate(randomGenerates.capture());
        assertThat(randomGenerates.getAllValues()).containsOnly(expectedRandomGenerate);
    }

    @Test
    void updateTransactionFailedTransaction() {
        // given
        var recordItem = recordItemBuilder.randomGenerate(1)
                .status(ResponseCodeEnum.DUPLICATE_TRANSACTION)
                .build();

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionEntropyNotSet() {
        // given
        var recordItem = recordItemBuilder.randomGenerate(1)
                .record(r -> r.clearEntropy())
                .build();

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    private UtilRandomGenerate getExpectedRandomGenerate(long consensusTimestamp, int range, byte[] randomBytes,
                                                         Integer randomNumber) {
        return UtilRandomGenerate.builder()
                .consensusTimestamp(consensusTimestamp)
                .range(range)
                .pseudorandomBytes(randomBytes)
                .pseudorandomNumber(randomNumber)
                .build();
    }
}
