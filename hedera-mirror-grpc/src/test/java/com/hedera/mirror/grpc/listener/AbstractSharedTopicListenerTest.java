package com.hedera.mirror.grpc.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Vector;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public abstract class AbstractSharedTopicListenerTest extends AbstractTopicListenerTest {

    @Test
    @DisplayName("slow subscriber receives overflow exception and normal subscriber is not affected")
    void slowSubscriberOverflowException() {
        int maxBufferSize = 16;
        listenerProperties.setMaxBufferSize(maxBufferSize);

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        // create a normal subscriber to keep the shared flux open
        Vector<Long> sequenceNumbers = new Vector<>();
        var subscription = getTopicListener().listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .subscribe(sequenceNumbers::add);

        // the slow subscriber
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
                .thenAwait(Duration.ofMillis(500L)) // stall to overrun backpressure buffer
                .thenRequest(Long.MAX_VALUE)
                .expectNextSequence(LongStream.range(3, maxBufferSize + 3).boxed().collect(Collectors.toList()))
                .expectErrorMatches(Exceptions::isOverflow)
                .verify(Duration.ofMillis(600L));

        assertThat(subscription.isDisposed()).isFalse();
        subscription.dispose();
        assertThat(sequenceNumbers).isEqualTo(LongStream.range(1, maxBufferSize + 4).boxed().collect(Collectors.toList()));
    }

    @Test
    @DisplayName("slow subscriber causes buffer overflow then timeout exception")
    void slowSubscribeOverflowThenTimeout() {
        int maxBufferSize = 16;
        listenerProperties.setMaxBufferSize(maxBufferSize);
        listenerProperties.setBufferTimeout(550L);

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        // the slow subscriber
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
                .thenAwait(Duration.ofMillis(500L)) // stall to overrun backpressure buffer
                .thenRequest(2)
                .expectNext(3L, 4L)
                .thenAwait(Duration.ofMillis(200L)) // timeout begins to count down when overflow happens
                .expectError(TimeoutException.class)
                .verify(Duration.ofMillis(800L));
    }
}
