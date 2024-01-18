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

import static com.hedera.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static com.hedera.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

class FreezeTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    protected ArgumentCaptor<NetworkFreeze> networkFreezeCaptor;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new FreezeTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var fileId = FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM);
        return recordItemBuilder
                .freeze()
                .transactionBody(b -> b.setUpdateFile(fileId))
                .build()
                .getTransactionBody()
                .toBuilder();
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.freeze().build();
        var transaction = domainBuilder.transaction().get();
        var freeze = recordItem.getTransactionBody().getFreeze();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onNetworkFreeze(networkFreezeCaptor.capture());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
        assertThat(networkFreezeCaptor.getAllValues())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), NetworkFreeze::getConsensusTimestamp)
                .returns(null, NetworkFreeze::getEndTime)
                .returns(DomainUtils.toBytes(freeze.getFileHash()), NetworkFreeze::getFileHash)
                .returns(EntityId.of(freeze.getUpdateFile()), NetworkFreeze::getFileId)
                .returns(recordItem.getPayerAccountId(), NetworkFreeze::getPayerAccountId)
                .returns(DomainUtils.timestampInNanosMax(freeze.getStartTime()), NetworkFreeze::getStartTime)
                .returns(freeze.getFreezeTypeValue(), NetworkFreeze::getType);
    }

    @SuppressWarnings("deprecation")
    @Test
    void updateTransactionDeprecated() {
        // Given
        var consensusTime = 1690848000L;
        var recordItem = recordItemBuilder
                .freeze()
                .transactionBody(b -> b.clearFileHash()
                        .clearFreezeType()
                        .clearStartTime()
                        .clearUpdateFile()
                        .setStartHour(1)
                        .setStartMin(2)
                        .setEndHour(3)
                        .setEndMin(4))
                .record(r -> r.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(consensusTime)))
                .build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onNetworkFreeze(networkFreezeCaptor.capture());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
        assertThat(networkFreezeCaptor.getAllValues())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), NetworkFreeze::getConsensusTimestamp)
                .returns((consensusTime + 184 * 60) * NANOS_PER_SECOND, NetworkFreeze::getEndTime)
                .returns(EMPTY_BYTE_ARRAY, NetworkFreeze::getFileHash)
                .returns(null, NetworkFreeze::getFileId)
                .returns(recordItem.getPayerAccountId(), NetworkFreeze::getPayerAccountId)
                .returns((consensusTime + 62 * 60) * NANOS_PER_SECOND, NetworkFreeze::getStartTime)
                .returns(FreezeType.UNKNOWN_FREEZE_TYPE_VALUE, NetworkFreeze::getType);
    }

    @SuppressWarnings("deprecation")
    @Test
    void updateTransactionDeprecatedStartAfterEnd() {
        // Given
        var consensusTime = 1690848000L;
        var recordItem = recordItemBuilder
                .freeze()
                .transactionBody(b -> b.clearFileHash()
                        .clearFreezeType()
                        .clearStartTime()
                        .clearUpdateFile()
                        .setStartHour(10)
                        .setStartMin(1)
                        .setEndHour(3)
                        .setEndMin(4))
                .record(r -> r.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(consensusTime)))
                .build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onNetworkFreeze(networkFreezeCaptor.capture());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
        assertThat(networkFreezeCaptor.getAllValues())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), NetworkFreeze::getConsensusTimestamp)
                .returns((consensusTime + 184 * 60 + 86400) * NANOS_PER_SECOND, NetworkFreeze::getEndTime)
                .returns(EMPTY_BYTE_ARRAY, NetworkFreeze::getFileHash)
                .returns(null, NetworkFreeze::getFileId)
                .returns(recordItem.getPayerAccountId(), NetworkFreeze::getPayerAccountId)
                .returns((consensusTime + 601 * 60) * NANOS_PER_SECOND, NetworkFreeze::getStartTime)
                .returns(FreezeType.UNKNOWN_FREEZE_TYPE_VALUE, NetworkFreeze::getType);
    }
}
