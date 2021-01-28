package com.hedera.mirror.grpc.jmeter.client.hts;

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

import com.google.common.base.Stopwatch;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.mirror.grpc.jmeter.handler.PropertiesHandler;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.hts.RESTGetByIdsRequest;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferPublishAndRetrieveRequest;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferPublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.hts.TokenTransfersPublishAndRetrieveSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
public class TokenTransferPublishAndRetrieveClient extends AbstractJavaSamplerClient {
    private PropertiesHandler propHandler;
    private List<SDKClientHandler> clientList;
    private TokenId tokenId;
    private int transactionsPerBatchCount;
    private AccountId operatorId;
    private Ed25519PrivateKey operatorPrivateKey;
    private AccountId recipientId;
    private long transferAmount;
    private long publishTimeout;
    private long publishInterval;
    private long expectedTransactionCount;
    private String restBaseUrl;
    private int restMaxRetry;
    private int restRetryBackoffMs;
    private int statusPrintIntervalMinutes;

    @Override
    public void setupTest(JavaSamplerContext javaSamplerContext) {
        propHandler = new PropertiesHandler(javaSamplerContext);

        // read in properties for publishing a Token Transfer
        tokenId = TokenId.fromString(propHandler.getTestParam("tokenId", "0"));
        operatorId = AccountId.fromString(propHandler.getTestParam("operatorId", "0"));
        operatorPrivateKey = Ed25519PrivateKey.fromString(propHandler.getTestParam("operatorKey", "0"));
        recipientId = AccountId.fromString(propHandler.getTestParam("recipientId", "1"));
        transferAmount = propHandler.getLongTestParam("transferAmount", 1L);

        // read in properties related to batch publishing and printing
        transactionsPerBatchCount = propHandler.getIntTestParam("messagesPerBatchCount", 0);
        publishInterval = propHandler.getIntTestParam("publishInterval", 20000);
        publishTimeout = propHandler.getIntTestParam("publishTimeout", 60);
        statusPrintIntervalMinutes = (propHandler.getIntTestParam("statusPrintIntervalMinutes", 1));

        // read in properties related to retrieving transactions via REST
        restBaseUrl = propHandler.getTestParam("restBaseUrl", "localhost:5551");
        restMaxRetry = propHandler.getIntTestParam("restMaxRetry", 1000);
        restRetryBackoffMs = propHandler.getIntTestParam("restRetryBackoffMs", 50);

        //The expected number of transactions to receive, determines success
        expectedTransactionCount = propHandler.getLongTestParam("expectedTransactionCount", 0L);

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
        AtomicLong transactionTotal = new AtomicLong(0);
        SampleResult result = new SampleResult();
        result.sampleStart();

        // kick off batched transaction publish
        TokenTransferPublishAndRetrieveRequest request = TokenTransferPublishAndRetrieveRequest.builder()
                .tokenTransferPublishRequest(TokenTransferPublishRequest.builder()
                        .transactionsPerBatchCount(transactionsPerBatchCount)
                        .operatorId(operatorId)
                        .recipientId(recipientId)
                        .tokenId(tokenId)
                        .transferAmount(transferAmount)
                        .statusPrintIntervalMinutes(statusPrintIntervalMinutes)
                        .build())
                .restGetByIdsRequest(RESTGetByIdsRequest.builder()
                        .restBaseUrl(restBaseUrl)
                        .restRetryMax(restMaxRetry)
                        .restRetryBackoffMs(restRetryBackoffMs).build())
                .build();

        // publish and retrieve transaction executor service
        ScheduledExecutorService executor = Executors
                .newScheduledThreadPool(clientList.size() * Runtime.getRuntime().availableProcessors());

        // print status executor service
        ScheduledExecutorService loggerScheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            log.info("Schedule client tasks every publishInterval: {} ms", publishInterval);
            Stopwatch totalStopwatch = Stopwatch.createStarted();
            clientList.forEach(x -> {
                executor.scheduleAtFixedRate(
                        () -> {
                            TokenTransfersPublishAndRetrieveSampler tokenTransfersPublishAndRetrieveSampler =
                                    new TokenTransfersPublishAndRetrieveSampler(request, x);
                            transactionTotal.addAndGet(tokenTransfersPublishAndRetrieveSampler
                                    .submitTokenTransferTransactions());
                        },
                        0,
                        publishInterval,
                        TimeUnit.MILLISECONDS);
            });

            log.info("Executor await termination publishTimeout: {} secs", publishTimeout);
            executor.awaitTermination(publishTimeout, TimeUnit.SECONDS);
            printStatus(transactionTotal.get(), totalStopwatch);
            if (transactionTotal.get() >= expectedTransactionCount) {
                log.info("Successfully retrieved at least {} transactions", expectedTransactionCount);
                success = true;
            } else {
                log.info("Failed to retrieve at least {} transactions", expectedTransactionCount);
            }
            result.setResponseMessage(String.valueOf(transactionTotal.get()));
            result.setResponseCodeOK();
        } catch (Exception e) {
            log.error("Error publishing HTS transactions", e);
            result.setResponseMessage("Exception: " + e);
            result.setResponseCode("500");
        } finally {
            result.sampleEnd();
            result.setSuccessful(success);

            //TODO This is killing threads when things are slow and blowing up the logs, investigate graceful handling
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

    private void printStatus(long totalCount, Stopwatch totalStopwatch) {
        double rate = TransactionSubmissionResult
                .getTransactionSubmissionRate(totalCount, totalStopwatch
                        .elapsed(TimeUnit.MILLISECONDS));
        log.info("Published and retrieved {} total transactions in {} s ({}/s)", totalCount, totalStopwatch
                .elapsed(TimeUnit.SECONDS), rate);
    }
}
