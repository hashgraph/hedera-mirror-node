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

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.hedera.mirror.test.e2e.acceptance.util.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.util.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.util.TopicHelper;

@Log4j2
public class TopicFeature {
    int numMessages;
    int latency;
    MirrorConsensusTopicQuery mirrorConsensusTopicQuery;
    Client sdkClient;
    MirrorNodeClient mirrorClient;
    TopicHelper topicHelper;
    ConsensusTopicId consensusTopicId;
    MirrorSubscriptionHandle subscription;
    List<TransactionReceipt> transactionReceipts;
    Ed25519PrivateKey submitKey;
    List<MirrorConsensusTopicResponse> mirrorConsensusTopicResponses;

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

    @Given("I attempt to create a new topic id")
    public void createNewTopic() throws HederaStatusException {
        if (consensusTopicId == null) {
            topicHelper = new TopicHelper(sdkClient);
            transactionReceipts = new ArrayList();

            submitKey = Ed25519PrivateKey.generate();
            Ed25519PublicKey submitPublicKey = submitKey.publicKey;
            log.debug("Topic creation PrivateKey : {}, PublicKey : {}", submitKey, submitPublicKey);

            TransactionReceipt receipt = topicHelper.createTopic(submitPublicKey);
            consensusTopicId = receipt.getConsensusTopicId();
            mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery()
                    .setTopicId(consensusTopicId)
                    .setStartTime(Instant.EPOCH);

            transactionReceipts.add(receipt);
        }
    }

    @Given("I provide a topic id {long}")
    public void setTopicIdParam(Long topicId) {
        consensusTopicId = new ConsensusTopicId(0, 0, topicId);
        mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery()
                .setTopicId(consensusTopicId);
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

    @When("I attempt to update an existing topic")
    public void updateTopic() throws HederaStatusException {
        TopicHelper topicHelper = new TopicHelper(sdkClient);
        transactionReceipts = new ArrayList();
        transactionReceipts.add(topicHelper.updateTopic(consensusTopicId, submitKey));
    }

    @When("I subscribe to the topic")
    public void verifySubscriptionChannelConnection() {
        subscription = mirrorClient.subscribeToTopic(mirrorConsensusTopicQuery);
        assertNotNull(subscription);
    }

    @When("I publish random messages")
    public void verifyTopicMessagePublish() throws InterruptedException, HederaStatusException {
        TopicHelper topicHelper = new TopicHelper(sdkClient);
        transactionReceipts = topicHelper
                .publishMessagesToTopic(consensusTopicId, "New message", submitKey, numMessages);
        assertEquals(numMessages, transactionReceipts.size());
    }

    @When("I attempt to delete the topic")
    public void deleteTopic() throws HederaStatusException {

        TopicHelper topicHelper = new TopicHelper(sdkClient);
        transactionReceipts = new ArrayList();
        transactionReceipts.add(topicHelper.deleteTopic(consensusTopicId));
    }

    @Then("all setup items were configured")
    public void verifySetup() {
        assertNotNull(sdkClient);
        assertNotNull(mirrorClient);
        assertNotNull(consensusTopicId);
        log.trace("Verified non null setup items");
    }

    @Then("I unsubscribe from a topic")
    public void verifyUnSubscribeFromChannelConnection() {
        mirrorClient.unSubscribeFromTopic(subscription);
    }

    @Then("the network should successfully establish a channel to this topic")
    public void verifySubscribeAndUnsubscribeChannelConnection() {
        verifySubscriptionChannelConnection();

        verifyUnSubscribeFromChannelConnection();
    }

    @Then("I subscribe with a filter to retrieve messages")
    public void retrieveTopicMessages() throws Exception {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(mirrorConsensusTopicQuery, "mirrorConsensusTopicQuery null");
        mirrorConsensusTopicResponses = mirrorClient
                .subscribeToTopicAndRetrieveMessages(mirrorConsensusTopicQuery, numMessages, latency);
    }

    @Then("the network should successfully observe these messages")
    public void verifyTopicMessageSubscription() throws Exception {
        assertNotNull(mirrorConsensusTopicResponses, "mirrorConsensusTopicResponses is null");
    }

    @Then("the network should confirm valid transaction receipts for this operation")
    public void verifyTransactionReceipts() {
        for (TransactionReceipt receipt : transactionReceipts) {
            assertNotNull(receipt);
        }
    }

    @Then("the network should confirm valid topic messages were received")
    public void verifyTopicMessages() throws Exception {
        topicHelper.processReceivedMessages(mirrorConsensusTopicResponses);
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
