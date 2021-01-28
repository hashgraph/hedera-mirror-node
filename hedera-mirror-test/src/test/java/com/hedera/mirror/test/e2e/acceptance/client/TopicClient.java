package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicDeleteTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Log4j2
@Value
public class TopicClient extends AbstractNetworkClient {
    private final Map<Long, Instant> recordPublishInstants;

    public TopicClient(SDKClient sdkClient) {
        super(sdkClient);
        recordPublishInstants = new HashMap<>();
        log.debug("Creating Topic Client");
    }

    public TransactionReceipt createTopic(Ed25519PublicKey adminKey, Ed25519PublicKey submitKey) throws HederaStatusException {

        Instant refInstant = Instant.now();
        ConsensusTopicCreateTransaction consensusTopicCreateTransaction = new ConsensusTopicCreateTransaction()
                .setAdminKey(adminKey)
                .setAutoRenewAccountId(sdkClient.getOperatorId())
                .setMaxTransactionFee(1_000_000_000)
                .setTopicMemo("HCS Topic_" + refInstant);
//                .setAutoRenewPeriod(Duration.ofDays(7000000)) // INSUFFICIENT_TX_FEE, also unsupported
//                .setAutoRenewAccountId()

        if (submitKey != null) {
            consensusTopicCreateTransaction.setSubmitKey(submitKey);
        }

        TransactionReceipt transactionReceipt = executeTransactionAndRetrieveReceipt(consensusTopicCreateTransaction,
                null).getReceipt();

        ConsensusTopicId topicId = transactionReceipt.getConsensusTopicId();
        log.debug("Created new topic {}", topicId);

        return transactionReceipt;
    }

    public TransactionReceipt updateTopic(ConsensusTopicId topicId) throws HederaStatusException {
        String newMemo = "HCS UpdatedTopic__" + Instant.now().getNano();
        ConsensusTopicUpdateTransaction consensusTopicUpdateTransaction = new ConsensusTopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo(newMemo)
                .setExpirationTime(Instant.now().plus(120, ChronoUnit.DAYS))
                .setAutoRenewPeriod(Duration.ofSeconds(8000000))
                .clearAdminKey()
                .clearSubmitKey()
                .clearTopicMemo()
                .clearAutoRenewAccountId();

        TransactionReceipt transactionReceipt = executeTransactionAndRetrieveReceipt(consensusTopicUpdateTransaction,
                null).getReceipt();

        log.debug("Updated topic '{}'.", topicId);
        return transactionReceipt;
    }

    public TransactionReceipt deleteTopic(ConsensusTopicId topicId) throws HederaStatusException {
        ConsensusTopicDeleteTransaction consensusTopicDeleteTransaction = new ConsensusTopicDeleteTransaction()
                .setTopicId(topicId);

        TransactionReceipt transactionReceipt = executeTransactionAndRetrieveReceipt(consensusTopicDeleteTransaction,
                null).getReceipt();

        log.debug("Deleted topic : '{}'.", topicId);

        return transactionReceipt;
    }

    public List<TransactionReceipt> publishMessagesToTopic(ConsensusTopicId topicId, String baseMessage,
                                                           Ed25519PrivateKey submitKey, int numMessages,
                                                           boolean verify) throws HederaStatusException {
        log.debug("Publishing {} message(s) to topicId : {}.", numMessages, topicId);
        List<TransactionReceipt> transactionReceiptList = new ArrayList<>();
        for (int i = 0; i < numMessages; i++) {
            byte[] publishTimestampByteArray = Longs.toByteArray(System.currentTimeMillis());
            byte[] suffixByteArray = ("_" + baseMessage + "_" + (i + 1)).getBytes(StandardCharsets.UTF_8);
            byte[] message = ArrayUtils.addAll(publishTimestampByteArray, suffixByteArray);

            if (verify) {
                transactionReceiptList.addAll(publishMessageToTopicAndVerify(topicId, message, submitKey));
            } else {
                publishMessageToTopic(topicId, message, submitKey);
            }
        }

        return transactionReceiptList;
    }

    public List<TransactionId> publishMessageToTopic(ConsensusTopicId topicId, byte[] message,
                                                     Ed25519PrivateKey submitKey) throws HederaStatusException {
        ConsensusMessageSubmitTransaction consensusMessageSubmitTransaction = new ConsensusMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message);

        List<TransactionId> transactionIdList = executeTransactionList(consensusMessageSubmitTransaction, submitKey);

        TransactionRecord transactionRecord = transactionIdList.get(0).getRecord(client);
        // get only the 1st sequence number
        if (recordPublishInstants.size() == 0) {
            recordPublishInstants.put(0L, transactionRecord.consensusTimestamp);
        }

        if (log.isTraceEnabled()) {
            log.trace("Published message : '{}' to topicId : {} with consensusTimestamp: {}",
                    new String(message, StandardCharsets.UTF_8), topicId, transactionRecord.consensusTimestamp);
        }

        return transactionIdList;
    }

    public List<TransactionReceipt> publishMessageToTopicAndVerify(ConsensusTopicId topicId, byte[] message,
                                                                   Ed25519PrivateKey submitKey) throws HederaStatusException {
        List<TransactionId> transactionIdList = publishMessageToTopic(topicId, message, submitKey);
        List<TransactionReceipt> transactionReceipts = new ArrayList<>();
        transactionIdList.forEach((transactionId -> {
            TransactionReceipt transactionReceipt = null;
            try {
                transactionReceipt = transactionId.getReceipt(client);

                // note time stamp
                recordPublishInstants.put(transactionReceipt.getConsensusTopicSequenceNumber(), transactionId
                        .getRecord(client).consensusTimestamp);
            } catch (HederaStatusException e) {
                e.printStackTrace();
            }

            log.trace("Verified message published : '{}' to topicId : {} with sequence number : {}", message, topicId,
                    transactionReceipt.getConsensusTopicSequenceNumber());

            transactionReceipts.add(transactionReceipt);
        }));

        return transactionReceipts;
    }

    public Instant getInstantOfPublishedMessage(long sequenceNumber) {
        return recordPublishInstants.get(sequenceNumber);
    }

    public Instant getInstantOfFirstPublishedMessage() {
        return recordPublishInstants.get(0L);
    }
}
