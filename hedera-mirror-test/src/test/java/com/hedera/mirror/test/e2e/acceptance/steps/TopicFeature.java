package com.hedera.mirror.test.e2e.acceptance.steps;

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

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.SubscriptionResponse;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;

@Log4j2
@Cucumber
public class TopicFeature {
    @Autowired
    private AcceptanceTestProperties acceptanceProps;
    private int messageSubscribeCount;
    private long latency;
    private MirrorConsensusTopicQuery mirrorConsensusTopicQuery;
    private ConsensusTopicId consensusTopicId;
    private SubscriptionResponse subscriptionResponse;
    private Ed25519PrivateKey submitKey;
    private Instant testInstantReference;
    private List<TransactionReceipt> publishedTransactionReceipts;
    @Autowired
    private MirrorNodeClient mirrorClient;
    @Autowired
    private TopicClient topicClient;

    @Given("I successfully create a new topic id")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewTopic() throws HederaStatusException {
        testInstantReference = Instant.now();

        submitKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey submitPublicKey = submitKey.publicKey;
        log.debug("Topic creation PrivateKey : {}, PublicKey : {}", submitKey, submitPublicKey);

        TransactionReceipt receipt = topicClient
                .createTopic(topicClient.getSdkClient().getPayerPublicKey(), submitPublicKey);
        assertNotNull(receipt);
        ConsensusTopicId topicId = receipt.getConsensusTopicId();
        assertNotNull(topicId);

        consensusTopicId = topicId;
        mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery()
                .setTopicId(consensusTopicId)
                .setStartTime(Instant.EPOCH);

        log.debug("Set mirrorConsensusTopicQuery with topic: {}, startTime: {}", consensusTopicId, Instant.EPOCH);
    }

    @Given("I successfully create a new open topic")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewOpenTopic() throws HederaStatusException {
        testInstantReference = Instant.now();

        TransactionReceipt receipt = topicClient
                .createTopic(topicClient.getSdkClient().getPayerPublicKey(), null);
        assertNotNull(receipt);
        ConsensusTopicId topicId = receipt.getConsensusTopicId();
        assertNotNull(topicId);

        consensusTopicId = topicId;
        mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery()
                .setTopicId(consensusTopicId)
                .setStartTime(Instant.EPOCH);

        log.debug("Set mirrorConsensusTopicQuery with topic: {}, startTime: {}", consensusTopicId, Instant.EPOCH);
    }

    @When("I successfully update an existing topic")
    public void updateTopic() throws HederaStatusException {
        TransactionReceipt receipt = topicClient.updateTopic(consensusTopicId);

        assertNotNull(receipt);
    }

    @When("I successfully delete the topic")
    public void deleteTopic() throws HederaStatusException {
        TransactionReceipt receipt = topicClient.deleteTopic(consensusTopicId);

        assertNotNull(receipt);
    }

    @Given("I provide a topic id {string}")
    public void setTopicIdParam(String topicId) {
        testInstantReference = Instant.now();
        mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery();

        Long topicNum = topicId.isEmpty() ? acceptanceProps.getExistingTopicNum() : Long.parseLong(topicId);
        consensusTopicId = new ConsensusTopicId(0, 0, topicNum);

        mirrorConsensusTopicQuery
                .setTopicId(consensusTopicId)
                .setStartTime(Instant.EPOCH);
        log.debug("Set mirrorConsensusTopicQuery with topic: {}, StartTime: {}", consensusTopicId, Instant.EPOCH);

        messageSubscribeCount = 0;
    }

    @Given("I provide a number of messages {int} I want to receive")
    public void setTopicListenParams(int numMessages) {
        messageSubscribeCount = numMessages;
    }

    @Given("I provide a number of messages {int} I want to receive within {int} seconds")
    public void setTopicListenParams(int numMessages, int latency) {
        messageSubscribeCount = numMessages;
        this.latency = latency;
    }

    @Given("I provide a {int} in seconds of which I want to receive messages within")
    public void setSubscribeParams(int latency) {
        this.latency = latency;
    }

    @Given("I provide a starting timestamp {string} and a number of messages {int} I want to receive")
    public void setTopicListenParams(String startTimestamp, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp, testInstantReference);
        log.debug("Subscribe mirrorConsensusTopicQuery startTime : {}", startTime);

        mirrorConsensusTopicQuery
                .setStartTime(startTime);

        log.debug("Set mirrorConsensusTopicQuery with topic: {}, startTime: {}", consensusTopicId, startTime);
    }

    @Given("I provide a starting timestamp {string} and ending timestamp {string} and a number of messages {int} I " +
            "want to receive")
    public void setTopicListenParams(String startTimestamp, String endTimestamp, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp, testInstantReference);
        Instant endTime = FeatureInputHandler.messageQueryDateStringToInstant(endTimestamp, Instant.now());
        log.trace("Set mirrorConsensusTopicQuery with topic: {}, startTime : {}. endTime : {}", consensusTopicId,
                startTime, endTime);

        mirrorConsensusTopicQuery
                .setStartTime(startTime)
                .setEndTime(endTime);
    }

    @Given("I provide a startSequence {int} and endSequence {int} and a number of messages {int} I want to receive")
    public void setTopicListenParams(int startSequence, int endSequence, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = topicClient.getInstantOfPublishedMessage(startSequence - 1).minusMillis(10);
        Instant endTime = topicClient.getInstantOfPublishedMessage(endSequence - 1).plusMillis(10);
        log.trace("Subscribe mirrorConsensusTopicQuery startTime : {}. endTime : {}", startTime, endTime);

        mirrorConsensusTopicQuery
                .setStartTime(startTime)
                .setEndTime(endTime);

        log.debug("Set mirrorConsensusTopicQuery with topic: {}, startTime: {}, endTime: {}",
                consensusTopicId, startTime, endTime);
    }

    @Given("I provide a starting timestamp {string} and ending timestamp {string} and a limit of {int} messages I " +
            "want to receive")
    public void setTopicListenParamswLimit(String startTimestamp, String endTimestamp, int limit) {
        messageSubscribeCount = limit;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp);
        Instant endTime = FeatureInputHandler.messageQueryDateStringToInstant(endTimestamp);
        mirrorConsensusTopicQuery
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setLimit(limit);

        log.debug("Set mirrorConsensusTopicQuery with topic: {}, startTime: {}, endTime: {}, limit: {}",
                consensusTopicId, startTime, endTime, limit);
    }

    @When("I subscribe to the topic")
    public void verifySubscriptionChannelConnection() throws Throwable {
        subscriptionResponse = mirrorClient.subscribeToTopic(mirrorConsensusTopicQuery);
        assertNotNull(subscriptionResponse);
    }

    @When("I publish {int} batches of {int} messages every {long} milliseconds")
    public void publishTopicMessages(int numGroups, int messageCount, long milliSleep) throws InterruptedException,
            HederaStatusException {
        for (int i = 0; i < numGroups; i++) {
            Thread.sleep(milliSleep, 0);
            publishTopicMessages(messageCount);
            log.trace("Emitted {} message(s) in batch {} of {} potential batches. Will sleep {} ms until " +
                    "next batch", messageCount, i + 1, numGroups, milliSleep);
        }

        messageSubscribeCount = numGroups * messageCount;
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void publishTopicMessages(int messageCount) throws InterruptedException, HederaStatusException {
        topicClient.publishMessagesToTopic(consensusTopicId, "New message", submitKey, messageCount, false);
    }

    @When("I publish and verify {int} messages sent")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void publishAndVerifyTopicMessages(int messageCount) throws InterruptedException, HederaStatusException {
        messageSubscribeCount = messageCount;
        publishedTransactionReceipts = topicClient
                .publishMessagesToTopic(consensusTopicId, "New message", submitKey, messageCount, true);
        assertEquals(messageCount, publishedTransactionReceipts.size());
    }

    @Then("I unsubscribe from a topic")
    public void verifyUnSubscribeFromChannelConnection() {
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should successfully establish a channel to this topic")
    public void verifySubscribeAndUnsubscribeChannelConnection() throws Throwable {
        verifySubscriptionChannelConnection();

        verifyUnSubscribeFromChannelConnection();
    }

    @Then("the network should observe an error {string}")
    public void verifySubscribeAndUnsubscribeChannelConnection(String errorCode) throws Throwable {
        try {
            verifySubscriptionChannelConnection();
        } catch (Exception ex) {
            if (!ex.getMessage().contains(errorCode)) {
                log.info("Exception mismatch : {}", ex.getMessage());
                throw new Exception("Unexpected error code returned");
            } else {
                log.warn("Expected error found");
            }
        }
    }

    @Then("I subscribe with a filter to retrieve messages")
    public void retrieveTopicMessages() throws Throwable {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(mirrorConsensusTopicQuery, "mirrorConsensusTopicQuery null");

        subscriptionResponse = subscribeWithBackgroundMessageEmission();
    }

    @Then("I subscribe with a filter to retrieve these published messages")
    public void retrievePublishedTopicMessages() throws Throwable {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(mirrorConsensusTopicQuery, "mirrorConsensusTopicQuery null");

        // get start time from first published messages
        Instant startTime;
        if (publishedTransactionReceipts == null) {
            startTime = topicClient.getInstantOfFirstPublishedMessage();
        } else {
            long firstMessageSeqNum = publishedTransactionReceipts.get(0).getConsensusTopicSequenceNumber();
            startTime = topicClient.getInstantOfPublishedMessage(firstMessageSeqNum);
        }

        mirrorConsensusTopicQuery.setStartTime(startTime);

        log.debug("Set mirrorConsensusTopicQuery with topic: {}, startTime: {}", consensusTopicId, startTime);

        subscriptionResponse = subscribeWithBackgroundMessageEmission();
    }

    @Then("the network should successfully observe these messages")
    public void verifyTopicMessageSubscription() throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        assertEquals(messageSubscribeCount, subscriptionResponse.getMessages().size());
        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should successfully observe {long} messages")
    public void verifyTopicMessageSubscription(long expectedMessageCount) throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        assertEquals(expectedMessageCount, subscriptionResponse.getMessages().size());
        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should confirm valid topic messages were received")
    public void verifyTopicMessages() throws Exception {
        subscriptionResponse.validateReceivedMessages();
    }

    @After
    public void closeClients() {
        try {
            topicClient.getSdkClient().close();
        } catch (Exception ex) {
            log.warn("Error closing SDK client : {}", ex.getMessage());
        }

        if (mirrorClient != null) {
            try {
                mirrorClient.close();
            } catch (Exception ex) {
                log.warn("Error closing mirror client : {}", ex.getMessage());
            }
        }
    }

    /**
     * Subscribe to topic and observe messages while emitting background messages to encourage service file close in
     * environments with low traffic.
     *
     * @return SubscriptionResponse
     * @throws InterruptedException
     */
    public SubscriptionResponse subscribeWithBackgroundMessageEmission() throws Throwable {
        ScheduledExecutorService scheduler = null;
        if (acceptanceProps.isEmitBackgroundMessages()) {
            log.debug("Emit a background message every second during subscription");
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    topicClient.publishMessageToTopic(
                            consensusTopicId,
                            "backgroundMessage".getBytes(StandardCharsets.UTF_8),
                            submitKey);
                } catch (HederaStatusException e) {
                    e.printStackTrace();
                }
            }, 0, 1, TimeUnit.SECONDS);
        }

        SubscriptionResponse subscriptionResponse;

        try {
            subscriptionResponse = mirrorClient
                    .subscribeToTopicAndRetrieveMessages(mirrorConsensusTopicQuery, messageSubscribeCount, latency);
        } finally {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }

        return subscriptionResponse;
    }

    /**
     * Recover method for retry operations Method parameters of retry method must match this method after exception
     * parameter
     *
     * @param t
     */
    @Recover
    public void recover(StatusRuntimeException t) {
        log.error("Transaction submissions for topic operation failed after retries w: {}", t.getMessage());
        throw t;
    }

    /**
     * Recover method for publishTopicMessages retry operations. Method parameters of retry method must match this
     * method after exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(StatusRuntimeException t, int messageCount) {
        log.error("Transaction submissions for message publish failed after retries w: {}", t.getMessage());
        throw t;
    }

    /**
     * Recover method for publishTopicMessages retry operations. Method parameters of retry method must match this
     * method after exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(StatusRuntimeException t, int numGroups, int messageCount, long milliSleep) {
        log.error("Transaction submissions for message publish failed after retries w: {}", t.getMessage());
        throw t;
    }
}
