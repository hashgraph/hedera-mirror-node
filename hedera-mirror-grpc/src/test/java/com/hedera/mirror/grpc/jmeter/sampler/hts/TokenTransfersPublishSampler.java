package com.hedera.mirror.grpc.jmeter.sampler.hts;

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
import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferRequest;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
@RequiredArgsConstructor
public class TokenTransfersPublishSampler {
    private final TokenTransferRequest tokenTransferRequest;
    private final SDKClientHandler sdkClient;
    private final boolean verifyTransactions;
    private final DescriptiveStatistics publishTokenTransferLatencyStats = new DescriptiveStatistics();
    private Stopwatch publishStopwatch;

    @SneakyThrows
    public List<TransactionId> submitTokenTransferTransactions() {
        TransactionSubmissionResult result = new TransactionSubmissionResult();
        Stopwatch totalStopwatch = Stopwatch.createStarted();
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger unknownFailures = new AtomicInteger();
        Map<Status, Integer> hederaResponseCodeEx = new HashMap<>();

        // publish MessagesPerBatchCount number of messages to the noted topic id
        log.trace("Submit transaction to {}, tokenTransferPublisher: {}", sdkClient
                .getNodeInfo(), tokenTransferRequest);

        for (int i = 0; i < tokenTransferRequest.getMessagesPerBatchCount(); i++) {

            try {
                publishStopwatch = Stopwatch.createStarted();
                TransactionId transactionId = sdkClient
                        .submitTokenTransfer(tokenTransferRequest.getTokenId(), tokenTransferRequest
                                .getOperatorId(), tokenTransferRequest
                                .getRecipientId(), tokenTransferRequest.getTransferAmount());
                publishTokenTransferLatencyStats.addValue(publishStopwatch.elapsed(TimeUnit.MILLISECONDS));
                result.onNext(transactionId);
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
                        "networkErrors, {} unknown errors", tokenTransferRequest
                        .getMessagesPerBatchCount(), tokenTransferRequest.getTokenId(),
                tokenTransferRequest
                        .getOperatorId(), tokenTransferRequest.getRecipientId(), totalStopwatch,
                sdkClient.getNodeInfo().getNodeId(),
                StringUtils.join(hederaResponseCodeEx), networkFailures.get(), unknownFailures.get());
        printPublishStats();
        result.onComplete();

        // verify transactions
        if (verifyTransactions) {
            return sdkClient.getValidTransactions(result.getTransactionIdList());
        }

        return result.getTransactionIdList();
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

