package com.hedera.mirror.grpc.jmeter.client;

/*-
* ‌
* Hedera Mirror Node
* ​
* Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.props.TopicMessagePublisher;
import com.hedera.mirror.grpc.jmeter.sampler.TopicMessagesPublishSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
public class TopicMessagePublishClient extends AbstractJavaSamplerClient {

    private PropertiesHandler propHandler;
    private List<SDKClient> clientList;
    private Long topicNum;
    private int messagesPerBatchCount;
    private int messageByteSize;
    private AccountId operatorId;
    private Ed25519PrivateKey operatorPrivateKey;
    private long publishTimeout;
    private long publishInterval;
    private boolean verifyTransactions;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        propHandler = new PropertiesHandler(javaSamplerContext);

        // read in nodes list, topic id, number of messages, message size
        topicNum = propHandler.getLongClientTestParam("TopicId", 0);
        messagesPerBatchCount = propHandler.getIntClientTestParam("MessagesPerBatchCount", 0);
        publishInterval = propHandler.getIntClientTestParam("PublishInterval", 0);
        publishTimeout = propHandler.getIntClientTestParam("PublishTimeout", 0);
        messageByteSize = propHandler.getIntClientTestParam("MessagesByteSize", 0);
        operatorId = AccountId.fromString(propHandler.getClientTestParam("OperatorId", 0));
        operatorPrivateKey = Ed25519PrivateKey.fromString(propHandler.getClientTestParam("OperatorKey", 0));
        verifyTransactions = Boolean.valueOf(propHandler.getClientTestParam("VerifyTransactions", 0, "true"));

        // node info expected in comma separated list of <node_IP>:<node_accountId>:<node_IP>
        String[] nodeList = propHandler.getClientTestParam("NetworkNodes", 0).split(",");
        clientList = Arrays.asList(nodeList).stream()
                .map(x -> new SDKClient(x, operatorId, operatorPrivateKey))
                .collect(Collectors.toList());
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument("propertiesBase", "hedera.mirror.test.performance");
        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        boolean success = false;
        SampleResult result = new SampleResult();
        result.sampleStart();

        // kick off batched message publish
        TopicMessagePublisher topicMessagePublisher = TopicMessagePublisher.builder()
                .consensusTopicId(new ConsensusTopicId(0, 0, topicNum))
                .messageByteSize(messageByteSize)
                .publishInterval(publishInterval)
                .publishTimeout(publishTimeout)
                .messagesPerBatchCount(messagesPerBatchCount)
                .operatorId(operatorId)
                .operatorPrivateKey(operatorPrivateKey)
                .build();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(clientList.size() * 4);
        ScheduledExecutorService loggerScheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            log.info("Schedule client tasks every publishInterval: {} ms", publishInterval);
            AtomicInteger counter = new AtomicInteger(0);
            Stopwatch totalStopwatch = Stopwatch.createStarted();
            clientList.forEach(x -> {
                executor.scheduleAtFixedRate(
                        () -> {
                            TopicMessagesPublishSampler topicMessagesPublishSampler =
                                    new TopicMessagesPublishSampler(topicMessagePublisher, x, verifyTransactions);
                            counter.addAndGet(topicMessagesPublishSampler.submitConsensusMessageTransactions());
                        },
                        0,
                        publishInterval,
                        TimeUnit.MILLISECONDS);
            });

            loggerScheduler.scheduleAtFixedRate(() -> {
                printStatus(counter.get(), totalStopwatch);
            }, 0, 1, TimeUnit.MINUTES);

            log.info("Executor await termination publishTimeout: {} secs", publishTimeout);
            executor.awaitTermination(publishTimeout, TimeUnit.SECONDS);
            printStatus(counter.get(), totalStopwatch);
            success = true;
            result.setResponseMessage(counter.get());
            result.setResponseCodeOK();
        } catch (Exception e) {
            e.printStackTrace();
            result.setResponseMessage("Exception: " + e);
            result.setResponseCode("500");
        } finally {
            result.sampleEnd();
            result.setResponseData(topicMessagePublisher.toString().getBytes());
            result.setSuccessful(success);

            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }

            loggerScheduler.shutdownNow();

            // close clients
            clientList.forEach(x -> {
                try {
                    x.close();
                } catch (InterruptedException e) {
                    log.debug("Error closing client: {}", e.getMessage());
                }
            });
        }

        return result;
    }

    private void printStatus(int totalCount, Stopwatch totalStopwatch) {
        double rate = TransactionSubmissionResult
                .getTransactionSubmissionRate(totalCount, totalStopwatch
                        .elapsed(TimeUnit.MILLISECONDS));
        log.info("Published {} total transactions in {} s ({}/s)", totalCount, totalStopwatch
                .elapsed(TimeUnit.SECONDS), rate);
    }

    @Value
    public class NodeInfo {
        private final AccountId nodeId;
        private final String nodeHost;
        private final String nodePort;

        public NodeInfo(String nodeInfo) {
            String[] nodeParts = nodeInfo.split(":");
            nodeHost = nodeParts[0];
            nodeId = AccountId.fromString(nodeParts[1]);
            nodePort = nodeParts[2];
        }

        public String getNodeAddress() {
            return nodeHost + ":" + nodePort;
        }
    }

    @Value
    public class SDKClient {
        private final NodeInfo nodeInfo;
        private final AccountId operatorId;
        private final Ed25519PrivateKey operatorPrivateKey;
        private final Client client;

        public SDKClient(String nodeParts, AccountId operatorId, Ed25519PrivateKey operatorPrivateKey) {
            nodeInfo = new NodeInfo(nodeParts);
            this.operatorId = operatorId;
            this.operatorPrivateKey = operatorPrivateKey;

            client = new Client(Map.of(nodeInfo.nodeId, nodeInfo.getNodeAddress()));
            client.setOperator(operatorId, operatorPrivateKey);

            log.trace("Created client for {}", nodeInfo);
        }

        public void close() throws InterruptedException {
            log.debug("Closing SDK client, waits up to 10 s for valid close");

            try {
                if (client != null) {
                    client.close(5, TimeUnit.SECONDS);
                }
            } catch (TimeoutException tex) {
                log.debug("Exception on client close: {}", tex.getMessage());
            }
        }
    }
}