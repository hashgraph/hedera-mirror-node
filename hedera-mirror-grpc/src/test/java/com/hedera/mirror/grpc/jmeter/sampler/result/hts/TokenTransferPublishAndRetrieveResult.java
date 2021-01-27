package com.hedera.mirror.grpc.jmeter.sampler.result.hts;

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
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import com.hedera.hashgraph.sdk.account.AccountId;

@Data
@Log4j2
@RequiredArgsConstructor
public class TokenTransferPublishAndRetrieveResult {
    private final Stopwatch totalStopwatch = Stopwatch.createStarted();
    private Stopwatch lastMessage = Stopwatch.createUnstarted();
    private long transactionCount = 0L;
    private boolean success = true;
    private final SummaryStatistics e2eLatencyTotalStats = new SummaryStatistics();
    private final SummaryStatistics publishToConsensusLatencyTotalStats = new SummaryStatistics();
    private final SummaryStatistics consensusToDeliveryLatencyTotalStats = new SummaryStatistics();
    private final AccountId nodeId;

    public Instant getConsensusInstant(String consensus) {
        String[] instantSegment = consensus.split(Pattern.quote("."));
        return Instant.ofEpochSecond(Long.parseLong(instantSegment[0]), Long.parseLong(instantSegment[1]));
    }

    public void onNext(String consensus, String start, Instant receivedInstant) {
        lastMessage = Stopwatch.createStarted();
        ++transactionCount;
        calculateLatencies(consensus, start, receivedInstant);
    }

    public void onComplete() {
        printTotalStats();
    }

    public Instant getMessagePublishInstant(String start) {
        String[] instantSegment = start.split(Pattern.quote("."));
        return Instant.ofEpochSecond(Long.parseLong(instantSegment[0]), Long.parseLong(instantSegment[1]));
    }

    private void calculateLatencies(String consensus, String start, Instant received) {
        Instant currentConsensusInstant = getConsensusInstant(consensus);
        Instant publishInstant = getMessagePublishInstant(start);
        long publishMillis = publishInstant == null ? 0 : publishInstant.toEpochMilli();
        long consensusMillis = currentConsensusInstant.toEpochMilli();
        long receiptMillis = received.toEpochMilli();
        double e2eSeconds = publishMillis == 0 ? 0 : (receiptMillis - publishMillis) / 1000.0;
        double publishToConsensus = publishMillis == 0 ? 0 : (consensusMillis - publishMillis) / 1000.0;
        double consensusToDelivery = (receiptMillis - consensusMillis) / 1000.0;
        log.trace("Node {}: Observed message, e2eSeconds: {}s, publishToConsensus: {}s, consensusToDelivery: {}s, " +
                        "publish timestamp: {}, consensus timestamp: {}, receipt time: {}",
                nodeId, String.format("%.03f", e2eSeconds), String.format("%.03f", publishToConsensus), String
                        .format("%.03f", consensusToDelivery), publishInstant, currentConsensusInstant,
                received);

        // update interval and total stat buckets
        updateE2ELatencyStats(e2eLatencyTotalStats, e2eSeconds);
        updateE2ELatencyStats(publishToConsensusLatencyTotalStats, publishToConsensus);
        updateE2ELatencyStats(consensusToDeliveryLatencyTotalStats,
                consensusToDelivery);
    }

    private void updateE2ELatencyStats(SummaryStatistics total, double latency) {
        if (latency > 0) {
            // update total stats
            total.addValue(latency);
        }
    }

    private void printTotalStats() {
        String totalRate = String.format("%.02f", getMessageRate(getTransactionCount(), totalStopwatch,
                lastMessage));
        log.info("Node {}: Observed {} total messages in {} ({}/s). Last message received {} ago.",
                nodeId, transactionCount, totalStopwatch, totalRate, lastMessage);

        printIndividualStat(e2eLatencyTotalStats, "Total E2E Latency");
        printIndividualStat(publishToConsensusLatencyTotalStats, "Total PublishToConsensus Latency");
        printIndividualStat(consensusToDeliveryLatencyTotalStats, "Total ConsensusToDelivery Latency");
    }

    private void printIndividualStat(SummaryStatistics stats, String name) {
        if (stats != null) {
            // Compute some statistics
            double min = stats.getMin();
            double max = stats.getMax();
            double mean = stats.getMean();

            log.info("Node {}: {} stats, min: {}s, max: {}s, avg: {}s",
                    nodeId, name, String.format("%.03f", min), String.format("%.03f", max), String
                            .format("%.03f", mean));
        }
    }

    public double getMessageRate(long messageCount, Stopwatch durationStopwatch, Stopwatch lastResponseStopwatch) {
        if (messageCount == 0 || durationStopwatch == null) {
            return 0;
        }

        long milliSeconds = durationStopwatch.elapsed(TimeUnit.MILLISECONDS) -
                (lastResponseStopwatch == null ? 0 : lastResponseStopwatch.elapsed(TimeUnit.MILLISECONDS));

        return milliSeconds > 0 ? (messageCount * 1.0 / milliSeconds) * 1000.0 : 0;
    }
}
