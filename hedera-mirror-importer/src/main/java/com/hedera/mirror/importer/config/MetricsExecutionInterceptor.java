/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.config;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Intercepts requests to the S3 API and records relevant metrics before continuing.
 */
@Log4j2
@Named
@RequiredArgsConstructor
public class MetricsExecutionInterceptor implements ExecutionInterceptor {

    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("(\\d{1,10}\\.\\d{1,10}\\.\\d{1,10})");
    private static final Pattern SIDECAR_PATTERN = Pattern.compile("Z_\\d{1,2}\\.rcd");
    private static final ExecutionAttribute<ResponseSizeSubscriber> SIZE = new ExecutionAttribute<>("size");
    private static final ExecutionAttribute<Instant> START_TIME = new ExecutionAttribute<>("start-time");
    private static final String LIST = "list";
    private static final String SIDECAR = "sidecar";
    private static final String SIGNATURE = "signature";
    private static final String SIGNED = "signed";
    private static final String START_AFTER = "start-after";

    private final MeterRegistry meterRegistry;

    private final Timer.Builder requestMetric = Timer.builder("hedera.mirror.download.request")
            .description("The time in seconds it took to receive the response from S3");

    private final DistributionSummary.Builder responseSizeMetric = DistributionSummary.builder(
                    "hedera.mirror.download.response")
            .description("The size of the response in bytes returned from S3")
            .baseUnit("bytes");

    @Override
    public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
        executionAttributes.putAttributeIfAbsent(START_TIME, Instant.now());
    }

    // Wrap the response to count the number of bytes read
    @Override
    public Optional<Publisher<ByteBuffer>> modifyAsyncHttpResponseContent(
            Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
        return context.responsePublisher().map(publisher -> subscriber -> {
            ResponseSizeSubscriber responseSizeSubscriber = new ResponseSizeSubscriber(subscriber);
            executionAttributes.putAttributeIfAbsent(SIZE, responseSizeSubscriber);
            publisher.subscribe(responseSizeSubscriber);
        });
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
        try {
            String uri = context.httpRequest().getUri().toString();
            EntityId nodeAccountId = getNodeAccountId(uri);
            Instant startTime = executionAttributes.getAttribute(START_TIME);
            ResponseSizeSubscriber responseSizeSubscriber = executionAttributes.getAttribute(SIZE);

            String[] tags = {
                "action", getAction(uri),
                "method", context.httpRequest().method().name(),
                "nodeAccount", nodeAccountId.getEntityNum().toString(),
                "realm", nodeAccountId.getRealmNum().toString(),
                "shard", nodeAccountId.getShardNum().toString(),
                "status", String.valueOf(context.httpResponse().statusCode()),
                "type", getType(uri)
            };

            if (startTime != null) {
                requestMetric.tags(tags).register(meterRegistry).record(Duration.between(startTime, Instant.now()));
            }

            if (responseSizeSubscriber != null) {
                responseSizeMetric
                        .tags(tags)
                        .register(meterRegistry)
                        .record(Double.valueOf(responseSizeSubscriber.getSize()));
            }
        } catch (Exception e) {
            log.warn("Unable to collect S3 metrics", e);
        }
    }

    // Instead of tagging the URI path, simplify it to the 3 actions we use from the S3 API
    private String getAction(String uri) {
        if (uri.contains(START_AFTER)) {
            return LIST;
        } else if (uri.contains(StreamType.SIGNATURE_SUFFIX)) {
            return SIGNATURE;
        } else if (SIDECAR_PATTERN.matcher(uri).find()) {
            return SIDECAR;
        } else {
            return SIGNED;
        }
    }

    private EntityId getNodeAccountId(String uri) {
        Matcher matcher = ENTITY_ID_PATTERN.matcher(uri);

        if (matcher.find() && matcher.groupCount() == 1) {
            return EntityId.of(matcher.group(1), EntityType.ACCOUNT);
        }

        throw new IllegalStateException("Could not detect a node account ID in URI: " + uri);
    }

    private String getType(String uri) {
        for (StreamType streamType : StreamType.values()) {
            if (uri.contains(streamType.getPath())) {
                return streamType.name();
            }
        }

        return "UNKNOWN";
    }

    @Getter
    @RequiredArgsConstructor
    private class ResponseSizeSubscriber implements Subscriber<ByteBuffer> {

        private final Subscriber<? super ByteBuffer> wrapped;
        private long size = 0L;

        @Override
        public void onSubscribe(Subscription s) {
            wrapped.onSubscribe(s);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            wrapped.onNext(byteBuffer);
            if (byteBuffer.hasRemaining()) {
                size += byteBuffer.remaining();
            }
        }

        @Override
        public void onError(Throwable t) {
            wrapped.onError(t);
        }

        @Override
        public void onComplete() {
            wrapped.onComplete();
        }
    }
}
