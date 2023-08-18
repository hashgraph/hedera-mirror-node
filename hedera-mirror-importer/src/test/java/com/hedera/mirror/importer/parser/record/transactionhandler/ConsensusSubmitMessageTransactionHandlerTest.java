/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.TestUtils.toEntityTransactions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsensusSubmitMessageTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusSubmitMessageTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder()
                                .setTopicNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOPIC;
    }

    @Override
    protected boolean isSkipMainEntityTransaction() {
        return true;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.consensusSubmitMessage().build();
        var transaction = domainBuilder.transaction().get();
        var topicMessage = ArgumentCaptor.forClass(TopicMessage.class);
        var transactionBody = recordItem.getTransactionBody().getConsensusSubmitMessage();
        var receipt = recordItem.getTransactionRecord().getReceipt();
        var expectedEntityTransactions =
                toEntityTransactions(recordItem, transaction.getNodeAccountId(), transaction.getPayerAccountId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onTopicMessage(topicMessage.capture());
        assertThat(topicMessage.getValue())
                .returns(transactionBody.getChunkInfo().getNumber(), TopicMessage::getChunkNum)
                .returns(transactionBody.getChunkInfo().getTotal(), TopicMessage::getChunkTotal)
                .returns(transaction.getConsensusTimestamp(), TopicMessage::getConsensusTimestamp)
                .returns(
                        transactionBody.getChunkInfo().getInitialTransactionID().toByteArray(),
                        TopicMessage::getInitialTransactionId)
                .returns(transactionBody.getMessage().toByteArray(), TopicMessage::getMessage)
                .returns(recordItem.getPayerAccountId(), TopicMessage::getPayerAccountId)
                .returns(receipt.getTopicRunningHash().toByteArray(), TopicMessage::getRunningHash)
                .returns((int) receipt.getTopicRunningHashVersion(), TopicMessage::getRunningHashVersion)
                .returns(receipt.getTopicSequenceNumber(), TopicMessage::getSequenceNumber)
                .returns(transaction.getEntityId(), TopicMessage::getTopicId);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTopics(false);
        var recordItem = recordItemBuilder.consensusSubmitMessage().build();
        var transaction = domainBuilder.transaction().get();
        var expectedEntityTransactions =
                toEntityTransactions(recordItem, transaction.getNodeAccountId(), transaction.getPayerAccountId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }
}
