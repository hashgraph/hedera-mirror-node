package com.hedera.mirror.grpc.listener;

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

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public abstract class AbstractSharedTopicListenerTest extends AbstractTopicListenerTest {

    @Test
    @DisplayName("slow subscriber receives overflow exception and normal subscriber is not affected")
    void slowSubscriberOverflowException() {
        int maxBufferSize = 16;
        Duration interval = Duration.ofMillis(10L);
        int prefetch = 4;

        // step verifier requests 2 messages on subscription, and there are downstream buffers after the backpressure
        // buffer, to ensure overflow, set the number of topic messages to send as follows
        int numMessages = maxBufferSize + prefetch * 2 + 3;
        listenerProperties.setInterval(interval);
        listenerProperties.setMaxBufferSize(maxBufferSize);
        listenerProperties.setPrefetch(prefetch);

        TopicMessageFilter filterFast = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(topicId)
                .build();

        // create a fast subscriber to keep the shared flux open. the fast subscriber should receive all messages
        var stepVerifierFast = topicListener.listen(filterFast)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextSequence(LongStream.range(1, numMessages + 1).boxed().collect(Collectors.toList()))
                .thenCancel()
                .verifyLater();

        TopicMessageFilter filterSlow = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(topicId)
                .build();

        // send the messages in two batches and wait 2 * polling interval between. Limit the first batch to
        // maxBufferSize messages so it definitely won't cause overflow with the SharedPollingTopicListener.
        // The wait also gives the subscriber threads chance to consume messages in slow environment.
        Flux<TopicMessage> firstBatch = domainBuilder.topicMessages(maxBufferSize, future);
        Flux<TopicMessage> secondBatch = domainBuilder
                .topicMessages(numMessages - maxBufferSize, future.plusSeconds(1));

        // the slow subscriber
        topicListener.listen(filterSlow)
                .map(TopicMessage::getSequenceNumber)
                .as(p -> StepVerifier.create(p, 1)) // initial request amount of 1
                .thenRequest(1) // trigger subscription
                .thenAwait(Duration.ofMillis(10L))
                .then(() -> publish(firstBatch))
                .thenAwait(interval.multipliedBy(2))
                .then(() -> publish(secondBatch))
                .expectNext(1L, 2L)
                .thenAwait(Duration.ofMillis(500L)) // stall to overrun backpressure buffer
                .thenRequest(Long.MAX_VALUE)
                .thenConsumeWhile(n -> n < numMessages)
                .expectErrorMatches(Exceptions::isOverflow)
                .verify(Duration.ofMillis(1000L));

        stepVerifierFast.verify(Duration.ofMillis(1000L));
    }
}
