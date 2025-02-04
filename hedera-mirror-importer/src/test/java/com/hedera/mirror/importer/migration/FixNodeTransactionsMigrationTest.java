/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.repository.NodeRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@Tag("migration")
@RequiredArgsConstructor
public class FixNodeTransactionsMigrationTest extends ImporterIntegrationTest {

    private final FixNodeTransactionsMigration migration;
    private final NodeRepository nodeRepository;
    private final DomainBuilder domainBuilder;
    private final RecordItemBuilder recordItemBuilder;

    @Test
    void empty() {
        softly.assertThat(nodeRepository.count()).isZero();
        runMigration();
        softly.assertThat(nodeRepository.count()).isZero();
        softly.assertThat(findHistory(Node.class)).isEmpty();
        softly.assertAll();
    }

    @Test
    void migrateNoHistory() {
        var expectedNodes = persist();

        softly.assertThat(nodeRepository.count()).isZero();

        runMigration();

        softly.assertThat(nodeRepository.count()).isEqualTo(2);
        softly.assertThat(nodeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNodes);

        softly.assertAll();
    }

    @Test
    void migrateHistory() {
        var originalNodes = persist();
        var historyNodes = new ArrayList<Node>();
        var newNodes = new ArrayList<Node>();

        for (Node node : originalNodes) {
            var nodeDelete = recordItemBuilder
                    .nodeDelete()
                    .transactionBody(tb -> tb.setNodeId(node.getNodeId()))
                    .build();

            var nodeUpdate = recordItemBuilder
                    .nodeUpdate()
                    .transactionBody(tb -> tb.setNodeId(node.getNodeId()))
                    .build();

            node.setTimestampRange(Range.closedOpen(node.getTimestampLower(), nodeDelete.getConsensusTimestamp()));
            historyNodes.add(node);
            historyNodes.add(Node.builder()
                    .adminKey(node.getAdminKey())
                    .createdTimestamp(node.getCreatedTimestamp())
                    .deleted(true)
                    .nodeId(node.getNodeId())
                    .timestampRange(
                            Range.closedOpen(nodeDelete.getConsensusTimestamp(), nodeUpdate.getConsensusTimestamp()))
                    .build());

            newNodes.add(Node.builder()
                    .adminKey(nodeUpdate
                            .getTransactionBody()
                            .getNodeUpdate()
                            .getAdminKey()
                            .toByteArray())
                    .createdTimestamp(node.getCreatedTimestamp())
                    .deleted(false)
                    .nodeId(node.getNodeId())
                    .timestampRange(Range.atLeast(nodeUpdate.getConsensusTimestamp()))
                    .build());

            domainBuilder
                    .transaction()
                    .customize(
                            t -> t.transactionBytes(nodeDelete.getTransaction().toByteArray())
                                    .consensusTimestamp(nodeDelete.getConsensusTimestamp())
                                    .transactionRecordBytes(
                                            nodeDelete.getTransactionRecord().toByteArray())
                                    .type(TransactionType.NODEDELETE.getProtoId()))
                    .persist();

            domainBuilder
                    .transaction()
                    .customize(
                            t -> t.transactionBytes(nodeUpdate.getTransaction().toByteArray())
                                    .consensusTimestamp(nodeUpdate.getConsensusTimestamp())
                                    .transactionRecordBytes(
                                            nodeUpdate.getTransactionRecord().toByteArray())
                                    .type(TransactionType.NODEUPDATE.getProtoId()))
                    .persist();
        }

        softly.assertThat(nodeRepository.count()).isZero();

        runMigration();

        softly.assertThat(nodeRepository.findAll()).containsExactlyInAnyOrderElementsOf(newNodes);
        softly.assertThat(findHistory(Node.class)).containsExactlyInAnyOrderElementsOf(historyNodes);

        softly.assertAll();
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }

    private List<Node> persist() {
        var nodeCreateRecordItem = recordItemBuilder.nodeCreate().build();

        // Update with nonce 1
        var nodeUpdateRecordItem = recordItemBuilder
                .nodeUpdate()
                .recordItem(ri -> ri.consensusTimestamp(nodeCreateRecordItem.getConsensusTimestamp() + 1))
                .record(r -> r.setTransactionID(
                        r.getTransactionID().toBuilder().setNonce(1).build()))
                .build();

        domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(
                                nodeCreateRecordItem.getTransaction().toByteArray())
                        .consensusTimestamp(nodeCreateRecordItem.getConsensusTimestamp())
                        .transactionRecordBytes(
                                nodeCreateRecordItem.getTransactionRecord().toByteArray())
                        .type(TransactionType.NODECREATE.getProtoId()))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(
                                nodeUpdateRecordItem.getTransaction().toByteArray())
                        .consensusTimestamp(nodeUpdateRecordItem.getConsensusTimestamp())
                        .transactionRecordBytes(
                                nodeUpdateRecordItem.getTransactionRecord().toByteArray())
                        .type(TransactionType.NODEUPDATE.getProtoId()))
                .persist();

        var expectedNodeCreate = Node.builder()
                .adminKey(nodeCreateRecordItem
                        .getTransactionBody()
                        .getNodeCreate()
                        .getAdminKey()
                        .toByteArray())
                .createdTimestamp(nodeCreateRecordItem.getConsensusTimestamp())
                .deleted(false)
                .nodeId(nodeCreateRecordItem.getTransactionRecord().getReceipt().getNodeId())
                .timestampRange(Range.atLeast(nodeCreateRecordItem.getConsensusTimestamp()))
                .build();
        var expectedNodeUpdate = Node.builder()
                .adminKey(nodeUpdateRecordItem
                        .getTransactionBody()
                        .getNodeUpdate()
                        .getAdminKey()
                        .toByteArray())
                .createdTimestamp(nodeUpdateRecordItem.getConsensusTimestamp())
                .deleted(false)
                .nodeId(nodeUpdateRecordItem
                        .getTransactionBody()
                        .getNodeUpdate()
                        .getNodeId())
                .timestampRange(Range.atLeast(nodeUpdateRecordItem.getConsensusTimestamp()))
                .build();

        return List.of(expectedNodeCreate, expectedNodeUpdate);
    }
}
