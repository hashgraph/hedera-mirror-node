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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.TopicMessagePublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.TopicMessagesPublishSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
public class TopicMessagePublishClient extends AbstractJavaSamplerClient {

    private PropertiesHandler propHandler;
    private List<SDKClientHandler> clientList;
    private Long topicNum;
    private int messagesPerBatchCount;
    private int messageByteSize;
    private AccountId operatorId;
    private Ed25519PrivateKey operatorPrivateKey;
    private long publishTimeout;
    private long publishInterval;
    private boolean verifyTransactions;
    private long printStatusInterval;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        propHandler = new PropertiesHandler(javaSamplerContext);

        // read in nodes list, topic id, number of messages, message size
        topicNum = propHandler.getLongTestParam("topicId", 0L);
        messagesPerBatchCount = propHandler.getIntTestParam("messagesPerBatchCount", 0);
        publishInterval = propHandler.getIntTestParam("publishInterval", 20000);
        publishTimeout = propHandler.getIntTestParam("publishTimeout", 60);
        messageByteSize = propHandler.getIntTestParam("messagesByteSize", 16);
        verifyTransactions = Boolean.valueOf(propHandler.getTestParam("verifyTransactions", "true"));
        printStatusInterval = propHandler.getLongTestParam("statusPrintIntervalMinutes", 1L);
        operatorId = AccountId.fromString(propHandler.getTestParam("operatorId", "0"));
        operatorPrivateKey = Ed25519PrivateKey.fromString(propHandler.getTestParam("operatorKey", "0"));

        // node info expected in comma separated list of <node_IP>:<node_accountId>:<node_port>
        String[] nodeList = propHandler.getTestParam("networkNodes", "localhost:0.0.3:50211").split(",");
        clientList = Arrays.asList(nodeList).stream()
                .map(x -> new SDKClientHandler(x, operatorId, operatorPrivateKey))
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
        TopicMessagePublishRequest topicMessagePublishRequest = TopicMessagePublishRequest.builder()
                .consensusTopicId(topicNum == 0L ? null : new ConsensusTopicId(0, 0, topicNum))
                .messageByteSize(messageByteSize)
                .publishInterval(publishInterval)
                .publishTimeout(publishTimeout)
                .messagesPerBatchCount(messagesPerBatchCount)
                .operatorId(operatorId)
                .operatorPrivateKey(operatorPrivateKey)
                .build();

        // publish message executor service
        ScheduledExecutorService executor = Executors
                .newScheduledThreadPool(clientList.size() * Runtime.getRuntime().availableProcessors());
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executor;

        // print status executor service
        ScheduledExecutorService loggerScheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            log.info("Schedule client tasks every publishInterval: {} ms", publishInterval);
            AtomicInteger intervalCount = new AtomicInteger(0);
            AtomicInteger totalCount = new AtomicInteger(0);
            Stopwatch intervalStopwatch = Stopwatch.createStarted();
            Stopwatch totalStopwatch = Stopwatch.createStarted();
            clientList.forEach(x -> {
                executor.scheduleAtFixedRate(
                        () -> {
                            TopicMessagesPublishSampler topicMessagesPublishSampler =
                                    new TopicMessagesPublishSampler(topicMessagePublishRequest, x, verifyTransactions);
                            intervalCount.addAndGet(topicMessagesPublishSampler
                                    .submitConsensusMessageTransactions());
                        },
                        0,
                        publishInterval,
                        TimeUnit.MILLISECONDS);
            });

            // log progress every minute
            AtomicInteger batchCount = new AtomicInteger(-1);
            loggerScheduler.scheduleAtFixedRate(() -> {
                // update total counter
                totalCount.addAndGet(intervalCount.get());

                log.info("Status after {} m. ThreadPoolExecutor: {}", batchCount.addAndGet(1) * printStatusInterval,
                        scheduledThreadPoolExecutor);
                printStatus(intervalCount.get(), intervalStopwatch);

                // reset interval count and stopwatch
                intervalCount.set(0);
                intervalStopwatch.reset();
                intervalStopwatch.start();
            }, 0, printStatusInterval, TimeUnit.MINUTES);

            log.info("Executor await termination publishTimeout: {} secs", publishTimeout);
            executor.awaitTermination(publishTimeout, TimeUnit.SECONDS);
            printStatus(totalCount.get(), totalStopwatch);
            success = true;
            result.setResponseMessage(String.valueOf(totalCount.get()));
            result.setResponseCodeOK();
        } catch (Exception e) {
            log.error("Error publishing HCS messages", e);
            result.setResponseMessage("Exception: " + e);
            result.setResponseCode("500");
        } finally {
            result.sampleEnd();
            result.setResponseData(topicMessagePublishRequest.toString().getBytes());
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
        long millis = totalStopwatch.elapsed(TimeUnit.MILLISECONDS);
        double rate = TransactionSubmissionResult
                .getTransactionSubmissionRate(totalCount, millis);

        log.info("Published {} transactions in {} s ({}/s)", totalCount, String.format("%.03f", millis / 1000.0),
                String.format("%.01f", rate));
    }
}
