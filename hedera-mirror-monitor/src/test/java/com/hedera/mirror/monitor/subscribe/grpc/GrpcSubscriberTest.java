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
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeResponse;

@ExtendWith(MockitoExtension.class)
public class GrpcSubscriberTest {

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
    void subscribe() {
        when(grpcClient.subscribe(any())).thenReturn(Flux.just(response(), response()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(2L)
                .verifyComplete();
    }

    @Test
    void multipleSubscribers() {
        grpcSubscriberProperties.setSubscribers(2);
        when(grpcClient.subscribe(any())).thenReturn(Flux.just(response(), response()));
        grpcSubscriber.subscribe()
                .as(StepVerifier::create)
                .expectNextCount(4L)
                .verifyComplete();
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
    void clientError() {
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
