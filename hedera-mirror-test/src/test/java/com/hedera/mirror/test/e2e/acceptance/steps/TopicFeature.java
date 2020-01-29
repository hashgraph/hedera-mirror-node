package com.hedera.mirror.test.e2e.acceptance.steps;

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

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.client.SubscriptionResponse;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;

@Log4j2
public class TopicFeature {
    int numMessages;
    int latency;
    MirrorConsensusTopicQuery mirrorConsensusTopicQuery;
    SDKClient sdkClient;
    MirrorNodeClient mirrorClient;
    TopicClient topicClient;
    ConsensusTopicId consensusTopicId;
    SubscriptionResponse subscriptionResponse;
    List<TransactionReceipt> transactionReceipts;
    Ed25519PrivateKey submitKey;
    List<MirrorConsensusTopicResponse> mirrorConsensusTopicResponses;

    @Given("User obtained SDK client")
    public void getSDKClient() throws HederaStatusException {
        if (sdkClient == null) {
            sdkClient = new SDKClient();
        }
    }

    @Given("User obtained Mirror Node Consensus client")
    public void getMirrorNodeClient() {
        if (mirrorClient == null) {
            mirrorClient = new MirrorNodeClient();
        }
    }

    @Given("I attempt to create a new topic id")
    public void createNewTopic() throws HederaStatusException {
        if (consensusTopicId == null) {
            topicClient = new TopicClient(sdkClient.getClient());
            transactionReceipts = new ArrayList();

            submitKey = Ed25519PrivateKey.generate();
            Ed25519PublicKey submitPublicKey = submitKey.publicKey;
            log.debug("Topic creation PrivateKey : {}, PublicKey : {}", submitKey, submitPublicKey);

            TransactionReceipt receipt = topicClient.createTopic(sdkClient.getPayerPublicKey(), submitPublicKey);
            consensusTopicId = receipt.getConsensusTopicId();
            mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery()
                    .setTopicId(consensusTopicId)
                    .setStartTime(Instant.EPOCH);

            transactionReceipts.add(receipt);
        }
    }

    @Given("I provide a topic id {string}")
    public void setTopicIdParam(String topicId) {
        mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery();
        if (!topicId.isEmpty()) {
            consensusTopicId = new ConsensusTopicId(0, 0, Long.parseLong(topicId));
            mirrorConsensusTopicQuery.setTopicId(consensusTopicId);
        }

        numMessages = 0;
    }

    @Given("I provide a number of messages {int} I want to receive")
    public void setTopicListenParams(int numMessages) {
        this.numMessages = numMessages;
    }

    @Given("I provide a number of messages {int} I want to receive within {int} seconds")
    public void setTopicListenParams(int numMessages, int latency) {
        this.numMessages = numMessages;
        this.latency = latency;
    }

    @Given("I provide a date {string} and a number of messages {int} I want to receive")
    public void setTopicListenParams(String startDate, int numMessages) {
        this.numMessages = numMessages;
        mirrorConsensusTopicQuery.setStartTime(Instant.parse(startDate));
    }

    @Given("I provide a startDate {string} and endDate {string} and a number of messages {int} I want to receive")
    public void setTopicListenParams(String startDate, String endDate, int numMessages) {
        this.numMessages = numMessages;
        mirrorConsensusTopicQuery
                .setStartTime(Instant.parse(startDate))
                .setEndTime(Instant.parse(endDate));
    }

    @Given("I provide a startDate {string} and endDate {string} and a limit of {int} messages I want to receive")
    public void setTopicListenParamswLimit(String startDate, String endDate, int limit) {
        numMessages = limit;
        mirrorConsensusTopicQuery
                .setStartTime(Instant.parse(startDate))
                .setEndTime(Instant.parse(endDate))
                .setLimit(limit);
    }

    @When("I attempt to update an existing topic")
    public void updateTopic() throws HederaStatusException {
        TopicClient topicClient = new TopicClient(sdkClient.getClient());
        transactionReceipts = new ArrayList();
        transactionReceipts.add(topicClient.updateTopic(consensusTopicId));
    }

    @When("I subscribe to the topic")
    public void verifySubscriptionChannelConnection() throws Throwable {
        subscriptionResponse = mirrorClient.subscribeToTopic(mirrorConsensusTopicQuery);
        assertNotNull(subscriptionResponse);
    }

    @When("I publish random messages")
    public void verifyTopicMessagePublish() throws InterruptedException, HederaStatusException {
        TopicClient topicClient = new TopicClient(sdkClient.getClient());
        transactionReceipts = topicClient
                .publishMessagesToTopic(consensusTopicId, "New message", submitKey, numMessages);
        assertEquals(numMessages, transactionReceipts.size());
    }

    @When("I publish {int} messages")
    public void verifyTopicMessagePublish(int messageCount) throws InterruptedException, HederaStatusException {
        TopicClient topicClient = new TopicClient(sdkClient.getClient());
        transactionReceipts = topicClient
                .publishMessagesToTopic(consensusTopicId, "New message", submitKey, messageCount);
        assertEquals(numMessages, transactionReceipts.size());
    }

    @When("I attempt to delete the topic")
    public void deleteTopic() throws HederaStatusException {

        TopicClient topicClient = new TopicClient(sdkClient.getClient());
        transactionReceipts = new ArrayList();
        transactionReceipts.add(topicClient.deleteTopic(consensusTopicId));
    }

    @Then("all setup items were configured")
    public void verifySetup() {
        assertNotNull(sdkClient);
        assertNotNull(mirrorClient);
        log.trace("Verified non null setup items");
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
    public void retrieveTopicMessages() throws Exception {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(mirrorConsensusTopicQuery, "mirrorConsensusTopicQuery null");

        subscriptionResponse = mirrorClient
                .subscribeToTopicAndRetrieveMessages(mirrorConsensusTopicQuery, numMessages, latency);
    }

    @Then("the network should successfully observe these messages")
    public void verifyTopicMessageSubscription() throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        assertEquals(numMessages, subscriptionResponse.getMessages().size());
        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should confirm valid transaction receipts for this operation")
    public void verifyTransactionReceipts() {
        for (TransactionReceipt receipt : transactionReceipts) {
            assertNotNull(receipt);
        }
    }

    @Then("the network should confirm valid topic messages were received")
    public void verifyTopicMessages() throws Exception {
        subscriptionResponse.validateReceivedMessages();
    }

    @After("@TopicClientClose")
    public void closeClients() {
        if (sdkClient != null) {
            try {
                sdkClient.close();
            } catch (Exception ex) {
                log.warn("Error closing SDK client : {}", ex.getMessage());
            }
        }

        if (mirrorClient != null) {
            try {
                mirrorClient.close();
            } catch (Exception ex) {
                log.warn("Error closing mirror client : {}", ex.getMessage());
            }
        }
    }
}
