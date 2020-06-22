package com.hedera.mirror.grpc.jmeter.sampler.result;

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
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;

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

    abstract Instant getConsensusInstant(T t);

    abstract long getSequenceNumber(T t);

    abstract String getMessage(T t);

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

    public void onNext(T result) {
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

        last = result;
    }

    public void onComplete() {
        String errorMessage = subscribeError == null ? "" : subscribeError.getMessage();
        String totalRate = String.format("%.02f", getMessageRate(getTotalMessageCount(), totalStopwatch, lastMessage));

        log.info("Observed {} total messages in {} ({}/s). Last message received {} ago. {}.",
                getTotalMessageCount(), totalStopwatch, totalRate, lastMessage,
                success ? "Success" : "Failed : " + errorMessage);

        printProgress();
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
    }

    public void onError(Throwable err) {
        subscribeError = err;
    }

    public void validateResponse(T currentResponse) {

        if (last != null) {
            Instant lastConsensusInstant = getConsensusInstant(last);
            Instant currentConsensusInstant = getConsensusInstant(currentResponse);
            long lastSequenceNumber = getSequenceNumber(last);
            long currentSequenceNumber = getSequenceNumber(currentResponse);

            log.trace("Observed message: {}, consensus timestamp: {}, topic sequence number: {}",
                    getMessage(currentResponse), currentConsensusInstant, currentSequenceNumber);

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
}
