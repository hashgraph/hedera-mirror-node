/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.Node;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NodeCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeCreateTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeCreate(NodeCreateTransactionBody.newBuilder()
                        .setAccountId(AccountID.newBuilder()
                                .setAccountNum(DEFAULT_ENTITY_NUM)
                                .build())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @AfterEach
    void after() {
        entityProperties.getPersist().setNodes(false);
    }

    @Test
    void nodeCreateTransactionNoPersist() {
        entityProperties.getPersist().setNodes(false);

        // given
        var recordItem = recordItemBuilder.nodeCreate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(null).transactionRecordBytes(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(transaction.getTransactionBytes()).isNull();
        assertThat(transaction.getTransactionRecordBytes()).isNull();
        verify(entityListener, times(0)).onNode(any());
    }

    @Test
    void nodeCreateTransactionPersist() {
        entityProperties.getPersist().setNodes(true);

        // given
        var recordItem = recordItemBuilder.nodeCreate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(null).transactionRecordBytes(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = recordItem.getTransaction().toByteArray();
        var transactionRecordBytes = recordItem.getTransactionRecord().toByteArray();

        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);

        var adminKey =
                recordItem.getTransactionBody().getNodeCreate().getAdminKey().toByteArray();
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(recordItem.getConsensusTimestamp(), Node::getCreatedTimestamp)
                .returns(recordItem.getTransactionRecord().getReceipt().getNodeId(), Node::getNodeId)
                .returns(adminKey, Node::getAdminKey)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)));
    }
}
