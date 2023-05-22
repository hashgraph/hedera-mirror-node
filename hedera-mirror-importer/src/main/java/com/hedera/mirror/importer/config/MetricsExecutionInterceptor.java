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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Intercepts requests to the S3 API and records relevant metrics before continuing.
 */
@Log4j2
@Named
public class MetricsExecutionInterceptor implements ExecutionInterceptor {

    static final ExecutionAttribute<ResponseSizeSubscriber> SIZE = new ExecutionAttribute<>("size");
    static final ExecutionAttribute<Instant> START_TIME = new ExecutionAttribute<>("start-time");
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("(\\d{1,10}\\.\\d{1,10}\\.\\d{1,10})");
    private static final Pattern SIDECAR_PATTERN = Pattern.compile("Z_\\d{1,2}\\.rcd");
    private static final Pattern NODE_ID_PATTERN =
            Pattern.compile("[^\\/]{1,50}\\/(\\d{1,10})\\/(\\d{1,10})\\/([^\\/]{1,10})");
    private static final String LIST = "list";
    private static final String SIDECAR = "sidecar";
    private static final String SIGNATURE = "signature";
    private static final String SIGNED = "signed";
    private static final String START_AFTER = "start-after";

    private final MeterRegistry meterRegistry;
    private final Timer.Builder requestMetric;
    private final DistributionSummary.Builder responseSizeMetric;

    @Autowired
    public MetricsExecutionInterceptor(MeterRegistry meterRegistry) {
        this(
                meterRegistry,
                Timer.builder("hedera.mirror.download.request")
                        .description("The time in seconds it took to receive the response from S3"),
                DistributionSummary.builder("hedera.mirror.download.response")
                        .description("The size of the response in bytes returned from S3")
                        .baseUnit("bytes"));
    }

    MetricsExecutionInterceptor(
            MeterRegistry meterRegistry,
            Timer.Builder timerBuilder,
            DistributionSummary.Builder distributionSummaryBuilder) {
        this.meterRegistry = meterRegistry;
        this.requestMetric = timerBuilder;
        this.responseSizeMetric = distributionSummaryBuilder;
    }

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
            var uriAttributes = getUriAttributes(context.httpRequest().getUri());
            var startTime = executionAttributes.getAttribute(START_TIME);
            var responseSizeSubscriber = executionAttributes.getAttribute(SIZE);

            String[] tags = {
                "action", uriAttributes.action(),
                "method", context.httpRequest().method().name(),
                "node", uriAttributes.nodeId().toString(),
                "realm", uriAttributes.realm().toString(),
                "shard", uriAttributes.shard().toString(),
                "status", String.valueOf(context.httpResponse().statusCode()),
                "type", uriAttributes.streamType()
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

    private UriAttributes getUriAttributes(URI uri) {
        var decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
        var action = getAction(decodedUri);

        Matcher accountIdMatcher = ENTITY_ID_PATTERN.matcher(decodedUri);
        if (accountIdMatcher.find() && accountIdMatcher.groupCount() == 1) {
            var streamType = getType(t -> decodedUri.contains(t.getPath()));
            var entityId = EntityId.of(accountIdMatcher.group(1), EntityType.ACCOUNT);
            return new UriAttributes(
                    entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum() - 3L, streamType, action);
        }

        Matcher nodeIdMatcher = NODE_ID_PATTERN.matcher(decodedUri);
        if (nodeIdMatcher.find() && nodeIdMatcher.groupCount() == 3) {
            var streamType = getType(t -> decodedUri.contains(t.getNodeIdBasedSuffix()));
            var shard = Long.valueOf(nodeIdMatcher.group(1));
            var nodeId = Long.valueOf(nodeIdMatcher.group(2));
            return new UriAttributes(shard, 0L, nodeId, streamType, action);
        }

        throw new IllegalStateException("Could not detect a node ID or account ID in URI: " + uri);
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

    private String getType(Predicate<StreamType> matcher) {
        for (StreamType streamType : StreamType.values()) {
            if (matcher.test(streamType)) {
                return streamType.name();
            }
        }
        return "UNKNOWN";
    }

    private record UriAttributes(Long shard, Long realm, Long nodeId, String streamType, String action) {}

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
