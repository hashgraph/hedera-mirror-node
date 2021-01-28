package com.hedera.mirror.grpc.jmeter.sampler.result;

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
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

@SuperBuilder
@Data
@Log4j2
public abstract class HCSSamplerResult<T> {
    private final long realmNum;
    private final long topicNum;
    private final Stopwatch totalStopwatch = Stopwatch.createStarted();
    private final Stopwatch historicalStopwatch = Stopwatch.createUnstarted();
    private final Stopwatch incomingStopwatch = Stopwatch.createUnstarted();
    private final Instant subscribeStart = Instant.now();
    private Throwable subscribeError;
    private Stopwatch lastMessage = Stopwatch.createUnstarted();
    private Stopwatch lastHistoricalMessage;
    private Stopwatch lastIncomingMessage;
    private long historicalMessageCount = 0L;
    private long incomingMessageCount = 0L;
    private T last;
    private boolean success = true;
    private boolean historical = true;
    private boolean calculateLatencies = true;
    private SummaryStatistics e2eLatencyStats = new SummaryStatistics();
    private SummaryStatistics publishToConsensusLatencyStats = new SummaryStatistics();
    private SummaryStatistics consensusToDeliveryLatencyStats = new SummaryStatistics();
    private final SummaryStatistics e2eLatencyTotalStats = new SummaryStatistics();
    private final SummaryStatistics publishToConsensusLatencyTotalStats = new SummaryStatistics();
    private final SummaryStatistics consensusToDeliveryLatencyTotalStats = new SummaryStatistics();

    abstract Instant getConsensusInstant(T t);

    abstract long getSequenceNumber(T t);

    abstract String getMessage(T t);

    abstract byte[] getMessageByteArray(T response);

    public long getTotalMessageCount() {
        return historicalMessageCount + incomingMessageCount;
    }

    public double getMessageRate(long messageCount, Stopwatch durationStopwatch, Stopwatch lastResponseStopwatch) {
        if (messageCount == 0 || durationStopwatch == null) {
            return 0;
        }

        long milliSeconds = durationStopwatch.elapsed(TimeUnit.MILLISECONDS) -
                (lastResponseStopwatch == null ? 0 : lastResponseStopwatch.elapsed(TimeUnit.MILLISECONDS));

        return milliSeconds > 0 ? (messageCount * 1.0 / milliSeconds) * 1000.0 : 0;
    }

    public void onNext(T result, Instant receivedInstant) {
        lastMessage = Stopwatch.createStarted();
        historical = getConsensusInstant(result).isBefore(subscribeStart);
        if (historical) {
            if (!historicalStopwatch.isRunning()) {
                historicalStopwatch.start();
            }

            ++historicalMessageCount;
            lastHistoricalMessage = Stopwatch.createStarted();
        } else {
            if (!incomingStopwatch.isRunning()) {
                incomingStopwatch.start();
            }

            if (historicalStopwatch.isRunning()) {
                historicalStopwatch.stop();
                lastHistoricalMessage.stop();
            }

            ++incomingMessageCount;
            lastIncomingMessage = Stopwatch.createStarted();
        }

        validateResponse(result);
        calculateLatencies(result, receivedInstant);

        last = result;
    }

    public void onComplete() {
        printTotalStats();
    }

    public void onError(Throwable err) {
        if (err.getMessage().contains("CANCELLED: unsubscribed")) {
            subscribeError = err;
        }
    }

    public void validateResponse(T currentResponse) {

        Instant currentConsensusInstant = getConsensusInstant(currentResponse);
        long currentSequenceNumber = getSequenceNumber(currentResponse);
        log.trace("Observed message: {}, consensus timestamp: {}, topic sequence number: {}",
                getMessage(currentResponse), currentConsensusInstant, currentSequenceNumber);
        if (last != null) {
            Instant lastConsensusInstant = getConsensusInstant(last);
            long lastSequenceNumber = getSequenceNumber(last);

            if (currentSequenceNumber != lastSequenceNumber + 1) {
                log.error("Out of order message sequence. Last : {}, current : {}", last, currentResponse);
                throw new IllegalArgumentException("Out of order message sequence. Expected " + (lastSequenceNumber + 1) + " got " + currentSequenceNumber);
            }

            if (!currentConsensusInstant.isAfter(lastConsensusInstant)) {
                log.error("Out of order message sequence. Last : {}, current : {}", last, currentResponse);
                throw new IllegalArgumentException("Out of order message timestamp. Expected " + currentConsensusInstant +
                        " to be after " + lastConsensusInstant);
            }
        }
    }

    public void printProgress() {
        String historicRate = String
                .format("%.02f", getMessageRate(getHistoricalMessageCount(), historicalStopwatch,
                        lastHistoricalMessage));
        String incomingRate = String
                .format("%.02f", getMessageRate(getIncomingMessageCount(), incomingStopwatch, lastIncomingMessage));

        log.info("Observed {} Historic messages in {} ({}/s), {} Incoming messages in {} ({}/s)",
                historicalMessageCount, historicalStopwatch, historicRate, incomingMessageCount, incomingStopwatch,
                incomingRate);

        printStats();
    }

    public Instant getMessagePublishInstant(T currentResponse) {
        Instant publishInstant;

        byte[] message = getMessageByteArray(currentResponse);
        try {
            publishInstant = retrieveInstantFromArray(message);
            if (isInstantOutOfRange(publishInstant)) {
                log.debug("publishInstant is out of range: {}", publishInstant);
                publishInstant = null;
            }
        } catch (Exception ex) {
            log.debug("response message contains invalid publish millisecond value: '{}', ex: {}",
                    new String(message, StandardCharsets.UTF_8), ex.getMessage());
            publishInstant = null;
        }

        return publishInstant;
    }

    private Instant retrieveInstantFromArray(byte[] message) {
        if (message == null || message.length < Long.BYTES) {
            return Instant.MAX;
        }

        Long publishMillis = Longs.fromByteArray(message);
        return Instant.ofEpochMilli(publishMillis);
    }

    private boolean isInstantOutOfRange(Instant publishInstant) {
        return publishInstant.isBefore(Instant.EPOCH) || publishInstant.isAfter(Instant.now());
    }

    private void calculateLatencies(T currentResponse, Instant received) {
        Instant currentConsensusInstant = getConsensusInstant(currentResponse);
        Instant publishInstant = getMessagePublishInstant(currentResponse);
        long publishMillis = publishInstant == null ? 0 : publishInstant.toEpochMilli();
        long consensusMillis = currentConsensusInstant.toEpochMilli();
        long receiptMillis = received.toEpochMilli();
        double e2eSeconds = publishMillis == 0 ? 0 : (receiptMillis - publishMillis) / 1000.0;
        double publishToConsensus = publishMillis == 0 ? 0 : (consensusMillis - publishMillis) / 1000.0;
        double consensusToDelivery = (receiptMillis - consensusMillis) / 1000.0;
        log.trace("Observed message, e2eSeconds: {}s, publishToConsensus: {}s, consensusToDelivery: {}s, publish " +
                        "timestamp: {}, consensus timestamp: {}, receipt time: {}, topic sequence number: {}",
                String.format("%.03f", e2eSeconds), String.format("%.03f", publishToConsensus), String
                        .format("%.03f", consensusToDelivery), publishInstant, currentConsensusInstant,
                received, getSequenceNumber(currentResponse));

        // update interval and total stat buckets
        updateE2ELatencyStats(e2eLatencyStats, e2eLatencyTotalStats, e2eSeconds);
        updateE2ELatencyStats(publishToConsensusLatencyStats, publishToConsensusLatencyTotalStats, publishToConsensus);
        updateE2ELatencyStats(consensusToDeliveryLatencyStats, consensusToDeliveryLatencyTotalStats,
                consensusToDelivery);
    }

    private void updateE2ELatencyStats(SummaryStatistics interval, SummaryStatistics total, double latency) {
        if (latency > 0) {
            // update interval stats
            interval.addValue(latency);

            // update total stats
            total.addValue(latency);
        }
    }

    private void printStats() {
        printIntervalStats();
        printTotalStats();
    }

    private void printIntervalStats() {
        printIndividualStat(e2eLatencyStats, "Interval E2E Latency");
        printIndividualStat(publishToConsensusLatencyStats, "Interval PublishToConsensus Latency");
        printIndividualStat(consensusToDeliveryLatencyStats, "Interval ConsensusToDelivery Latency");

        // clear interval stat buckets
        e2eLatencyStats = new SummaryStatistics();
        publishToConsensusLatencyStats = new SummaryStatistics();
        consensusToDeliveryLatencyStats = new SummaryStatistics();
    }

    private void printTotalStats() {
        String errorMessage = subscribeError == null ? "" : subscribeError.getMessage();
        String totalRate = String.format("%.02f", getMessageRate(getTotalMessageCount(), totalStopwatch, lastMessage));
        log.info("Observed {} total messages in {} ({}/s). Last message received {} ago. {}.",
                getTotalMessageCount(), totalStopwatch, totalRate, lastMessage,
                success ? "Success" : "Failed : " + errorMessage);

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

            log.info("{} stats, min: {}s, max: {}s, avg: {}s",
                    name, String.format("%.03f", min), String.format("%.03f", max), String.format("%.03f", mean));
        }
    }
}
