package com.hedera.mirror.monitor.subscribe.rest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import reactor.test.StepVerifier;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.MirrorNodeProperties;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscriberProtocol;
import com.hedera.mirror.monitor.subscribe.Subscription;
import com.hedera.mirror.monitor.subscribe.SubscriptionStatus;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RestSubscriberTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private SubscribeProperties subscribeProperties;
    private RestSubscriberProperties restSubscriberProperties;
    private RestSubscriber restSubscriber;

    @BeforeEach
    void setup() {
        MonitorProperties monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(new MirrorNodeProperties());
        monitorProperties.getMirrorNode().getRest().setHost("127.0.0.1");

        restSubscriberProperties = new RestSubscriberProperties();
        restSubscriberProperties.setLimit(3L);
        restSubscriberProperties.setName("test");
        restSubscriberProperties.getRetry().setMaxAttempts(2L);
        restSubscriberProperties.getRetry().setMinBackoff(Duration.ofNanos(1L));
        restSubscriberProperties.getRetry().setMaxBackoff(Duration.ofNanos(2L));

        subscribeProperties = new SubscribeProperties();
        subscribeProperties.getRest().add(restSubscriberProperties);

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        restSubscriber = new RestSubscriber(monitorProperties, subscribeProperties, builder);
    }

    @Test
    void subscribe() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(2L, Subscription::getCount)
                .returns(Map.of(), Subscription::getErrors)
                .returns(SubscriberProtocol.REST, Subscription::getProtocol)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus);
    }

    @Test
    void multipleSubscribers() {
        restSubscriberProperties.setSubscribers(2);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(4L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction, times(4)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().collectList().block())
                .hasSize(2)
                .allSatisfy(s -> assertThat(s).isNotNull()
                        .returns(2L, Subscription::getCount)
                        .returns(Map.of(), Subscription::getErrors)
                        .returns(restSubscriberProperties, Subscription::getProperties)
                        .returns(SubscriberProtocol.REST, Subscription::getProtocol)
                        .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus))
                .extracting(Subscription::getId)
                .containsExactly(1, 2);
    }

    @Test
    void multipleScenarios() {
        RestSubscriberProperties restSubscriberProperties2 = new RestSubscriberProperties();
        restSubscriberProperties2.setName("test2");
        subscribeProperties.getRest().add(restSubscriberProperties2);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(4L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(4)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().collectList().block())
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allSatisfy(s -> assertThat(s).isNotNull()
                        .returns(2L, Subscription::getCount)
                        .returns(Map.of(), Subscription::getErrors))
                .extracting(Subscription::getName)
                .doesNotHaveDuplicates();
    }

    @Test
    void disabled() {
        subscribeProperties.setEnabled(false);
        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().count().block()).isZero();
    }

    @Test
    void disabledScenario() {
        restSubscriberProperties.setEnabled(false);
        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().count().block()).isZero();
    }

    @Test
    void subscribeEmpty() {
        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Subscription::getCount)
                .returns(Map.of(), Subscription::getErrors)
                .returns(SubscriberProtocol.REST, Subscription::getProtocol)
                .returns(SubscriptionStatus.IDLE, Subscription::getStatus);
    }

    @Test
    void duration() {
        restSubscriberProperties.setDuration(Duration.ofSeconds(1));
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK))
                .thenReturn(response(HttpStatus.OK).delayElement(Duration.ofSeconds(5L)));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(1L)
                .thenAwait(Duration.ofMillis(500L))
                .expectComplete()
                .verify(Duration.ofMillis(1500L));

        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(1L, Subscription::getCount)
                .returns(Map.of(), Subscription::getErrors)
                .returns(SubscriberProtocol.REST, Subscription::getProtocol)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus);
    }

    @Test
    void limitReached() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(3L)
                .expectComplete()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(3L, Subscription::getCount);
    }

    @Test
    void limitExceeded() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class))).thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(3L)
                .expectComplete()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(3L, Subscription::getCount);
    }

    @Test
    void nonRetryableError() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.INTERNAL_SERVER_ERROR));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(1L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Subscription::getCount)
                .returns(Map.of("500", 1), Subscription::getErrors)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus);
    }

    @Test
    void recovers() {
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofMillis(500L))
                .expectNextCount(1L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(1L, Subscription::getCount)
                .returns(Map.of(), Subscription::getErrors)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus);
    }

    @Test
    void neverRecovers() {
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(1L))
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Subscription::getCount)
                .returns(Map.of("404", 1), Subscription::getErrors)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus);
    }

    @Test
    void samplePercent0() {
        restSubscriberProperties.setLimit(1000L);
        restSubscriberProperties.setSamplePercent(0.0);

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> {
                    for (int i = 0; i < restSubscriberProperties.getLimit(); ++i) {
                        restSubscriber.onPublish(publishResponse());
                    }
                })
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Subscription::getCount)
                .returns(Map.of(), Subscription::getErrors)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus);
    }

    @Test
    void samplePercent75() {
        restSubscriberProperties.setLimit(256L);
        restSubscriberProperties.setSamplePercent(0.75);
        int min = (int) (restSubscriberProperties.getLimit() * (restSubscriberProperties.getSamplePercent() - 0.5));
        int max = (int) (restSubscriberProperties.getLimit() * (restSubscriberProperties.getSamplePercent() + 0.5));
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK))
                .thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> {
                    for (int i = 0; i < restSubscriberProperties.getLimit(); ++i) {
                        restSubscriber.onPublish(publishResponse());
                    }
                })
                .thenAwait(Duration.ofSeconds(1L))
                .expectNextCount(min)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, atLeast(min)).exchange(Mockito.isA(ClientRequest.class));
        verify(exchangeFunction, atMost(max)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(Map.of(), Subscription::getErrors)
                .extracting(Subscription::getCount)
                .isNotNull()
                .matches(count -> count >= min && count <= max);
    }

    @Test
    void samplePercent100() {
        restSubscriberProperties.setLimit(256L);
        restSubscriberProperties.setSamplePercent(1.0);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK))
                .thenReturn(response(HttpStatus.OK));

        restSubscriber.subscribe()
                .as(StepVerifier::create)
                .then(() -> {
                    for (int i = 0; i < restSubscriberProperties.getLimit(); ++i) {
                        restSubscriber.onPublish(publishResponse());
                    }
                })
                .thenAwait(Duration.ofSeconds(1L))
                .expectNextCount(restSubscriberProperties.getLimit())
                .thenCancel()
                .verify(Duration.ofSeconds(2L));

        verify(exchangeFunction, times((int) restSubscriberProperties.getLimit()))
                .exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(Map.of(), Subscription::getErrors)
                .returns(SubscriptionStatus.COMPLETED, Subscription::getStatus)
                .extracting(Subscription::getCount)
                .isEqualTo(restSubscriberProperties.getLimit());
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
            return Mono.defer(() -> Mono
                    .error(WebClientResponseException.create(httpStatus.value(), "", HttpHeaders.EMPTY, null, null)));
        }
        return Mono.just(ClientResponse.create(httpStatus)
                .header("Content-Type", "application/json")
                .body("{}")
                .build());
    }
}
