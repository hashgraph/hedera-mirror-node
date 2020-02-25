package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class SharedPollingTopicListenerTest extends AbstractTopicListenerTest {

    @Resource
    private SharedPollingTopicListener topicListener;

    @Override
    protected TopicListener getTopicListener() {
        return topicListener;
    }

    @BeforeEach
    void setup() {
        topicListener.init(); // Clear the buffer between runs
    }

    @Test
    void multipleSubscribers() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(1)),
                domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(2)),
                domainBuilder.topicMessage(t -> t.topicNum(2).sequenceNumber(7)),
                domainBuilder.topicMessage(t -> t.topicNum(2).sequenceNumber(8)),
                domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(3))
        );

        TopicMessageFilter filter1 = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();
        TopicMessageFilter filter2 = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(2)
                .build();

        StepVerifier stepVerifier1 = getTopicListener()
                .listen(filter1)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verifyLater();

        StepVerifier stepVerifier2 = getTopicListener()
                .listen(filter2)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(7L, 8L)
                .thenCancel()
                .verifyLater();

        Uninterruptibles.sleepUninterruptibly(50, TimeUnit.MILLISECONDS);
        generator.blockLast();

        stepVerifier1.verify(Duration.ofMillis(500));
        stepVerifier2.verify(Duration.ofMillis(500));

        getTopicListener()
                .listen(filter1)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .as("Verify can still re-subscribe after shared poller cancelled when no subscriptions")
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofMillis(100));
    }

    @Test
    void bufferFilled() {
        int bufferSize = listenerProperties.getBufferSize();
        listenerProperties.setBufferSize(2);
        topicListener.init();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        getTopicListener().listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> domainBuilder.topicMessages(5).blockLast())
                .expectNext(1L, 2L, 3L, 4L, 5L)
                .thenCancel()
                .verify(Duration.ofMillis(500));

        listenerProperties.setBufferSize(bufferSize);
    }
}
