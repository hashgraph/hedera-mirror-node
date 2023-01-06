package com.hedera.mirror.monitor.subscribe;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.monitor.publish.PublishResponse;

@ExtendWith(MockitoExtension.class)
class CompositeSubscriberTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);
    @Mock
    private MirrorSubscriber mirrorSubscriber1;

    @Mock
    private MirrorSubscriber mirrorSubscriber2;

    private CompositeSubscriber compositeSubscriber;

    @BeforeEach
    void setup() {
        compositeSubscriber = new CompositeSubscriber(Arrays.asList(mirrorSubscriber1, mirrorSubscriber2));
    }

    @Test
    void onPublish() {
        PublishResponse publishResponse = PublishResponse.builder().build();
        compositeSubscriber.onPublish(publishResponse);
        verify(mirrorSubscriber1).onPublish(publishResponse);
        verify(mirrorSubscriber2).onPublish(publishResponse);
    }

    @Test
    void subscribe() {
        SubscribeResponse subscribeResponse1 = SubscribeResponse.builder().build();
        SubscribeResponse subscribeResponse2 = SubscribeResponse.builder().build();
        when(mirrorSubscriber1.subscribe()).thenReturn(Flux.just(subscribeResponse1));
        when(mirrorSubscriber2.subscribe()).thenReturn(Flux.just(subscribeResponse2));

        StepVerifier.withVirtualTime(() -> compositeSubscriber.subscribe())
                .thenAwait(WAIT)
                .expectNext(subscribeResponse1, subscribeResponse2)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void subscriptions() {
        TestScenario subscription1 = new TestScenario();
        TestScenario subscription2 = new TestScenario();
        when(mirrorSubscriber1.getSubscriptions()).thenReturn(Flux.just(subscription1));
        when(mirrorSubscriber2.getSubscriptions()).thenReturn(Flux.just(subscription2));

        StepVerifier.withVirtualTime(() -> compositeSubscriber.getSubscriptions())
                .thenAwait(WAIT)
                .expectNext(subscription1, subscription2)
                .expectComplete()
                .verify(WAIT);
    }
}
