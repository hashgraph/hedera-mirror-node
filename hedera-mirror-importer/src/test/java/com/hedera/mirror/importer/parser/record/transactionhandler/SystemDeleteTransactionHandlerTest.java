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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

class SystemDeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(Optional.of(EntityId.of(DEFAULT_ENTITY_NUM)));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new SystemDeleteTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setSystemDelete(SystemDeleteTransactionBody.newBuilder()
                        .setFileID(FileID.newBuilder()
                                .setFileNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    // SystemDelete for file is tested by common test case in AbstractTransactionHandlerTest.
    // Test SystemDelete for contract here.
    @Test
    void testSystemDeleteForContract() {
        TransactionBody transactionBody = TransactionBody.newBuilder()
                .setSystemDelete(SystemDeleteTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder()
                                .setContractNum(DEFAULT_ENTITY_NUM)
                                .build()))
                .build();

        testGetEntityIdHelper(
                transactionBody, getDefaultTransactionRecord().build(), EntityId.of(0L, 0L, DEFAULT_ENTITY_NUM));
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void deleteEmptyEntityIds(EntityId entityId) {
        TransactionBody transactionBody = TransactionBody.newBuilder()
                .setSystemDelete(SystemDeleteTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder()
                                .setContractNum(DEFAULT_ENTITY_NUM)
                                .build()))
                .build();

        when(entityIdService.lookup(contractId)).thenReturn(Optional.ofNullable(entityId));

        testGetEntityIdHelper(transactionBody, getDefaultTransactionRecord().build(), EntityId.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(
            value = EntityType.class,
            names = {"CONTRACT", "FILE"})
    void updateTransactionSuccessful(EntityType type) {
        long id = domainBuilder.id();
        var entityId = EntityId.of(id);
        var recordItem = recordItemBuilder
                .systemDelete()
                .transactionBody(b -> {
                    b.clearId();
                    switch (type) {
                        case CONTRACT -> b.setContractID(ContractID.newBuilder().setContractNum(id));
                        case FILE -> b.setFileID(FileID.newBuilder().setFileNum(id));
                    }
                })
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(entityId))
                .get();
        var expectedEntity = entityId.toEntity().toBuilder()
                .deleted(true)
                .timestampRange(Range.atLeast(timestamp))
                .type(type)
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulDeleteNothing() {
        var recordItem = recordItemBuilder
                .systemDelete()
                .transactionBody(Builder::clearId)
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
