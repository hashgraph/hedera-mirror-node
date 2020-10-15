package com.hedera.mirror.grpc.jmeter.sampler;

import com.google.common.base.Stopwatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.hedera.hashgraph.sdk.HederaNetworkException;
import com.hedera.hashgraph.sdk.HederaPrecheckStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.TokenTransferPublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
@RequiredArgsConstructor
public class TokenTransfersPublishSampler {
    private final TokenTransferPublishRequest tokenTransferPublishRequest;
    private final SDKClientHandler sdkClient;
    private final boolean verifyTransactions;
    private final DescriptiveStatistics publishTokenTransferLatencyStats = new DescriptiveStatistics();
    private Stopwatch publishStopwatch;

    @SneakyThrows
    public int submitTokenTransferTransactions() {
        TransactionSubmissionResult result = new TransactionSubmissionResult();
        Stopwatch totalStopwatch = Stopwatch.createStarted();
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger unknownFailures = new AtomicInteger();
        Map<Status, Integer> hederaResponseCodeEx = new HashMap<>();

        // publish MessagesPerBatchCount number of messages to the noted topic id
        log.trace("Submit transaction to {}, tokenTransferPublisher: {}", sdkClient
                .getNodeInfo(), tokenTransferPublishRequest);

        for (int i = 0; i < tokenTransferPublishRequest.getMessagesPerBatchCount(); i++) {

            try {
                publishStopwatch = Stopwatch.createStarted();
                List<TransactionId> transactionIdList = sdkClient.submitTokenTransfer(
                        tokenTransferPublishRequest.getTokenId(),
                        tokenTransferPublishRequest.getOperatorId(),
                        tokenTransferPublishRequest.getRecipientId(),
                        tokenTransferPublishRequest.getTokenAmount());
                publishTokenTransferLatencyStats.addValue(publishStopwatch.elapsed(TimeUnit.MILLISECONDS));
                transactionIdList.forEach((transactionId -> result.onNext(transactionId)));
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

        log.info("Submitted {} token transfers for token {} from {} to {} in {} on node {}. {} preCheckErrors, {} " +
                        "networkErrors, {} unknown errors", tokenTransferPublishRequest
                        .getMessagesPerBatchCount(), tokenTransferPublishRequest.getTokenId(),
                tokenTransferPublishRequest
                        .getOperatorId(), tokenTransferPublishRequest.getRecipientId(), totalStopwatch,
                tokenTransferPublishRequest.getTokenId(), sdkClient.getNodeInfo().getNodeId(),
                StringUtils.join(hederaResponseCodeEx), networkFailures.get(), unknownFailures.get());
        printPublishStats();

        int transactionCount = result.getCounter().get();
        result.onComplete();

        // verify transactions
        if (verifyTransactions) {
            transactionCount = sdkClient.getValidTransactionsCount(result.getTransactionIdList());
        }

        return transactionCount;
    }

    private void printPublishStats() {
        // Compute some statistics
        double min = publishTokenTransferLatencyStats.getMin();
        double max = publishTokenTransferLatencyStats.getMax();
        double mean = publishTokenTransferLatencyStats.getMean();
        double median = publishTokenTransferLatencyStats.getPercentile(50);
        double seventyFifthPercentile = publishTokenTransferLatencyStats.getPercentile(75);
        double ninetyFifthPercentile = publishTokenTransferLatencyStats.getPercentile(95);

        log.info("TokenTransfer stats, min: {} ms, max: {} ms, avg: {} ms, median: {} ms, 75th percentile: {} ms," +
                        " 95th percentile: {} ms", String.format("%.03f", min), String.format("%.03f", max),
                String.format("%.03f", mean), String.format("%.03f", median),
                String.format("%.03f", seventyFifthPercentile), String.format("%.03f", ninetyFifthPercentile));
    }
}

