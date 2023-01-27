package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicInfo;
import com.hedera.hashgraph.sdk.TopicInfoQuery;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Named
public class TopicClient extends AbstractNetworkClient {

    private static final Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    private final Map<Long, Instant> recordPublishInstants;

    public TopicClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        recordPublishInstants = new ConcurrentHashMap<>();
    }

    public NetworkTransactionResponse createTopic(ExpandedAccountId adminAccount, PublicKey submitKey) {
        String memo = getMemo("Create Topic");
        TopicCreateTransaction consensusTopicCreateTransaction = new TopicCreateTransaction()
                .setAdminKey(adminAccount.getPublicKey())
                .setAutoRenewAccountId(sdkClient.getExpandedOperatorAccountId().getAccountId())
                .setTopicMemo(memo)
                .setTransactionMemo(memo)
                .setAutoRenewPeriod(autoRenewPeriod); // INSUFFICIENT_TX_FEE, also unsupported

        if (submitKey != null) {
            consensusTopicCreateTransaction.setSubmitKey(submitKey);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(consensusTopicCreateTransaction,
                        KeyList.of(adminAccount.getPrivateKey()));
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        log.debug("Created new topic {}", topicId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateTopic(TopicId topicId) {
        String memo = getMemo("Update Topic");
        TopicUpdateTransaction consensusTopicUpdateTransaction = new TopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo(memo)
                .setAutoRenewPeriod(autoRenewPeriod)
                .clearAdminKey()
                .clearSubmitKey()
                .clearAutoRenewAccountId()
                .setTransactionMemo(memo);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(consensusTopicUpdateTransaction);

        log.debug("Updated topic '{}'.", topicId);
        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteTopic(TopicId topicId) {
        TopicDeleteTransaction consensusTopicDeleteTransaction = new TopicDeleteTransaction()
                .setTopicId(topicId)
                .setTransactionMemo(getMemo("Delete Topic"));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(consensusTopicDeleteTransaction);

        log.debug("Deleted topic : '{}'.", topicId);

        return networkTransactionResponse;
    }

    public List<TransactionReceipt> publishMessagesToTopic(TopicId topicId, String baseMessage,
                                                           KeyList submitKeys, int numMessages,
                                                           boolean verify) {
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

    public TransactionId publishMessageToTopic(TopicId topicId, byte[] message, KeyList submitKeys) {
        TopicMessageSubmitTransaction consensusMessageSubmitTransaction = new TopicMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message)
                .setTransactionMemo(getMemo("Publish topic message"));

        TransactionId transactionId = executeTransaction(consensusMessageSubmitTransaction, submitKeys);

        TransactionRecord transactionRecord = getTransactionRecord(transactionId);
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

    public TransactionReceipt publishMessageToTopicAndVerify(TopicId topicId, byte[] message, KeyList submitKeys) {
        TransactionId transactionId = publishMessageToTopic(topicId, message, submitKeys);
        TransactionReceipt transactionReceipt = null;
        try {
            transactionReceipt = getTransactionReceipt(transactionId);

            // note time stamp
            recordPublishInstants.put(
                    transactionReceipt.topicSequenceNumber,
                    getTransactionRecord(transactionId).consensusTimestamp);
        } catch (Exception e) {
            log.error("Error retrieving transaction receipt", e);
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

    public TopicInfo getTopicInfo(TopicId topicId) {
        return executeQuery(() -> new TopicInfoQuery().setTopicId(topicId));
    }
}
