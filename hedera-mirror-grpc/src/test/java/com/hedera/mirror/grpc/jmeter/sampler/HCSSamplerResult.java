package com.hedera.mirror.grpc.jmeter.sampler;

import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Builder
@Data
@Log4j2
public class HCSSamplerResult {
    private final long realmNum;
    private final long topicNum;
    private long historicalMessageCount = 0L;
    private long incomingMessageCount = 0L;
    private Instant lastConcensusTimestamp;
    private long lastSequenceNumber;
    private boolean success = true;

    public long getTotalMessageCount() {
        return historicalMessageCount + incomingMessageCount;
    }

    public void onNext(Timestamp timestamp, long sequenceNumber) {
        Instant currentTime = toInstant(timestamp);

        if (lastConcensusTimestamp.isAfter(Instant.MIN)) {
            if (sequenceNumber != lastSequenceNumber + 1) {
                throw new IllegalArgumentException("Out of order message sequence. Expected " + (lastSequenceNumber + 1) + " got " + sequenceNumber);
            }

            if (!currentTime.isAfter(lastConcensusTimestamp)) {
                throw new IllegalArgumentException("Out of order message timestamp. Expected " + currentTime +
                        " to be after " + lastConcensusTimestamp);
            }
        }

        if (currentTime.isBefore(TopicMessageGeneratorSampler.INCOMING_START)) {
            ++historicalMessageCount;
        } else {
            ++incomingMessageCount;
        }

        lastConcensusTimestamp = currentTime;
        lastSequenceNumber = sequenceNumber;
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
