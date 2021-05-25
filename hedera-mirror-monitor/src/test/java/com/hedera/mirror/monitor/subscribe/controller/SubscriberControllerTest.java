package com.hedera.mirror.monitor.subscribe.controller;

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

import static org.mockito.Mockito.when;

import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.SubscriberProtocol;
import com.hedera.mirror.monitor.subscribe.SubscriptionStatus;
import com.hedera.mirror.monitor.subscribe.TestSubscription;

@Log4j2
@ExtendWith(MockitoExtension.class)
class SubscriberControllerTest {

    @Mock
    private MirrorSubscriber mirrorSubscriber;

    private WebTestClient webTestClient;

    private TestSubscription subscription1;
    private TestSubscription subscription2;

    @BeforeEach
    void setup() {
        subscription1 = new TestSubscription();
        subscription1.setName("grpc1");
        subscription1.setId(1);
        subscription1.setProtocol(SubscriberProtocol.GRPC);
        subscription1.setStatus(SubscriptionStatus.COMPLETED);

        subscription2 = new TestSubscription();
        subscription2.setName("rest1");
        subscription2.setId(1);
        subscription2.setProtocol(SubscriberProtocol.REST);
        subscription2.setStatus(SubscriptionStatus.RUNNING);

        SubscriberController subscriberController = new SubscriberController(mirrorSubscriber);
        webTestClient = WebTestClient.bindToController(subscriberController).build();
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscription1, subscription2));
    }

    @Test
    void subscriptions() {
        webTestClient.get()
                .uri("/api/v1/subscriber")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsWithProtocol() {
        webTestClient.get()
                .uri("/api/v1/subscriber?protocol=REST")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription2));
    }

    @Test
    void subscriptionsWithStatus() {
        webTestClient.get()
                .uri("/api/v1/subscriber?status=COMPLETED")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription1));
    }

    @Test
    void subscriptionsWithEmptyStatus() {
        webTestClient.get()
                .uri("/api/v1/subscriber?status=")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsWithStatuses() {
        webTestClient.get()
                .uri("/api/v1/subscriber?status=COMPLETED,RUNNING")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsNotFound() {
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.empty());
        webTestClient.get()
                .uri("/api/v1/subscriber?protocol=GRPC")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void subscriptionsByName() {
        subscription2.setName(subscription1.getName());
        subscription2.setId(2);
        webTestClient.get()
                .uri("/api/v1/subscriber/grpc1")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsByNameWithStatus() {
        subscription2.setName(subscription1.getName());
        subscription2.setId(2);
        webTestClient.get()
                .uri("/api/v1/subscriber/grpc1?status=COMPLETED")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription1));
    }

    @Test
    void subscriptionsByNameNotFound() {
        webTestClient.get()
                .uri("/api/v1/subscriber/invalid")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void subscription() {
        subscription2.setName(subscription1.getName());
        subscription2.setId(2);
        webTestClient.get()
                .uri("/api/v1/subscriber/grpc1/2")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestSubscription.class)
                .isEqualTo(Arrays.asList(subscription2));
    }

    @Test
    void subscriptionIdNotFound1() {
        webTestClient.get()
                .uri("/api/v1/subscriber/grpc1/3")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void subscriptionNameNotFound() {
        webTestClient.get()
                .uri("/api/v1/subscriber/invalid/1")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }
}
