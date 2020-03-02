package com.hedera.mirror.grpc.jmeter.sampler.result;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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
    private final Stopwatch stopwatch = Stopwatch.createStarted();
    private final Instant subscribeStart = Instant.now();
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

    public long getMessageRate() {
        long seconds = stopwatch.elapsed(TimeUnit.SECONDS);
        return seconds > 0 ? getTotalMessageCount() / seconds : 0;
    }

    public void onNext(T result) {

        historical = getConsensusInstant(result).isBefore(subscribeStart);
        if (historical) {
            ++historicalMessageCount;
        } else {
            ++incomingMessageCount;
        }

        validateResponse(result);

        last = result;
    }

    public void onComplete() {
        log.info("Observed {} historic and {} incoming messages in {} ({}/s): {}", historicalMessageCount,
                incomingMessageCount, stopwatch, getMessageRate(), success ? "success" : "failed");
    }

    public void onError(Throwable err) {
        log.error("GRPC error on subscription : {}", err.getMessage());
        throw new IllegalArgumentException("Error on subscription");
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
                log.error("Out of order messgae sequence. Last : {}, current : {}", last, currentResponse);
                throw new IllegalArgumentException("Out of order message sequence. Expected " + (lastSequenceNumber + 1) + " got " + currentSequenceNumber);
            }

            if (!currentConsensusInstant.isAfter(lastConsensusInstant)) {
                log.error("Out of order messgae sequence. Last : {}, current : {}", last, currentResponse);
                throw new IllegalArgumentException("Out of order message timestamp. Expected " + currentConsensusInstant +
                        " to be after " + lastConsensusInstant);
            }
        }
    }
}
