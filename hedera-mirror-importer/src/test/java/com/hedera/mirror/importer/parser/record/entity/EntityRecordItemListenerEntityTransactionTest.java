/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.GeneratedMessageV3;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class EntityRecordItemListenerEntityTransactionTest extends AbstractEntityRecordItemListenerTest {

    @BeforeEach
    void setup() {
        entityProperties.getPersist().setEntityTransactions(true);
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setEntityTransactions(false);
        entityProperties.getPersist().setItemizedTransfers(true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityTransactions(String name, RecordItem recordItem) {
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(getExpectedEntityTransactions(recordItem));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityTransactionsWhenDisabled(String name, RecordItem recordItem) {
        entityProperties.getPersist().setEntityTransactions(false);
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityTransactionsWhenItemizedTransfersDisabled(String name, RecordItem recordItem) {
        entityProperties.getPersist().setItemizedTransfers(false);
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(getExpectedEntityTransactions(recordItem));
    }

    private Collection<EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem) {
        return getEntities(recordItem).stream()
                .filter(entityProperties.getPersist()::shouldPersistEntityTransaction)
                .map(e -> TestUtils.toEntityTransaction(e, recordItem))
                .toList();
    }

    @SuppressWarnings("deprecation")
    private Set<EntityId> getEntities(RecordItem recordItem) {
        boolean includeTransfersInBody = entityProperties.getPersist().isItemizedTransfers();
        var entities = getEntities(recordItem.getTransactionBody(), includeTransfersInBody);
        entities.addAll(getEntities(recordItem.getTransactionRecord(), true));
        for (var sidecar : recordItem.getSidecarRecords()) {
            entities.addAll(getEntities(sidecar, false));
        }

        if (recordItem.getTransactionType() == TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()) {
            // Remove the topicId in ConsensusSubmitMessage tx
            var topicId = EntityId.of(
                    recordItem.getTransactionBody().getConsensusSubmitMessage().getTopicID());
            entities.remove(topicId);
        }

        var record = recordItem.getTransactionRecord();
        ContractFunctionResult contractFunctionResult = null;
        switch (record.getBodyCase()) {
            case CONTRACTCALLRESULT -> contractFunctionResult = record.getContractCallResult();
            case CONTRACTCREATERESULT -> contractFunctionResult = record.getContractCreateResult();
        }

        if (contractFunctionResult != null
                && recordItem.getHapiVersion().isGreaterThanOrEqualTo(RecordFile.HAPI_VERSION_0_23_0)) {
            var rootContractId = EntityId.of(contractFunctionResult.getContractID());
            for (var contractId : contractFunctionResult.getCreatedContractIDsList()) {
                var entityId = EntityId.of(contractId);
                if (!rootContractId.equals(entityId)) {
                    entities.remove(entityId);
                }
            }
        }

        return entities;
    }

    private Set<EntityId> getEntities(GeneratedMessageV3 message, boolean includeTransfers) {
        if (message instanceof SchedulableTransactionBody) {
            // Don't include any entities in a  SchedulableTransactionBody
            return Collections.emptySet();
        }

        var entities = new HashSet<EntityId>();
        for (var value : message.getAllFields().values()) {
            entities.addAll(getEntitiesInner(value, includeTransfers));
        }

        return entities;
    }

    private Set<EntityId> getEntitiesInner(Object value, boolean includeTransfers) {
        var entities = new HashSet<EntityId>();
        if (value instanceof AccountID accountId) {
            entities.add(EntityId.of(accountId));
        } else if (value instanceof ContractID contractId) {
            entities.add(EntityId.of(contractId));
        } else if (value instanceof FileID fileId) {
            entities.add(EntityId.of(fileId));
        } else if (value instanceof ScheduleID scheduleId) {
            entities.add(EntityId.of(scheduleId));
        } else if (value instanceof TokenID tokenId) {
            entities.add(EntityId.of(tokenId));
        } else if (value instanceof TopicID topicId) {
            entities.add(EntityId.of(topicId));
        } else if (value instanceof GeneratedMessageV3 message) {
            if (!includeTransfers && (value instanceof TransferList || value instanceof TokenTransferList)) {
                return Collections.emptySet();
            }

            entities.addAll(getEntities(message, includeTransfers));
        } else if (value instanceof Collection<?> collection) {
            for (var element : collection) {
                entities.addAll(getEntitiesInner(element, includeTransfers));
            }
        }

        return entities;
    }

    @SneakyThrows
    private static Stream<Arguments> provideRecordItems() {
        var recordItemBuilder = new RecordItemBuilder();
        var methods = RecordItemBuilder.class.getMethods();

        var argumentsList = new ArrayList<Arguments>();
        for (var method : methods) {
            if (method.getReturnType() != RecordItemBuilder.Builder.class || method.getParameterCount() != 0) {
                continue;
            }

            var recordItem = ((RecordItemBuilder.Builder<?>) method.invoke(recordItemBuilder))
                    .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                    .build();
            argumentsList.add(Arguments.of(method.getName(), recordItem));

            if (recordItem.getTransactionBody().hasContractCall()) {
                // Test contractCall before and after HAPI 0.23.0. Since HAPI 0.23.0,
                // ContractFunctionResult.createdContractIDs is marked as deprecated and each new child contract should
                // have its own child contractCreate transaction & record. As a result, mirrornode should not generate
                // EntityTransaction for such child contracts starting from HAPI 0.23.0.
                var version = recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0)
                        ? RecordFile.HAPI_VERSION_0_23_0
                        : new Version(0, 22, 0);
                recordItem = ((RecordItemBuilder.Builder<?>) method.invoke(recordItemBuilder))
                        .recordItem(r -> r.hapiVersion(version))
                        .build();
                argumentsList.add(Arguments.of(String.format("%s_%s", method.getName(), version), recordItem));
            }
        }

        return argumentsList.stream();
    }
}
