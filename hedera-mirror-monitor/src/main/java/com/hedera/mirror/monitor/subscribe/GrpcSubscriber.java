package com.hedera.mirror.monitor.subscribe;

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

import static io.grpc.Status.Code.INVALID_ARGUMENT;

import com.google.common.util.concurrent.Uninterruptibles;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishResponse;

public class GrpcSubscriber extends AbstractSubscriber<GrpcSubscriberProperties> {

    private final MirrorClient mirrorClient;
    private final AtomicLong retries;

    private MirrorSubscriptionHandle subscription;
    private volatile MirrorConsensusTopicResponse lastReceived;
    private Instant endTime;

    GrpcSubscriber(ExpressionConverter expressionConverter, MeterRegistry meterRegistry,
                   MonitorProperties monitorProperties, GrpcSubscriberProperties subscriberProperties) {
        super(meterRegistry, subscriberProperties);
        this.retries = new AtomicLong(0L);

        String topicId = expressionConverter.convert(subscriberProperties.getTopicId());
        subscriberProperties.setTopicId(topicId);

        String endpoint = monitorProperties.getMirrorNode().getGrpc().getEndpoint();
        log.info("Connecting to mirror node {}", endpoint);
        this.mirrorClient = new MirrorClient(endpoint);
        subscribe();
    }

    private void onNext(MirrorConsensusTopicResponse topicResponse) {
        long endTimestamp = System.currentTimeMillis();
        counter.incrementAndGet();
        retries.set(0L);
        log.trace("Received message #{} with timestamp {}", topicResponse.sequenceNumber,
                topicResponse.consensusTimestamp);

        if (lastReceived != null) {
            long expected = lastReceived.sequenceNumber + 1;
            if (topicResponse.sequenceNumber != expected) {
                log.warn("Expected sequence number {} but received {}", expected, topicResponse.sequenceNumber);
            }
        }

        this.lastReceived = topicResponse;
        Long timestamp = Utility.getTimestamp(topicResponse.message);

        if (timestamp == null || timestamp <= 0 || timestamp >= System.currentTimeMillis()) {
            log.warn("Invalid timestamp in message: {}", timestamp);
            return;
        }

        long latency = endTimestamp - timestamp;
        getLatencyTimer(TransactionType.CONSENSUS_SUBMIT_MESSAGE).record(latency, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onError(Throwable t) {
        log.error("Error subscribing: ", t);
        Status.Code statusCode = getStatusCode(t);
        errors.add(statusCode.name());

        if (shouldRetry(t)) {
            AbstractSubscriberProperties.RetryProperties retry = subscriberProperties.getRetry();
            long delayMillis = retries.get() * retry.getMinBackoff().toMillis();
            Duration retryDuration = Duration.ofMillis(Math.min(delayMillis, retry.getMaxBackoff().toMillis()));
            log.info("Retrying in {}s", retryDuration.toSeconds());
            Uninterruptibles.sleepUninterruptibly(retryDuration);
            subscribe();
        } else {
            close();
        }
    }

    @Override
    protected boolean shouldRetry(Throwable t) {
        Status.Code statusCode = getStatusCode(t);

        // Don't retry client errors
        if (statusCode == INVALID_ARGUMENT) {
            return false;
        }

        return retries.incrementAndGet() < subscriberProperties.getRetry().getMaxAttempts();
    }

    private Status.Code getStatusCode(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) t).getStatus().getCode();
        }
        return Status.Code.UNKNOWN;
    }

    @Override
    public void onPublish(PublishResponse response) {
        // Ignore for now
    }

    @Override
    public void close() {
        try {
            super.close();
            log.info("Closing mirror node connection");
            subscription.unsubscribe();
            mirrorClient.close(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore
        }
    }

    private synchronized void subscribe() {
        long limit = subscriberProperties.getLimit();
        MirrorConsensusTopicQuery mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery();
        mirrorConsensusTopicQuery.setTopicId(ConsensusTopicId.fromString(subscriberProperties.getTopicId()));
        mirrorConsensusTopicQuery.setLimit(limit > 0 ? limit - counter.get() : 0);

        Instant startTime = lastReceived != null ? lastReceived.consensusTimestamp.plusNanos(1) : subscriberProperties
                .getStartTime();
        startTime = Objects.requireNonNullElseGet(startTime, Instant::now);
        mirrorConsensusTopicQuery.setStartTime(startTime);

        if (endTime != null) {
            mirrorConsensusTopicQuery.setEndTime(endTime);
        } else {
            Duration duration = subscriberProperties.getDuration();
            if (duration != null) {
                endTime = startTime.plus(duration);
                mirrorConsensusTopicQuery.setEndTime(endTime);
            }
        }

        log.info("Starting subscriber: {}", subscriberProperties);
        subscription = mirrorConsensusTopicQuery.subscribe(mirrorClient, this::onNext, this::onError);
    }
}
