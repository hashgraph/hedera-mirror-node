package com.hedera.mirror.test.e2e.acceptance.steps;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicInfo;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.client.SubscriptionResponse;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;

@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TopicFeature {

    private final AcceptanceTestProperties acceptanceProps;
    private final MirrorNodeClient mirrorClient;
    private final SDKClient sdkClient;
    private final TopicClient topicClient;

    private int messageSubscribeCount;
    private long latency;
    private TopicMessageQuery topicMessageQuery;
    private TopicId consensusTopicId;
    private SubscriptionResponse subscriptionResponse;
    private PrivateKey submitKey;
    private Instant testInstantReference;
    private List<TransactionReceipt> publishedTransactionReceipts;

    @Given("I successfully create a new topic id")
    public void createNewTopic() {
        testInstantReference = Instant.now();

        submitKey = PrivateKey.generate();
        PublicKey submitPublicKey = submitKey.getPublicKey();
        log.trace("Topic creation PrivateKey : {}, PublicKey : {}", submitKey, submitPublicKey);

        NetworkTransactionResponse networkTransactionResponse = topicClient
                .createTopic(topicClient.getSdkClient().getExpandedOperatorAccountId(), submitPublicKey);
        assertNotNull(networkTransactionResponse.getReceipt());
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        assertNotNull(topicId);

        consensusTopicId = topicId;
        topicMessageQuery = new TopicMessageQuery()
                .setTopicId(consensusTopicId)
                .setStartTime(Instant.EPOCH);

        log.debug("Set TopicMessageQuery with topic: {}, startTime: {}", consensusTopicId, Instant.EPOCH);
    }

    @Given("I successfully create a new open topic")
    public void createNewOpenTopic() {
        testInstantReference = Instant.now();

        NetworkTransactionResponse networkTransactionResponse = topicClient
                .createTopic(topicClient.getSdkClient().getExpandedOperatorAccountId(), null);
        assertNotNull(networkTransactionResponse.getReceipt());
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        assertNotNull(topicId);

        consensusTopicId = topicId;
        topicMessageQuery = new TopicMessageQuery()
                .setTopicId(consensusTopicId)
                .setStartTime(Instant.EPOCH);

        log.debug("Set TopicMessageQuery with topic: {}, startTime: {}", consensusTopicId, Instant.EPOCH);
    }

    @When("I successfully update an existing topic")
    public void updateTopic() {
        TransactionReceipt receipt = topicClient.updateTopic(consensusTopicId).getReceipt();

        assertNotNull(receipt);
    }

    @When("I successfully delete the topic")
    public void deleteTopic() {
        TransactionReceipt receipt = topicClient.deleteTopic(consensusTopicId).getReceipt();

        assertNotNull(receipt);
    }

    @Given("I provide a topic id {string}")
    public void setTopicIdParam(String topicId) {
        testInstantReference = Instant.now();
        topicMessageQuery = new TopicMessageQuery().setStartTime(Instant.EPOCH);
        consensusTopicId = null;

        if (!topicId.isEmpty()) {
            consensusTopicId = new TopicId(0, 0, Long.parseLong(topicId));
            topicMessageQuery.setTopicId(consensusTopicId);
        }

        log.debug("Set TopicMessageQuery with topic: {}, StartTime: {}", consensusTopicId, Instant.EPOCH);
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
        log.debug("Subscribe TopicMessageQuery startTime : {}", startTime);

        topicMessageQuery.setStartTime(startTime);

        log.debug("Set TopicMessageQuery with topic: {}, startTime: {}", consensusTopicId, startTime);
    }

    @Given("I provide a starting timestamp {string} and ending timestamp {string} and a number of messages {int} I " +
            "want to receive")
    public void setTopicListenParams(String startTimestamp, String endTimestamp, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp, testInstantReference);
        Instant endTime = FeatureInputHandler.messageQueryDateStringToInstant(endTimestamp, Instant.now());
        log.trace("Set TopicMessageQuery with topic: {}, startTime : {}. endTime : {}", consensusTopicId,
                startTime, endTime);

        topicMessageQuery
                .setStartTime(startTime)
                .setEndTime(endTime);
    }

    @Given("I provide a startSequence {int} and endSequence {int} and a number of messages {int} I want to receive")
    public void setTopicListenParams(int startSequence, int endSequence, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = topicClient.getInstantOfPublishedMessage(startSequence - 1).minusMillis(10);
        Instant endTime = topicClient.getInstantOfPublishedMessage(endSequence - 1).plusMillis(10);
        log.trace("Subscribe TopicMessageQuery startTime : {}. endTime : {}", startTime, endTime);

        topicMessageQuery
                .setStartTime(startTime)
                .setEndTime(endTime);

        log.debug("Set TopicMessageQuery with topic: {}, startTime: {}, endTime: {}",
                consensusTopicId, startTime, endTime);
    }

    @Given("I provide a starting timestamp {string} and ending timestamp {string} and a limit of {int} messages I " +
            "want to receive")
    public void setTopicListenParamswLimit(String startTimestamp, String endTimestamp, int limit) {
        messageSubscribeCount = limit;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp);
        Instant endTime = FeatureInputHandler.messageQueryDateStringToInstant(endTimestamp);
        topicMessageQuery
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setLimit(limit);

        log.debug("Set TopicMessageQuery with topic: {}, startTime: {}, endTime: {}, limit: {}",
                consensusTopicId, startTime, endTime, limit);
    }

    @When("I subscribe to the topic")
    public void verifySubscriptionChannelConnection() throws Throwable {
        subscriptionResponse = mirrorClient.subscribeToTopic(sdkClient, topicMessageQuery);
        assertNotNull(subscriptionResponse);
    }

    @When("I publish {int} batches of {int} messages every {long} milliseconds")
    @Retryable(value = {PrecheckStatusException.class, ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")

    @SuppressWarnings("java:S2925")
    public void publishTopicMessages(int numGroups, int messageCount, long milliSleep) throws InterruptedException {
        for (int i = 0; i < numGroups; i++) {
            Thread.sleep(milliSleep, 0);
            publishTopicMessages(messageCount);
            log.trace("Emitted {} message(s) in batch {} of {} potential batches. Sleeping {} ms",
                    messageCount, i + 1, numGroups, milliSleep);
        }

        messageSubscribeCount = numGroups * messageCount;
    }

    @Retryable(value = {PrecheckStatusException.class, ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void publishTopicMessages(int messageCount) {
        messageSubscribeCount = messageCount;
        topicClient.publishMessagesToTopic(consensusTopicId, "New message", getSubmitKeys(), messageCount, false);
    }

    @When("I publish and verify {int} messages sent")
    @Retryable(value = {AssertionError.class, PrecheckStatusException.class,
            ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void publishAndVerifyTopicMessages(int messageCount) {
        messageSubscribeCount = messageCount;
        publishedTransactionReceipts = topicClient
                .publishMessagesToTopic(consensusTopicId, "New message", getSubmitKeys(), messageCount, true);
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
        assertThatThrownBy(this::verifySubscriptionChannelConnection)
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining(errorCode);
    }

    @Then("I subscribe with a filter to retrieve messages")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void retrieveTopicMessages() throws Throwable {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(topicMessageQuery, "TopicMessageQuery null");

        subscriptionResponse = subscribeWithBackgroundMessageEmission();
    }

    @Then("I subscribe with a filter to retrieve these published messages")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void retrievePublishedTopicMessages() throws Throwable {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(topicMessageQuery, "TopicMessageQuery null");

        // get start time from first published messages
        Instant startTime;
        if (publishedTransactionReceipts == null) {
            startTime = topicClient.getInstantOfFirstPublishedMessage();
        } else {
            long firstMessageSeqNum = publishedTransactionReceipts.get(0).topicSequenceNumber;
            startTime = topicClient.getInstantOfPublishedMessage(firstMessageSeqNum);
        }

        topicMessageQuery.setStartTime(startTime);

        log.debug("Set TopicMessageQuery with topic: {}, startTime: {}", consensusTopicId, startTime);

        subscriptionResponse = subscribeWithBackgroundMessageEmission();
    }

    @Then("the network should successfully observe the topic")
    public void verifyTopicOnNetwork() {
        TopicInfo topicInfo = topicClient.getTopicInfo(consensusTopicId);
        assertNotNull(topicInfo, "topicInfo is null");
        assertNotEquals("", topicInfo.topicMemo, "topicMemo is not empty");
        assertThat(topicInfo.sequenceNumber).isGreaterThanOrEqualTo(messageSubscribeCount);
    }

    @Then("the network should successfully observe these messages")
    public void verifyTopicMessageSubscription() throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should successfully observe {long} messages")
    public void verifyTopicMessageSubscription(long expectedMessageCount) throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should confirm valid topic messages were received")
    public void verifyTopicMessages() throws Exception {
        subscriptionResponse.validateReceivedMessages();
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
                            getSubmitKeys());
                } catch (Exception e) {
                    log.error("Error publishing to topic", e);
                }
            }, 0, 1, TimeUnit.SECONDS);
        }

        SubscriptionResponse subscriptionResponse;

        try {
            subscriptionResponse = mirrorClient
                    .subscribeToTopicAndRetrieveMessages(sdkClient, topicMessageQuery, messageSubscribeCount, latency);
            assertEquals(messageSubscribeCount, subscriptionResponse.getMirrorHCSResponses().size());
        } finally {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }

        return subscriptionResponse;
    }

    private KeyList getSubmitKeys() {
        return submitKey == null ? null : KeyList.of(submitKey);
    }
}
