package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicDeleteTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Log4j2
@Value
public class TopicClient {

    private final SDKClient sdkClient;
    private final Client client;
    private final Map<Long, Instant> recordPublishInstants;

    public TopicClient(SDKClient sdkClient) {
        this.sdkClient = sdkClient;
        client = sdkClient.getClient();
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

        TransactionReceipt transactionReceipt = consensusTopicCreateTransaction
                .execute(client)
                .getReceipt(client);

        ConsensusTopicId topicId = transactionReceipt.getConsensusTopicId();
        log.debug("Created new topic {}, with TransactionReceipt : {}", topicId, transactionReceipt);

        return transactionReceipt;
    }

    public TransactionReceipt updateTopic(ConsensusTopicId topicId) throws HederaStatusException {
        String newMemo = "HCS UpdatedTopic__" + Instant.now().getNano();
        TransactionReceipt transactionReceipt = new ConsensusTopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo(newMemo)
                .setExpirationTime(Instant.now().plus(120, ChronoUnit.DAYS))
                .setAutoRenewPeriod(Duration.ofSeconds(8000000))
                .clearAdminKey()
                .clearSubmitKey()
                .clearTopicMemo()
                .clearAutoRenewAccountId()
//                .setAutoRenewAccountId()
                .build(client)
                .execute(client)
                .getReceipt(client);

        log.debug("Updated topic '{}'. Received transactionReceipt : {} ", topicId, transactionReceipt);
        return transactionReceipt;
    }

    public TransactionReceipt deleteTopic(ConsensusTopicId topicId) throws HederaStatusException {
        TransactionReceipt transactionReceipt = new ConsensusTopicDeleteTransaction()
                .setTopicId(topicId)
                .execute(client)
                .getReceipt(client);

        log.debug("Deleted topic : '{}'. Obtained receipt : {}", topicId, transactionReceipt);

        return transactionReceipt;
    }

    public List<TransactionReceipt> publishMessagesToTopic(ConsensusTopicId topicId, String baseMessage,
                                                           Ed25519PrivateKey submitKey,
                                                           int numMessages, boolean verify) throws HederaStatusException
            , InterruptedException {
        log.debug("Publishing {} messages to topicId : {}.", numMessages, topicId);
        Instant refInstant = Instant.now();
        List<TransactionReceipt> transactionReceiptList = new ArrayList<>();
        for (int i = 0; i < numMessages; i++) {
            String message = baseMessage + "_" + refInstant + "_" + i + 1;

            if (verify) {
                transactionReceiptList.add(publishMessageToTopicAndVerify(topicId, message, submitKey));
            } else {
                publishMessageToTopic(topicId, message, submitKey);
            }
        }

        return transactionReceiptList;
    }

    public TransactionId publishMessageToTopic(ConsensusTopicId topicId, String message,
                                               Ed25519PrivateKey submitKey) throws HederaStatusException {
        Transaction transaction = new ConsensusMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message)
                .build(client);

        if (submitKey != null) {
            // The transaction is automatically signed by the payer.
            // Due to the topic having a submitKey requirement, additionally sign the transaction with that key.
            transaction.sign(submitKey);
        }

        TransactionId transactionId = transaction.execute(client);
        // get only the 1st sequence number
        if (recordPublishInstants.size() == 0) {
            recordPublishInstants.put(0L, transactionId.getRecord(client).consensusTimestamp);
        }

        return transactionId;
    }

    public TransactionReceipt publishMessageToTopicAndVerify(ConsensusTopicId topicId, String message,
                                                             Ed25519PrivateKey submitKey) throws HederaStatusException {
        TransactionId transactionId = publishMessageToTopic(topicId, message, submitKey);

        TransactionReceipt transactionReceipt = transactionId.getReceipt(client);

        log.trace("Published message : '{}' to topicId : {} with sequence number : {}", message, topicId,
                transactionReceipt.getConsensusTopicSequenceNumber());

        // note time stamp
        recordPublishInstants.put(transactionReceipt.getConsensusTopicSequenceNumber(), transactionId
                .getRecord(client).consensusTimestamp);

        return transactionReceipt;
    }

    public Instant getInstantOfPublishedMessage(long sequenceNumber) {
        return recordPublishInstants.get(sequenceNumber);
    }

    public Instant getInstantOfFirstPublishedMessage() {
        return recordPublishInstants.get(0L);
    }
}
