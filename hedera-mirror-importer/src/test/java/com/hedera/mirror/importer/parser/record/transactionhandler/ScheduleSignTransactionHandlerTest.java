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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.Test;

class ScheduleSignTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ScheduleSignTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setScheduleSign(ScheduleSignTransactionBody.newBuilder()
                        .setScheduleID(ScheduleID.newBuilder()
                                .setScheduleNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected SignatureMap.Builder getDefaultSigMap() {
        return SignatureMap.newBuilder()
                .addSigPair(SignaturePair.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("Ed25519-1"))
                        .setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-1"))
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("Ed25519-2"))
                        .setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-2"))
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("Ed25519-3"))
                        .setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-3"))
                        .build());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder()
                .setStatus(responseCodeEnum)
                .setScheduleID(ScheduleID.newBuilder()
                        .setScheduleNum(DEFAULT_ENTITY_NUM)
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.SCHEDULE;
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.scheduleSign().build();
        var scheduleId =
                EntityId.of(recordItem.getTransactionBody().getScheduleSign().getScheduleID());
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(scheduleId))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
