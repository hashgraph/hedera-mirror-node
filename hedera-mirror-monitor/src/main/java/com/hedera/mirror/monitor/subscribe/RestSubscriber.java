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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;
import reactor.util.retry.Retry;

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

        directProcessor.doOnSubscribe(s -> log.info("Connecting to mirror node {}", url))
                .doOnNext(publishResponse -> log.trace("Querying REST API: {}", publishResponse))
                .doOnComplete(() -> log.info("Finished subscription"))
                .limitRequest(properties.getLimit())
                .take(properties.getDuration())
                .onErrorContinue((t, o) -> log.info("Error: {}", t))
                .flatMap(publishResponse -> webClient.get()
                        .uri("/transactions/{transactionId}", toString(publishResponse.getTransactionId()))
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(json -> log.trace("Response: {}", json))
                        .timeout(properties.getTimeout())
                        .retryWhen(Retry.backoff(properties.getRetries(), properties.getFrequency())
                                .filter(this::isClientError))
                        .doOnNext(clientResponse -> record(publishResponse))
                ).subscribe();
    }

    @Override
    public void onPublish(PublishResponse response) {
        restProcessor.next(response);
    }

    private boolean isClientError(Throwable t) {
        return t instanceof WebClientResponseException &&
                ((WebClientResponseException) t).getStatusCode().is4xxClientError();
    }

    private void record(PublishResponse r) {
        Timer timer = timers.computeIfAbsent(r.getRequest().getType(), this::newTimer);
        timer.record(Duration.between(r.getRequest().getTimestamp(), Instant.now()));
    }

    private Timer newTimer(TransactionType type) {
        return Timer.builder("hedera.mirror.monitor.subscribe")
                .tag("api", "rest")
                .tag("type", type.name())
                .register(meterRegistry);
    }

    private String toString(TransactionId tid) {
        return tid.accountId + "-" + tid.validStart.getEpochSecond() + "-" + tid.validStart.getNano();
    }
}
