package com.hedera.mirror.grpc.jmeter.client.hts;

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
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferRequest;
import com.hedera.mirror.grpc.jmeter.sampler.hts.TokenTransfersPublishAndRetrieveSampler;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
public class TokenTransferPublishAndRetrieveClient extends AbstractJavaSamplerClient {
    private PropertiesHandler propHandler;
    private List<SDKClientHandler> clientList;
    private TokenId tokenId;
    private int messagesPerBatchCount;
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

        // read in nodes list, topic id, number of messages, message size
        tokenId = TokenId.fromString(propHandler.getTestParam("tokenId", "0"));
        messagesPerBatchCount = propHandler.getIntTestParam("messagesPerBatchCount", 0);
        publishInterval = propHandler.getIntTestParam("publishInterval", 20000);
        publishTimeout = propHandler.getIntTestParam("publishTimeout", 60);
        operatorId = AccountId.fromString(propHandler.getTestParam("operatorId", "0"));
        operatorPrivateKey = Ed25519PrivateKey.fromString(propHandler.getTestParam("operatorKey", "0"));
        recipientId = AccountId.fromString(propHandler.getTestParam("recipientId", "1"));
        transferAmount = propHandler.getLongTestParam("transferAmount", 1L);
        expectedTransactionCount = propHandler.getLongTestParam("expectedTransactionCount", 0L);
        restBaseUrl = propHandler.getTestParam("restBaseUrl", "localhost:5551");
        //TODO These two may be a little aggressive, they've worked so far but may need to pull down the default.
        restMaxRetry = propHandler.getIntTestParam("restMaxRetry", 1000);
        restRetryBackoffMs = propHandler.getIntTestParam("restRetryBackoffMs", 50);
        statusPrintIntervalMinutes = (propHandler.getIntTestParam("statusPrintIntervalMinutes", 1));

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

        // kick off batched message publish
        TokenTransferRequest request = TokenTransferRequest.builder()
                .messagesPerBatchCount(messagesPerBatchCount)
                .operatorId(operatorId)
                .recipientId(recipientId)
                .tokenId(tokenId)
                .transferAmount(transferAmount)
                .restBaseUrl(restBaseUrl)
                .statusPrintIntervalMinutes(statusPrintIntervalMinutes)
                .restRetryMax(restMaxRetry)
                .restRetryBackoffMs(restRetryBackoffMs)
                .build();

        // publish message executor service
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
                            TokenTransfersPublishAndRetrieveSampler topicMessagesPublishSampler =
                                    new TokenTransfersPublishAndRetrieveSampler(request, x);
                            transactionTotal.addAndGet(topicMessagesPublishSampler
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
                log.info("Successfully retrieved at least {} messages", expectedTransactionCount);
                success = true;
            } else {
                log.info("Failed to retrieve at least {} messages", expectedTransactionCount);
            }
            result.setResponseMessage(String.valueOf(transactionTotal.get()));
            result.setResponseCodeOK();
        } catch (Exception e) {
            log.error("Error publishing HTS messages", e);
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
