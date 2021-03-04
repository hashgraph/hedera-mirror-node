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

import static com.hedera.mirror.monitor.subscribe.AbstractSubscriber.METRIC_DURATION;
import static com.hedera.mirror.monitor.subscribe.AbstractSubscriber.METRIC_E2E;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Suppliers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.MirrorNodeProperties;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishResponse;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RestSubscriberTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private MeterRegistry meterRegistry;
    private MonitorProperties monitorProperties;
    private RestSubscriberProperties subscriberProperties;
    private WebClient.Builder builder;
    private Supplier<RestSubscriber> restSubscriber;
    private CountDownLatch countDownLatch;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();

        monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(new MirrorNodeProperties());
        monitorProperties.getMirrorNode().getRest().setHost("127.0.0.1");

        subscriberProperties = new RestSubscriberProperties();
        subscriberProperties.setLimit(3L);
        subscriberProperties.setName("test");
        subscriberProperties.getRetry().setMaxAttempts(2L);
        subscriberProperties.getRetry().setMinBackoff(Duration.ofNanos(1L));
        subscriberProperties.getRetry().setMaxBackoff(Duration.ofNanos(2L));

        builder = WebClient.builder().exchangeFunction(exchangeFunction);
        restSubscriber = Suppliers
                .memoize(() -> new RestSubscriber(meterRegistry, monitorProperties, subscriberProperties, builder));
    }

    @Test
    void publish() throws Exception {
        countDownLatch = new CountDownLatch(2);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.get().onPublish(publishResponse());
        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(2L);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isNotEmpty();
    }

    @Test
    void duration() throws Exception {
        countDownLatch = new CountDownLatch(1);
        subscriberProperties.setDuration(Duration.ofSeconds(1));
        Mono<ClientResponse> delay = response(HttpStatus.OK)
                .delayElement(Duration.ofSeconds(5L))
                .doOnSubscribe(s -> countDownLatch.countDown());
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(delay);

        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(2, TimeUnit.SECONDS);
        verify(exchangeFunction).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(0L);
    }

    @Test
    void limitReached() throws Exception {
        countDownLatch = new CountDownLatch(3);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.get().onPublish(publishResponse());
        restSubscriber.get().onPublish(publishResponse());
        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(3L);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isEmpty();
    }

    @Test
    void limitExceeded() throws Exception {
        countDownLatch = new CountDownLatch(3);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.get().onPublish(publishResponse());
        restSubscriber.get().onPublish(publishResponse());
        restSubscriber.get().onPublish(publishResponse());
        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(3L);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isEmpty();
    }

    @Test
    void nonRetryableError() throws Exception {
        countDownLatch = new CountDownLatch(1);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.INTERNAL_SERVER_ERROR));
        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(0L);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isNotEmpty();
    }

    @Test
    void recovers() throws Exception {
        countDownLatch = new CountDownLatch(2);
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.OK));

        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(1L);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isNotEmpty();
    }

    @Test
    void neverRecovers() throws Exception {
        countDownLatch = new CountDownLatch(3);
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND));

        restSubscriber.get().onPublish(publishResponse());

        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(0L);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isNotEmpty();
    }

    @Test
    void zeroSamplePercent() throws InterruptedException {
        subscriberProperties.setSamplePercent(0.0);

        int sampleSize = 1000;
        countDownLatch = new CountDownLatch(sampleSize);
        subscriberProperties.setLimit(sampleSize);

        for (int i = 0; i < sampleSize; ++i) {
            restSubscriber.get().onPublish(publishResponse());
        }

        verify(exchangeFunction, times(0)).exchange(Mockito.isA(ClientRequest.class));
        assertE2EMetric(0L);
    }

    @Test
    void middleSamplePercent() throws InterruptedException {
        subscriberProperties.setSamplePercent(.75);

        int sampleSize = 1000;
        countDownLatch = new CountDownLatch(700);
        subscriberProperties.setLimit(sampleSize);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        for (int i = 0; i < sampleSize; ++i) {
            restSubscriber.get().onPublish(publishResponse());
        }

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, atLeast(700)).exchange(Mockito.isA(ClientRequest.class));
        verify(exchangeFunction, atMost(800)).exchange(Mockito.isA(ClientRequest.class));
    }

    @Test
    void oneHundredSamplePercent() throws Exception {
        subscriberProperties.setSamplePercent(1.0);

        int sampleSize = 1000;
        countDownLatch = new CountDownLatch(700);
        subscriberProperties.setLimit(sampleSize);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        for (int i = 0; i < sampleSize; ++i) {
            restSubscriber.get().onPublish(publishResponse());
        }

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(1000)).exchange(Mockito.isA(ClientRequest.class));

        assertE2EMetric(1000L);
    }

    private PublishResponse publishResponse() {
        return PublishResponse.builder()
                .request(PublishRequest.builder()
                        .timestamp(Instant.now())
                        .type(TransactionType.CONSENSUS_SUBMIT_MESSAGE)
                        .build())
                .timestamp(Instant.now())
                .transactionId(TransactionId.withValidStart(AccountId.fromString("0.0.1000"), Instant.ofEpochSecond(1)))
                .build();
    }

    private Mono<ClientResponse> response(HttpStatus httpStatus) {
        if (!httpStatus.is2xxSuccessful()) {
            return Mono.<ClientResponse>defer(() -> Mono
                    .error(WebClientResponseException.create(httpStatus.value(), "", HttpHeaders.EMPTY, null, null)))
                    .doFinally(s -> countDownLatch.countDown());
        }
        return Mono.just(ClientResponse.create(httpStatus)
                .header("Content-Type", "application/json")
                .body("{}")
                .build())
                .doFinally(s -> countDownLatch.countDown());
    }

    private void assertE2EMetric(long count) {
        if (count > 0) {
            assertThat(meterRegistry.find(METRIC_E2E).timers())
                    .hasSize(1)
                    .element(0)
                    .extracting(Timer::takeSnapshot)
                    .hasFieldOrPropertyWithValue("count", count);
        } else {
            assertThat(meterRegistry.find(METRIC_E2E).timers()).isEmpty();
        }
    }
}
