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

import static com.hedera.mirror.common.util.DomainUtils.toBytes;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class ConsensusSubmitMessageTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getConsensusSubmitMessage().getTopicID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }

    /**
     * Add common entity ids for a ConsensusSubmitMessage transaction. Note the main entity id (the topic id the message
     * is submitted to) is skipped since it's already tracked in topic_message table
     *
     * @param transaction
     * @param recordItem
     */
    @Override
    protected void addCommonEntityIds(Transaction transaction, RecordItem recordItem) {
        recordItem.addEntityId(transaction.getNodeAccountId());
        recordItem.addEntityId(transaction.getPayerAccountId());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTopics() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getConsensusSubmitMessage();
        var transactionRecord = recordItem.getTransactionRecord();
        var receipt = transactionRecord.getReceipt();
        int runningHashVersion =
                receipt.getTopicRunningHashVersion() == 0 ? 1 : (int) receipt.getTopicRunningHashVersion();
        var topicMessage = new TopicMessage();

        // Handle optional fragmented topic message
        if (transactionBody.hasChunkInfo()) {
            ConsensusMessageChunkInfo chunkInfo = transactionBody.getChunkInfo();
            topicMessage.setChunkNum(chunkInfo.getNumber());
            topicMessage.setChunkTotal(chunkInfo.getTotal());

            if (chunkInfo.hasInitialTransactionID()) {
                topicMessage.setInitialTransactionId(
                        chunkInfo.getInitialTransactionID().toByteArray());
            }
        }

        topicMessage.setConsensusTimestamp(transaction.getConsensusTimestamp());
        topicMessage.setMessage(toBytes(transactionBody.getMessage()));
        topicMessage.setPayerAccountId(recordItem.getPayerAccountId());
        topicMessage.setRunningHash(toBytes(receipt.getTopicRunningHash()));
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setSequenceNumber(receipt.getTopicSequenceNumber());
        topicMessage.setTopicId(transaction.getEntityId());
        entityListener.onTopicMessage(topicMessage);
    }
}
