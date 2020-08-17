package com.hedera.mirror.grpc.listener;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public abstract class AbstractSharedTopicListenerTest extends AbstractTopicListenerTest {

    @Test
    void slowSubscriberOverflowException() {
        int maxBufferSize = 16;
        listenerProperties.setMaxBufferSize(16);
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();
        getTopicListener().listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(p -> StepVerifier.create(p, 1)) // initial request amount - 1
                .thenRequest(1) // trigger subscription
                .thenAwait(Duration.ofMillis(10L))
                .then(() -> {
                    // upon subscription, step verifier will request 2, so we need 2 + maxBufferSize + 1 to trigger
                    // overflow error
                    domainBuilder.topicMessages(maxBufferSize + 3, future).blockLast();
                })
                .expectNext(1L, 2L)
                .thenAwait(Duration.ofMillis(500L))
                .thenRequest(Long.MAX_VALUE)
                .expectNextCount(maxBufferSize)
                .expectErrorMatches(Exceptions::isOverflow)
                .verify(Duration.ofMillis(600L));
    }
}
