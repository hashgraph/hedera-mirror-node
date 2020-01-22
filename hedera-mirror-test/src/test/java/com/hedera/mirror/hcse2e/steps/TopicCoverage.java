package com.hedera.mirror.hcse2e.steps;
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

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.cdimascio.dotenv.Dotenv;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusClient;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.mirror.hcse2e.util.MirrorNodeClient;
import com.hedera.mirror.hcse2e.util.SDKClient;
import com.hedera.mirror.hcse2e.util.TopicHelper;

@Log4j2
public class TopicCoverage {
    String memo;
    Long maxFee;
    long topicId;
    long autoRenew;
    int numMessages;
    Long sleepBetweenMessages;
    int latency;
    Instant startDate = Instant.EPOCH;
    Client sdkClient;
    MirrorNodeClient mirrorClient;
    ConsensusTopicId consensusTopicId;
    //    Pair<Ed25519PrivateKey, ConsensusTopicId> createTopicResponse;
    TransactionId transactionId;
    ConsensusClient.Subscription subscription;
    Pair<ConsensusClient.Subscription, Boolean> messageSubscribeResult;
    //    TransactionReceipt transactionReceipt;
    List<TransactionReceipt> transactionReceipts;

    @Given("User obtained SDK client")
    public void getSDKClient() throws HederaStatusException {
        if (sdkClient == null) {
            sdkClient = SDKClient.hederaClient();
        }
    }

    @Given("User obtained Mirror Node Consensus client")
    public void getMirrorNodeClient() {
        if (mirrorClient == null) {
            mirrorClient = new MirrorNodeClient();
        }
    }

    @Given("I provide a memo {string} and a max transaction fee of {long}")
    public void setTopicCreateParams(String memo, Long maxFee) {
        this.memo = memo;
        this.maxFee = maxFee;
    }

    @Given("I provide a topic id {long}")
    public void setTopicIdParam(Long topicId) {
        this.topicId = topicId;
        consensusTopicId = new ConsensusTopicId(0, 0, topicId);
        Assert.assertNotNull(consensusTopicId);
    }

    @Given("I provide a topic id {long}, memo {string} and an auto renew period of {long}")
    public void setTopicUpdateParams(Long topicId, String memo, Long autoRenew) {
        setTopicIdParam(topicId);
        this.memo = memo;
        this.autoRenew = autoRenew;
    }

    @Given("I provide a topic id {long}, a number of messages {int}  and a sleep time between them {long}")
    public void setTopicPublishParams(Long topicId, int numMessages, Long sleepBetweenMessages) {
        setTopicIdParam(topicId);
        this.numMessages = numMessages;
        this.sleepBetweenMessages = sleepBetweenMessages;
    }

    @Given("I provide a topic id {long} and a number {int} I want to receive within {int} seconds")
    public void setTopicListenParams(Long topicId, int numMessages, int latency) {
        setTopicIdParam(topicId);
        this.numMessages = numMessages;
        this.latency = latency;
    }

    @Given("I provide a topic id {long} and a date {string} and a number {int} I want to receive")
    public void setTopicListenParams(Long topicId, String startDate, int numMessages) {
        setTopicIdParam(topicId);
        this.numMessages = numMessages;
        this.startDate = Instant.parse(startDate);
    }

    @When("I attempt to create a new topic id")
    public void createNewTopic() throws HederaStatusException {
        TopicHelper topicHelper = new TopicHelper(sdkClient);
        transactionReceipts = new ArrayList();
        TransactionReceipt receipt = topicHelper.createTopic(memo, maxFee);
        consensusTopicId = receipt.getConsensusTopicId();
        transactionReceipts.add(receipt);
    }

    @When("I attempt to update an existing topic")
    public void updateTopic() throws HederaStatusException {
        TopicHelper topicHelper = new TopicHelper(sdkClient);
        transactionReceipts = new ArrayList();
        transactionReceipts.add(topicHelper.updateTopic(consensusTopicId, memo, autoRenew));
    }

    @When("I subscribe to the topic")
    public void verifySubscriptionChannelConnection() {
        subscription = mirrorClient.subscribeToTopic(consensusTopicId, Instant.now());
        Assert.assertNotNull(subscription);
    }

    @When("I publish random messages")
    public void verifyTopicMessagePublish() throws InterruptedException, HederaStatusException {
        TopicHelper topicHelper = new TopicHelper(sdkClient);
        Ed25519PrivateKey submitPrivateKey = Ed25519PrivateKey
                .fromString(Dotenv.load().get("TOPIC_SUBMIT_PRIVATE_KEY"));
        transactionReceipts = topicHelper
                .publishMessagesToTopic(consensusTopicId, "New message", submitPrivateKey, numMessages,
                        sleepBetweenMessages);
        Assert.assertEquals(numMessages, transactionReceipts.size());
    }

    @Then("all clients are established")
    public void verifyClients() {
        Assert.assertNotNull(sdkClient);
        Assert.assertNotNull(mirrorClient);
        log.debug("Verified non null mirrorClient");
    }

    @Then("the network should successfully confirm the transaction for this operation")
    public void verifyTransactionId() {
        Assert.assertNotNull(transactionId);
    }

    @Then("I unsubscribe from a topic")
    public void verifyUnSubscribeFromChannelConnection() {
        mirrorClient.unSubscribeFromTopic(subscription);
    }

    @Then("I attempt to delete the topic")
    public void deleteTopic() throws HederaStatusException {

        TopicHelper topicHelper = new TopicHelper(sdkClient);
        transactionReceipts = new ArrayList();
        transactionReceipts.add(topicHelper.deleteTopic(consensusTopicId));
    }

    @Then("the network should successfully establish a channel to this topic")
    public void verifySubscribeAndUnsubscribeChannelConnection() {
        verifySubscriptionChannelConnection();

        verifyUnSubscribeFromChannelConnection();
    }

    @Then("I subscribe with a filter to retrieve messages")
    public void retrieveTopicMessages() throws InterruptedException {
        messageSubscribeResult = mirrorClient
                .subscribeToTopicAndRetrieveMessages(consensusTopicId, startDate, numMessages, latency);
        Assert.assertNotNull(messageSubscribeResult);
        subscription = messageSubscribeResult.getLeft();
    }

    @Then("the network should successfully observe these messages")
    public void verifyTopicMessageSubscription() {
        ConsensusClient.Subscription subscription = messageSubscribeResult.getLeft();
        Assert.assertNotNull(subscription);
        mirrorClient.unSubscribeFromTopic(subscription);

        Assert.assertTrue(messageSubscribeResult.getRight());
    }

//    @Then("the network received a valid transaction receipt")
//    public void verifyTransactionReceipt() {
//        Assert.assertNotNull(transactionReceipt);
//    }

    @Then("the network should confirm valid transaction receipts for this operation")
    public void verifyTransactionReceipts() {
        for (TransactionReceipt receipt : transactionReceipts) {
            Assert.assertNotNull(receipt);
        }
    }

    @After
    public void closeClients() {

        if (sdkClient != null) {
            try {
                sdkClient.close();
            } catch (Exception ex) {
                log.warn("Error closing SDK client : {}", ex);
            }
        }

        if (mirrorClient != null) {
            try {
                mirrorClient.close();
            } catch (Exception ex) {
                log.warn("Error closing mirror client : {}", ex);
            }
        }
    }
}
