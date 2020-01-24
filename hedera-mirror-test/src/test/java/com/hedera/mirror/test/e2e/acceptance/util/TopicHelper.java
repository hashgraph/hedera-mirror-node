package com.hedera.mirror.test.e2e.acceptance.util;

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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicDeleteTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;

@Log4j2
public class TopicHelper {

    private final Client client;

    public TopicHelper(Client client) {
        this.client = client;
    }

    public TransactionReceipt createTopic(Ed25519PublicKey submitPublicKey) throws HederaStatusException {

        Instant refInstant = Instant.now();
        TransactionReceipt transactionReceipt = new ConsensusTopicCreateTransaction()
//                .setAdminKey(submitPublicKey) // INVALID_SIGNATURE when of above keys are used
                .setSubmitKey(submitPublicKey)
//                .setAutoRenewAccountId(AccountId.fromString("0.0.2")) // AUTORENEW_ACCOUNT_NOT_ALLOWED
                .setMaxTransactionFee(1_000_000_000)
                .setTopicMemo("HCS Topic_" + refInstant)
//                .setAutoRenewPeriod(Duration.ofDays(5)) // AUTORENEW_DURATION_NOT_IN_RANGE - 30 * 86400L
//                .setNodeAccountId()
//                .setTransactionId()
//                .setTransactionMemo("HCS Topic Creation_" + refInstant)
//                .setTransactionValidDuration(Duration.ofDays(1))
                .execute(client)
                .getReceipt(client);

        ConsensusTopicId topicId = transactionReceipt.getConsensusTopicId();
        log.debug("Created new topic {}, with TransactionReceipt : {}", topicId, transactionReceipt);

        return transactionReceipt;
    }

    public TransactionReceipt updateTopic(ConsensusTopicId topicId, Ed25519PrivateKey submitKey) throws HederaStatusException {
        String newMemo = "HCS UpdatedTopic__" + Instant.now().getNano();
        TransactionReceipt transactionReceipt = new ConsensusTopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo(newMemo)
                .setAutoRenewPeriod(Duration.ofDays(12))
                .build(client)
                .sign(submitKey)
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
                                                           int numMessages) throws HederaStatusException
            , InterruptedException {
        log.debug("Publishing {} messages to topicId : {}.", numMessages, topicId);
        Instant refInstant = Instant.now();
        List<TransactionReceipt> transactionReceiptList = new ArrayList<>();
        for (int i = 0; i < numMessages; i++) {
            String message = baseMessage + "_" + refInstant + "_" + i + 1;
            transactionReceiptList.add(publishMessageToTopic(topicId, message, submitKey));
            Thread.sleep(500, 0);
        }

        return transactionReceiptList;
    }

    public TransactionReceipt publishMessageToTopic(ConsensusTopicId topicId, String message,
                                                    Ed25519PrivateKey submitKey) throws HederaStatusException {
        TransactionReceipt transactionReceipt = new ConsensusMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message)
                .build(client)
                // The transaction is automatically signed by the payer.
                // Due to the topic having a submitKey requirement, additionally sign the transaction with that key.
                .sign(submitKey)
                .execute(client)
                .getReceipt(client);

        log.debug("Published message : '{}' to topicId : {} with sequence number : {}", message, topicId,
                transactionReceipt.getConsensusTopicSequenceNumber());

        return transactionReceipt;
    }

    public void processReceivedMessages(List<MirrorConsensusTopicResponse> messages) throws Exception {
        int invalidMessages = 0;
        MirrorConsensusTopicResponse lastMirrorConsensusTopicResponse = null;
        for (MirrorConsensusTopicResponse mirrorConsensusTopicResponse : messages) {
            String messageAsString = new String(mirrorConsensusTopicResponse.message, StandardCharsets.UTF_8);
            log.info("Received message: {}, consensus timestamp: {}, topic sequence number: {}",
                    messageAsString, mirrorConsensusTopicResponse.consensusTimestamp,
                    mirrorConsensusTopicResponse.sequenceNumber);

            if (!validateResponse(lastMirrorConsensusTopicResponse, mirrorConsensusTopicResponse)) {
                invalidMessages++;
            }

            lastMirrorConsensusTopicResponse = mirrorConsensusTopicResponse;
        }

        if (invalidMessages > 0) {
            throw new Exception("Retrieved {} invalid messages in response");
        }

        log.info("{} messages were successfully validated", messages.size());
    }

    public boolean validateResponse(MirrorConsensusTopicResponse previousResponse,
                                    MirrorConsensusTopicResponse currentResponse) {
        boolean validResponse = true;

        if (previousResponse != null && currentResponse != null) {
            if (previousResponse.consensusTimestamp.isAfter(currentResponse.consensusTimestamp)) {
                log.error("Previous message {}, has a timestamp greater than current message {}",
                        previousResponse.consensusTimestamp, currentResponse.consensusTimestamp);
                validResponse = false;
            }

            if (previousResponse.sequenceNumber + 1 != currentResponse.sequenceNumber) {
                log.error("Previous message {}, has a sequenceNumber greater than current message {}",
                        previousResponse.sequenceNumber, currentResponse.sequenceNumber);
                validResponse = false;
            }
        }

        return validResponse;
    }
}
