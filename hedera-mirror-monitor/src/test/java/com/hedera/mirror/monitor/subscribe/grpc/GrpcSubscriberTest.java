package com.hedera.mirror.monitor.subscribe.grpc;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeResponse;
import com.hedera.mirror.monitor.subscribe.SubscriberProtocol;
import com.hedera.mirror.monitor.subscribe.Subscription;

@ExtendWith(MockitoExtension.class)
class GrpcSubscriberTest {

    private final ExpressionConverter expressionConverter = p -> p;
    private final SubscribeProperties subscribeProperties = new SubscribeProperties();
    private final GrpcSubscriberProperties grpcSubscriberProperties = new GrpcSubscriberProperties();

    @Mock
    private GrpcClient grpcClient;

    private GrpcSubscriber grpcSubscriber;

    @BeforeEach
    void setup() {
        grpcSubscriberProperties.setName("Test");
        grpcSubscriberProperties.setTopicId("0.0.1000");
        subscribeProperties.getGrpc().add(grpcSubscriberProperties);
        grpcSubscriber = new GrpcSubscriber(expressionConverter, grpcClient, subscribeProperties);
    }

    @Test
    void onPublish() {
        grpcSubscriber.onPublish(PublishResponse.builder().build());
        verifyNoInteractions(grpcClient);
    }

    @Test
    void subscribe() {
        when(grpcClient.subscribe(any())).thenReturn(Flux.just(response(), response()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(2L)
                .expectComplete()
                .verify(Duration.ofMillis(500L));
        assertThat(grpcSubscriber.subscriptions().blockFirst())
                .isNotNull()
                .returns(SubscriberProtocol.GRPC, Subscription::getProtocol);
    }

    @Test
    void multipleSubscribers() {
        grpcSubscriberProperties.setSubscribers(2);
        when(grpcClient.subscribe(any())).thenReturn(Flux.just(response(), response()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(4L)
                .verifyComplete();
        assertThat(grpcSubscriber.subscriptions().collectList().block())
                .hasSize(2)
                .allSatisfy(s -> assertThat(s).isNotNull()
                        .returns(grpcSubscriberProperties, Subscription::getProperties)
                        .returns(SubscriberProtocol.GRPC, Subscription::getProtocol))
                .extracting(Subscription::getId)
                .containsExactly(1, 2);
    }

    @Test
    void multipleScenarios() {
        GrpcSubscriberProperties grpcSubscriberProperties2 = new GrpcSubscriberProperties();
        grpcSubscriberProperties2.setName("Test2");
        grpcSubscriberProperties2.setTopicId("0.0.1001");
        subscribeProperties.getGrpc().add(grpcSubscriberProperties2);

        when(grpcClient.subscribe(any())).thenReturn(Flux.just(response(), response()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(4L)
                .verifyComplete();
        assertThat(grpcSubscriber.subscriptions().collectList().block())
                .hasSize(2)
                .allSatisfy(s -> assertThat(s).isNotNull()
                        .returns(SubscriberProtocol.GRPC, Subscription::getProtocol))
                .extracting(Subscription::getName)
                .doesNotHaveDuplicates();
    }

    @Test
    void disabledScenario() {
        grpcSubscriberProperties.setEnabled(false);
        verifyNoInteractions(grpcClient);
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(200));
        assertThat(grpcSubscriber.subscriptions().count().block()).isEqualTo(0L);
    }

    @Test
    void disabled() {
        subscribeProperties.setEnabled(false);
        verifyNoInteractions(grpcClient);
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(200));
        assertThat(grpcSubscriber.subscriptions().count().block()).isEqualTo(0L);
    }

    @Test
    void recoverableError() {
        when(grpcClient.subscribe(any()))
                .thenReturn(Flux.error(new IllegalStateException()))
                .thenReturn(Flux.just(response()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .verifyComplete();
    }

    @Test
    void retriesExhausted() {
        grpcSubscriberProperties.getRetry().setMaxAttempts(2);
        when(grpcClient.subscribe(any())).thenReturn(Flux.error(new IllegalStateException()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .verifyError(IllegalStateException.class);
    }

    @Test
    void nonRetryableError() {
        when(grpcClient.subscribe(any())).thenReturn(Flux.error(new StatusRuntimeException(Status.INVALID_ARGUMENT)));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .verifyError(StatusRuntimeException.class);
    }

    private SubscribeResponse response() {
        return SubscribeResponse.builder()
                .receivedTimestamp(Instant.now())
                .build();
    }
}
