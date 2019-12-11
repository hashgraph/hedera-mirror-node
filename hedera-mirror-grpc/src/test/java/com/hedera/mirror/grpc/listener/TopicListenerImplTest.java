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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class TopicListenerImplTest extends GrpcIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private TopicListener topicListener;
//    private DatabaseClient databaseClient;
//
//    @PostConstruct
//    void setup() {
//        databaseClient.delete().from(TopicMessage.class).fetch().rowsUpdated().block();
//    }

    @Test
    void verifyListenerReceivesMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.now().minusSeconds(60))
                .build();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(i -> {
                    var message = domainBuilder.topicMessage(t -> {
                    });
                    return message;
                }
        )
                .take(10)
                .subscribe();

        Flux<TopicMessage> receivedTopicMessages = topicListener.listen(filter);

        StepVerifier.create(receivedTopicMessages)
                .expectNextCount(10)
                .thenCancel()
                .verify();
    }

    @Disabled("Fails when whole test class suite is run")
    @Test
    void verifyListenerReceivesMessagesForSingleTopic() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(2L)
                .startTime(Instant.now().minusSeconds(60))
                .build();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(i -> {
                    return domainBuilder.topicMessage(t -> t.topicNum(i.intValue()));
                }
        )
                .take(10)
                .subscribe();

        Flux<TopicMessage> receivedTopicMessages = topicListener.listen(filter);

        StepVerifier.create(receivedTopicMessages)
                .assertNext(topic -> Assertions.assertThat(topic.getTopicNum()).isEqualTo(2))
                .thenCancel()
                .verify();
    }

    @Disabled("Intermittently fails")
    @Test
    void verifyListenerReceivesMultipleMessagesForSingleTopic() {
        int desiredTopicNum = 2;
        int topicNumFactor = 5; // use this to ensure sequence numbers match expected as they may come out of order
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(desiredTopicNum)
                .startTime(Instant.now().minusSeconds(60))
                .build();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(i -> {
            return domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(i));
        })
                .take(2)
                .doOnNext(System.out::println)
                .subscribe();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(j -> {
            return domainBuilder
                    .topicMessage(t -> t.topicNum(desiredTopicNum).sequenceNumber((j + 1) * topicNumFactor));
        })
                .take(3)
                .doOnNext(System.out::println)
                .subscribe();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(k -> {
            return domainBuilder.topicMessage(t -> t.topicNum(3).sequenceNumber(k));
        })
                .take(2)
                .doOnNext(System.out::println)
                .subscribe();

        Flux<TopicMessage> receivedTopicMessages = topicListener.listen(filter);

        StepVerifier.create(receivedTopicMessages)
                .expectNextCount(3)
                .expectNextMatches(u -> u.getTopicNum() == desiredTopicNum && u
                        .getSequenceNumber() % topicNumFactor == 0)
                .expectNextMatches(u -> u.getTopicNum() == desiredTopicNum && u
                        .getSequenceNumber() % topicNumFactor == 0)
                .expectNextMatches(u -> u.getTopicNum() == desiredTopicNum && u
                        .getSequenceNumber() % topicNumFactor == 0)

                .thenCancel()
                .verify();
    }
}
