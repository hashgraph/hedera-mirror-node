package com.hedera.mirror.grpc.jmeter.sampler.hts;

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONObject;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import com.hedera.hashgraph.sdk.HederaNetworkException;
import com.hedera.hashgraph.sdk.HederaPrecheckStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferRequest;
import com.hedera.mirror.grpc.jmeter.sampler.result.hts.TokenTransferPublishAndRetrieveResult;

@Log4j2
public class TokenTransfersPublishAndRetrieveSampler {
    private final TokenTransferRequest request;
    private final SDKClientHandler sdkClient;
    private final DescriptiveStatistics publishTokenTransferLatencyStats = new DescriptiveStatistics();
    private Stopwatch publishStopwatch;
    private final WebClient webClient;
    private static final String REST_PATH = "/api/v1/transactions/";

    public TokenTransfersPublishAndRetrieveSampler(TokenTransferRequest request,
                                                   SDKClientHandler sdkClient) {
        this.request = request;
        this.sdkClient = sdkClient;
        this.webClient = WebClient.create(request.getRestBaseUrl());
    }

    @SneakyThrows
    public long submitTokenTransferTransactions() {
        TokenTransferPublishAndRetrieveResult result = new TokenTransferPublishAndRetrieveResult(sdkClient.getNodeInfo()
                .getNodeId());
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger unknownFailures = new AtomicInteger();
        Map<Status, Integer> hederaResponseCodeEx = new HashMap<>();

        // publish MessagesPerBatchCount number of messages to the noted topic id
        log.trace("Submit transaction to {}, tokenTransferPublisher: {}", sdkClient
                .getNodeInfo(), request);

        for (int i = 0; i < request.getMessagesPerBatchCount(); i++) {

            try {
                publishStopwatch = Stopwatch.createStarted();
                TransactionId transactionId = sdkClient
                        .submitTokenTransfer(request.getTokenId(), request.getOperatorId(), request
                                .getRecipientId(), request.getTransferAmount());
                publishTokenTransferLatencyStats.addValue(publishStopwatch.elapsed(TimeUnit.MILLISECONDS));
                String retrievedTransaction = getTransaction(convertTransactionId(transactionId.toString()));
                Instant received = Instant.now();
                //TODO Having trouble wrangling the result object into a POJO, this is a workaround.
                JSONObject obj = new JSONObject(retrievedTransaction).getJSONArray("transactions")
                        .getJSONObject(0);
                //TODO Make sure the valid start time is equivalent of publish time for metrics
                result.onNext(obj.getString("consensus_timestamp"),
                        obj.getString("valid_start_timestamp"), received);
            } catch (HederaPrecheckStatusException preEx) {
                hederaResponseCodeEx.compute(preEx.status, (key, val) -> (val == null) ? 1 : val + 1);
            } catch (HederaNetworkException preEx) {
                networkFailures.incrementAndGet();
            } catch (Exception ex) {
                unknownFailures.incrementAndGet();
                log.error("Unexpected exception publishing message {} to {}: {}", i,
                        sdkClient.getNodeInfo().getNodeId(), ex);
            }
        }
        printPublishStats();
        result.onComplete();
        return result.getTransactionCount();
    }

    private void printPublishStats() {
        // Compute some statistics
        double min = publishTokenTransferLatencyStats.getMin();
        double max = publishTokenTransferLatencyStats.getMax();
        double mean = publishTokenTransferLatencyStats.getMean();
        double median = publishTokenTransferLatencyStats.getPercentile(50);
        double seventyFifthPercentile = publishTokenTransferLatencyStats.getPercentile(75);
        double ninetyFifthPercentile = publishTokenTransferLatencyStats.getPercentile(95);

        log.info("Token Transfer publish node {}: stats, min: {} ms, max: {} ms, avg: {} ms, median: {} ms, 75th" +
                        " percentile: {} ms," +
                        " 95th percentile: {} ms", sdkClient.getNodeInfo().getNodeId(), String.format("%.03f", min),
                String.format("%.03f", max), String.format("%.03f", mean), String.format("%.03f", median),
                String.format("%.03f", seventyFifthPercentile), String.format("%.03f", ninetyFifthPercentile));
    }

    //TODO Is there a better way of doing this?
    private String convertTransactionId(String transactionId) {
        int indexOfBadPeriod = transactionId.lastIndexOf(".");
        String realTransaction = new StringBuilder().append(transactionId.replaceFirst("@", "-")
                .substring(0, indexOfBadPeriod)).append("-")
                .append(transactionId.substring(indexOfBadPeriod + 1)).toString();
        return realTransaction;
    }

    private String getTransaction(String transactionId) {
        return webClient.get().uri(REST_PATH + transactionId).retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry
                        .fixedDelay(request.getRestRetryMax(), Duration.ofMillis(request.getRestRetryBackoffMs())))
                .block();
    }
}

