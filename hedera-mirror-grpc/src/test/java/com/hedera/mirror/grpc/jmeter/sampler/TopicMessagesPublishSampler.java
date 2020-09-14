package com.hedera.mirror.grpc.jmeter.sampler;

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
import org.apache.logging.log4j.Level;

import com.hedera.hashgraph.sdk.HederaNetworkException;
import com.hedera.hashgraph.sdk.HederaPrecheckStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.grpc.jmeter.handler.SDKClientHandler;
import com.hedera.mirror.grpc.jmeter.props.TopicMessagePublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
@RequiredArgsConstructor
public class TopicMessagesPublishSampler extends PublishSampler {
    private final TopicMessagePublishRequest topicMessagePublishRequest;
    private final SDKClientHandler sdkClient;
    private final boolean verifyTransactions;
    private Stopwatch publishStopwatch;

    @SneakyThrows
    public int submitConsensusMessageTransactions() {
        TransactionSubmissionResult result = new TransactionSubmissionResult();
        Stopwatch totalStopwatch = Stopwatch.createStarted();
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger unknownFailures = new AtomicInteger();
        Map<Status, Integer> hederaResponseCodeEx = new HashMap<>();

        // publish MessagesPerBatchCount number of messages to the noted topic id
        log.trace("Submit transaction to {}, topicMessagePublisher: {}", sdkClient
                .getNodeInfo(), topicMessagePublishRequest);

        Level messageLogLevel = Level.INFO;
        for (int i = 0; i < topicMessagePublishRequest.getMessagesPerBatchCount(); i++) {

            try {
                publishStopwatch = Stopwatch.createStarted();
                List<TransactionId> transactionIdList = sdkClient.submitTopicMessage(
                        topicMessagePublishRequest.getConsensusTopicId(),
                        topicMessagePublishRequest.getMessage());
                publishLatencyStatistics.addValue(publishStopwatch.elapsed(TimeUnit.MILLISECONDS));
                transactionIdList.forEach((transactionId -> result.onNext(transactionId)));
                messageLogLevel = Level.DEBUG;
            } catch (HederaPrecheckStatusException preEx) {
                hederaResponseCodeEx.compute(preEx.status, (key, val) -> (val == null) ? 1 : val + 1);
            } catch (HederaNetworkException netEx) {
                networkFailures.incrementAndGet();
                log.error("Network Error submitting transaction {} to {}: {}", i, sdkClient.getNodeInfo(),
                        netEx.getMessage());
            } catch (Exception ex) {
                unknownFailures.incrementAndGet();
                log.error("Unexpected exception publishing message {} to {}: {}", i,
                        sdkClient.getNodeInfo().getNodeId(), ex);
            }
        }

        log.log(messageLogLevel, "Submitted {} messages in {} to topic {} on node {}. {} preCheckErrors, {} " +
                        "networkErrors, " +
                        "{} unknown errors", topicMessagePublishRequest.getMessagesPerBatchCount(), totalStopwatch,
                topicMessagePublishRequest.getConsensusTopicId(), sdkClient.getNodeInfo().getNodeId(),
                StringUtils.join(hederaResponseCodeEx), networkFailures.get(), unknownFailures.get());
        printPublishStats("Publish2Consensus stats");

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
        double min = publishToConsensusLatencyStats.getMin();
        double max = publishToConsensusLatencyStats.getMax();
        double mean = publishToConsensusLatencyStats.getMean();
        double median = publishToConsensusLatencyStats.getPercentile(50);
        double seventyFifthPercentile = publishToConsensusLatencyStats.getPercentile(75);
        double ninetyFifthPercentile = publishToConsensusLatencyStats.getPercentile(95);

        log.trace("Publish2Consensus stats for {} messages, min: {} ms, max: {} ms, avg: {} ms, median: {} ms, 75th " +
                        "percentile: {} " +
                        "ms, 95th percentile: {} ms", topicMessagePublishRequest.getMessagesPerBatchCount(),
                String.format("%.03f", min), String.format("%.03f", max), String.format("%.03f", mean),
                String.format("%.03f", median), String.format("%.03f", seventyFifthPercentile),
                String.format("%.03f", ninetyFifthPercentile));
    }
}
