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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaNetworkException;
import com.hedera.hashgraph.sdk.HederaPrecheckStatusException;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.mirror.grpc.jmeter.client.TopicMessagePublishClient;
import com.hedera.mirror.grpc.jmeter.props.TopicMessagePublishRequest;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
@RequiredArgsConstructor
public class TopicMessagesPublishSampler {
    private final TopicMessagePublishRequest topicMessagePublishRequest;
    private final TopicMessagePublishClient.SDKClient sdkClient;
    private final boolean verifyTransactions;
    private final DescriptiveStatistics publishToConsensusLatencyStats = new DescriptiveStatistics();
    private Stopwatch publishStopwatch;

    @SneakyThrows
    public int submitConsensusMessageTransactions() {
        Transaction transaction;
        TransactionSubmissionResult result = new TransactionSubmissionResult();
        Stopwatch totalStopwatch = Stopwatch.createStarted();
        AtomicInteger preCheckFailures = new AtomicInteger();
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger unknownFailures = new AtomicInteger();

        // publish MessagesPerBatchCount number of messages to the noted topic id
        Client client = sdkClient.getClient();
        log.trace("Submit transaction to {}, topicMessagePublisher: {}", sdkClient
                .getNodeInfo(), topicMessagePublishRequest);

        for (int i = 0; i < topicMessagePublishRequest.getMessagesPerBatchCount(); i++) {

            transaction = new ConsensusMessageSubmitTransaction()
                    .setTopicId(topicMessagePublishRequest.getConsensusTopicId())
                    .setMessage(topicMessagePublishRequest.getMessage())
                    .build(client);

            try {
                publishStopwatch = Stopwatch.createStarted();
                TransactionId transactionId = transaction.execute(client, Duration.ofSeconds(2));
                publishToConsensusLatencyStats.addValue(publishStopwatch.elapsed(TimeUnit.MILLISECONDS));
                result.onNext(transactionId);
            } catch (HederaPrecheckStatusException preEx) {
                preCheckFailures.incrementAndGet();
            } catch (HederaNetworkException preEx) {
                networkFailures.incrementAndGet();
            } catch (Exception ex) {
                unknownFailures.incrementAndGet();
                log.error("Unexpected exception publishing message {} to {}: {}", i,
                        sdkClient.getNodeInfo().getNodeId(), ex);
            }
        }

        log.info("Submitted {} messages in {} to topic {} on node {}. {} preCheckErrors, {} networkErrors, " +
                        "{} unknown errors", topicMessagePublishRequest.getMessagesPerBatchCount(), totalStopwatch,
                topicMessagePublishRequest.getConsensusTopicId(), sdkClient.getNodeInfo().getNodeId(),
                preCheckFailures.get(), networkFailures.get(), unknownFailures.get());
        printPublishStats();

        int transactionCount = result.getCounter().get();
        result.onComplete();

        // verify transactions
        if (verifyTransactions) {
            transactionCount = getValidTransactionsCount(result.getTransactionIdList(), client);
        }

        return transactionCount;
    }

    private int getValidTransactionsCount(List<TransactionId> transactionIds, Client client) {
        log.debug("Verify Transactions {}", transactionIds.size());
        AtomicInteger counter = new AtomicInteger(0);
        transactionIds.forEach(x -> {
            TransactionReceipt receipt = null;
            try {
                receipt = x.getReceipt(client);
            } catch (HederaStatusException e) {
                log.debug("Error pulling {} receipt {}", x, e.getMessage());
            }
            if (receipt.status == Status.Success) {
                counter.incrementAndGet();
            } else {
                log.warn("Transaction {} had an unexpected status of {}", x, receipt.status);
            }
        });

        log.debug("{} out of {} transactions returned a Success status", counter.get(), transactionIds.size());
        return counter.get();
    }

    private void printPublishStats() {
        // Compute some statistics
        double min = publishToConsensusLatencyStats.getMin();
        double max = publishToConsensusLatencyStats.getMax();
        double mean = publishToConsensusLatencyStats.getMean();
        double median = publishToConsensusLatencyStats.getPercentile(50);
        double seventyFifthPercentile = publishToConsensusLatencyStats.getPercentile(75);
        double ninetyFifthPercentile = publishToConsensusLatencyStats.getPercentile(95);

        log.info("Publish2Consensus stats, min: {}s, max: {}s, avg: {}s, median: {}s, 75th percentile: {}s, " +
                        "95th percentile: {}s", String.format("%.03f", min), String.format("%.03f", max),
                String.format("%.03f", mean), String.format("%.03f", median),
                String.format("%.03f", seventyFifthPercentile), String.format("%.03f", ninetyFifthPercentile));
    }
}
