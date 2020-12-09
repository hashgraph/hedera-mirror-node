package com.hedera.mirror.monitor.subscribe;

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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishResponse;

@Log4j2
@RequiredArgsConstructor
public class RestSubscriber implements Subscriber {

    private final MeterRegistry meterRegistry;
    private final FluxSink<PublishResponse> restProcessor;
    private final Map<TransactionType, Timer> timers;
    private static final SecureRandom RANDOM = new SecureRandom();

    public RestSubscriber(MeterRegistry meterRegistry, MonitorProperties monitorProperties,
                          RestSubscriberProperties properties, WebClient.Builder webClientBuilder) {
        this.meterRegistry = meterRegistry;
        this.timers = new ConcurrentHashMap<>();

        String url = monitorProperties.getMirrorNode().getRest().getBaseUrl();
        WebClient webClient = webClientBuilder.baseUrl(url)
                .defaultHeaders(h -> h.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();

        DirectProcessor<PublishResponse> directProcessor = DirectProcessor.create();
        restProcessor = directProcessor.sink();

        RetryBackoffSpec retrySpec = Retry
                .backoff(properties.getRetry().getMaxAttempts(), properties.getRetry().getMinBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(this::shouldRetry)
                .doBeforeRetry(r -> log.debug("Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1, r.failure()));

        directProcessor.doOnSubscribe(s -> log.info("Connecting to mirror node {}", url))
                .filter(publishResponse -> Double
                        .compare(RANDOM.nextDouble(), properties.getValidationPercentage()) < 1)
                .doOnNext(publishResponse -> log.trace("Querying REST API: {}", publishResponse))
                .doFinally(s -> log.warn("Received {} signal", s))
                .limitRequest(properties.getLimit())
                .take(properties.getDuration())
                .flatMap(publishResponse -> webClient.get()
                        .uri("/transactions/{transactionId}", toString(publishResponse
                                .getTransactionId()))
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(json -> log.trace("Response: {}", json))
                        .timeout(properties.getTimeout())
                        .retryWhen(retrySpec)
                        .onErrorContinue((t, o) -> log.warn("Error subscribing to REST API: {}", t))
                        .doOnNext(clientResponse -> record(publishResponse)))
                .subscribe();
    }

    @Override
    public void onPublish(PublishResponse response) {
        restProcessor.next(response);
    }

    private boolean shouldRetry(Throwable t) {
        return t instanceof WebClientResponseException &&
                ((WebClientResponseException) t).getStatusCode() == HttpStatus.NOT_FOUND;
    }

    private void record(PublishResponse r) {
        Duration latency = Duration.between(r.getRequest().getTimestamp(), Instant.now());
        log.debug("Transaction retrieved with a latency of {}s", latency.toSeconds());
        Timer timer = timers.computeIfAbsent(r.getRequest().getType(), this::newTimer);
        timer.record(latency);
    }

    private Timer newTimer(TransactionType type) {
        return Timer.builder(METRIC_NAME)
                .tag("api", "rest")
                .tag("type", type.name())
                .register(meterRegistry);
    }

    private String toString(TransactionId tid) {
        return tid.accountId + "-" + tid.validStart.getEpochSecond() + "-" + tid.validStart.getNano();
    }
}
