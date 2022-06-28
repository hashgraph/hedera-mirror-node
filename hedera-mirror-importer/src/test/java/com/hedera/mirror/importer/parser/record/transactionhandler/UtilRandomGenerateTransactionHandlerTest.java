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

import com.google.protobuf.ByteString;

import com.hedera.mirror.common.domain.entity.EntityType;


import com.hedera.mirror.common.domain.transaction.UtilRandomGenerate;

import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class UtilRandomGenerateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<UtilRandomGenerate> randomGenerates;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new UtilRandomGenerateTransactionHandler(entityListener);
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
        int randomNumber = new SecureRandom().nextInt(range);
        var recordItem = recordItemBuilder
                .utilRandomGenerate(range)
                .record(r -> r.setPseudorandomNumber(randomNumber))
                .build();
        var expectedRandomGenerate =
                getExpectedRandomGenerate(recordItem.getConsensusTimestamp(), range, new byte[0], randomNumber);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onUtilRandomGenerate(randomGenerates.capture());
        assertThat(randomGenerates.getAllValues()).contains(expectedRandomGenerate);
    }

    @Test
    void updateTransactionRandomBytes() {
        // given
        int range = 0;
        int hip351BytesLength = 384;
        byte[] randomBytes = domainBuilder.bytes(hip351BytesLength);
        var recordItem = recordItemBuilder
                .utilRandomGenerate(range)
                .record(r -> r.setPseudorandomBytes(ByteString.copyFrom(randomBytes)))
                .build();
        var expectedRandomGenerate =
                getExpectedRandomGenerate(recordItem.getConsensusTimestamp(), range, randomBytes, 0);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onUtilRandomGenerate(randomGenerates.capture());
        assertThat(randomGenerates.getAllValues()).contains(expectedRandomGenerate);
    }

    private UtilRandomGenerate getExpectedRandomGenerate(long consensusTimestamp, int range, byte[] randomBytes,
                                                         int randomNumber) {
        return UtilRandomGenerate.builder()
                .consensusTimestamp(consensusTimestamp)
                .range(range)
                .pseudorandomBytes(randomBytes)
                .pseudorandomNumber(randomNumber)
                .build();
    }
}
