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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
public class TopicClient extends AbstractNetworkClient {
    private static final Duration autoRenewPeriod = Duration.ofSeconds(8000000);
    private final Map<Long, Instant> recordPublishInstants;
    private TopicId defaultTopicId = null;

    public TopicClient(SDKClient sdkClient) {
        super(sdkClient);
        recordPublishInstants = new HashMap<>();
        log.debug("Creating Topic Client");
    }

    public NetworkTransactionResponse createTopic(ExpandedAccountId adminAccount, PublicKey submitKey) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        Instant refInstant = Instant.now();
        String memo = "HCS Topic_" + refInstant;
        TopicCreateTransaction consensusTopicCreateTransaction = new TopicCreateTransaction()
                .setAdminKey(adminAccount.getPublicKey())
                .setAutoRenewAccountId(sdkClient.getOperatorId())
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTopicMemo(memo)
                .setTransactionMemo(memo)
                .setAutoRenewPeriod(autoRenewPeriod); // INSUFFICIENT_TX_FEE, also unsupported

        if (submitKey != null) {
            consensusTopicCreateTransaction.setSubmitKey(submitKey);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(consensusTopicCreateTransaction, KeyList
                        .of(adminAccount.getPrivateKey()));
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        log.debug("Created new topic {}", topicId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateTopic(TopicId topicId) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        String newMemo = "HCS UpdatedTopic__" + Instant.now().getNano();
        TopicUpdateTransaction consensusTopicUpdateTransaction = new TopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo(newMemo)
                .setAutoRenewPeriod(autoRenewPeriod)
                .clearAdminKey()
                .clearSubmitKey()
                .clearTopicMemo()
                .clearAutoRenewAccountId(sdkClient.getOperatorId())
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee());

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(consensusTopicUpdateTransaction,
                        null);

        log.debug("Updated topic '{}'.", topicId);
        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteTopic(TopicId topicId) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        TopicDeleteTransaction consensusTopicDeleteTransaction = new TopicDeleteTransaction()
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTopicId(topicId);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(consensusTopicDeleteTransaction,
                        null);

        log.debug("Deleted topic : '{}'.", topicId);

        return networkTransactionResponse;
    }

    public List<TransactionReceipt> publishMessagesToTopic(TopicId topicId, String baseMessage,
                                                           KeyList submitKeys, int numMessages,
                                                           boolean verify) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
        log.debug("Publishing {} message(s) to topicId : {}.", numMessages, topicId);
        List<TransactionReceipt> transactionReceiptList = new ArrayList<>();
        for (int i = 0; i < numMessages; i++) {
            byte[] publishTimestampByteArray = Longs.toByteArray(System.currentTimeMillis());
            byte[] suffixByteArray = ("_" + baseMessage + "_" + (i + 1)).getBytes(StandardCharsets.UTF_8);
            byte[] message = ArrayUtils.addAll(publishTimestampByteArray, suffixByteArray);

            if (verify) {
                transactionReceiptList.add(publishMessageToTopicAndVerify(topicId, message, submitKeys));
            } else {
                publishMessageToTopic(topicId, message, submitKeys);
            }
        }

        return transactionReceiptList;
    }

    public TopicMessageSubmitTransaction getTopicMessageSubmitTransaction(TopicId topicId, byte[] message) {
        return new TopicMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message);
    }

    public TopicId getDefaultTopicId() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        if (defaultTopicId == null) {
            NetworkTransactionResponse networkTransactionResponse = createTopic(sdkClient
                    .getExpandedOperatorAccountId(), null);
            defaultTopicId = networkTransactionResponse.getReceipt().topicId;
            log.debug("Created TopicId: '{}' for use in current test session", defaultTopicId);
        }

        return defaultTopicId;
    }

    public void publishMessageToDefaultTopic() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        publishMessagesToTopic(getDefaultTopicId(), "Background message", null, 1, false);
    }

    public TransactionId publishMessageToTopic(TopicId topicId, byte[] message, KeyList submitKeys) throws TimeoutException, PrecheckStatusException, ReceiptStatusException {
        TopicMessageSubmitTransaction consensusMessageSubmitTransaction = new TopicMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message);

        TransactionId transactionId = executeTransaction(consensusMessageSubmitTransaction, submitKeys);

        TransactionRecord transactionRecord = transactionId.getRecord(client);
        // get only the 1st sequence number
        if (recordPublishInstants.size() == 0) {
            recordPublishInstants.put(0L, transactionRecord.consensusTimestamp);
        }

        if (log.isTraceEnabled()) {
            log.trace("Published message : '{}' to topicId : {} with consensusTimestamp: {}",
                    new String(message, StandardCharsets.UTF_8), topicId, transactionRecord.consensusTimestamp);
        }

        return transactionId;
    }

    public TransactionReceipt publishMessageToTopicAndVerify(TopicId topicId, byte[] message, KeyList submitKeys) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        TransactionId transactionId = publishMessageToTopic(topicId, message, submitKeys);
        TransactionReceipt transactionReceipt = null;
        try {
            transactionReceipt = transactionId.getReceipt(client);

            // note time stamp
            recordPublishInstants.put(transactionReceipt.topicSequenceNumber, transactionId
                    .getRecord(client).consensusTimestamp);
        } catch (TimeoutException | PrecheckStatusException | ReceiptStatusException e) {
            e.printStackTrace();
        }

        log.trace("Verified message published : '{}' to topicId : {} with sequence number : {}", message, topicId,
                transactionReceipt.topicSequenceNumber);

        return transactionReceipt;
    }

    public Instant getInstantOfPublishedMessage(long sequenceNumber) {
        return recordPublishInstants.get(sequenceNumber);
    }

    public Instant getInstantOfFirstPublishedMessage() {
        return recordPublishInstants.get(0L);
    }
}
