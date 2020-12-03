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

import static com.hedera.mirror.monitor.subscribe.Subscriber.METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.mirror.monitor.MirrorNodeProperties;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishResponse;

@MockitoSettings
class RestSubscriberTest {

    @Mock()
    private ExchangeFunction exchangeFunction;

    private MeterRegistry meterRegistry;
    private MonitorProperties monitorProperties;
    private RestSubscriberProperties subscriberProperties;
    private WebClient.Builder builder;
    private RestSubscriber restSubscriber;
    private CountDownLatch countDownLatch;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();

        monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(new MirrorNodeProperties());
        monitorProperties.getMirrorNode().getRest().setHost("127.0.0.1");

        subscriberProperties = new RestSubscriberProperties();
        subscriberProperties.setLimit(2L);
        subscriberProperties.getRetry().setMaxAttempts(2L);
        subscriberProperties.getRetry().setMinBackoff(Duration.ofNanos(1L));
        subscriberProperties.getRetry().setMaxBackoff(Duration.ofNanos(2L));

        builder = WebClient.builder().exchangeFunction(exchangeFunction);
        this.restSubscriber = new RestSubscriber(meterRegistry, monitorProperties, subscriberProperties, builder);
    }

    @Test
    void publish() throws Exception {
        countDownLatch = new CountDownLatch(2);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.onPublish(publishResponse());
        restSubscriber.onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertMetric(2L);
    }

    @Test
    void duration() throws Exception {
        countDownLatch = new CountDownLatch(1);
        subscriberProperties.setDuration(Duration.ofMillis(500));
        this.restSubscriber = new RestSubscriber(meterRegistry, monitorProperties, subscriberProperties, builder);
        Mono<ClientResponse> delay = response(HttpStatus.OK)
                .delayElement(Duration.ofSeconds(5L))
                .doOnSubscribe(s -> countDownLatch.countDown());
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(delay);

        restSubscriber.onPublish(publishResponse());

        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        verify(exchangeFunction).exchange(Mockito.isA(ClientRequest.class));
        assertMetric(0L);
    }

    @Test
    void limit() throws Exception {
        countDownLatch = new CountDownLatch(3);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.onPublish(publishResponse());
        restSubscriber.onPublish(publishResponse());
        restSubscriber.onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertMetric(2L);
    }

    @Test
    void nonRetryableError() throws Exception {
        countDownLatch = new CountDownLatch(1);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.INTERNAL_SERVER_ERROR));
        restSubscriber.onPublish(publishResponse());

        countDownLatch.await(500, TimeUnit.MILLISECONDS);
        verify(exchangeFunction).exchange(Mockito.isA(ClientRequest.class));
        assertMetric(0L);
    }

    @Test
    void recovers() throws Exception {
        countDownLatch = new CountDownLatch(2);
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.OK));

        restSubscriber.onPublish(publishResponse());

        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertMetric(1L);
    }

    @Test
    void neverRecovers() throws Exception {
        countDownLatch = new CountDownLatch(3);
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND));

        restSubscriber.onPublish(publishResponse());

        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertMetric(0L);
    }

    private PublishResponse publishResponse() {
        return PublishResponse.builder()
                .request(PublishRequest.builder().type(TransactionType.CONSENSUS_SUBMIT_MESSAGE).build())
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

    private void assertMetric(long count) {
        if (count > 0) {
            assertThat(meterRegistry.find(METRIC_NAME).timers())
                    .hasSize(1)
                    .element(0)
                    .extracting(Timer::takeSnapshot)
                    .hasFieldOrPropertyWithValue("count", count);
        } else {
            assertThat(meterRegistry.find(METRIC_NAME).timers()).isEmpty();
        }
    }
}
