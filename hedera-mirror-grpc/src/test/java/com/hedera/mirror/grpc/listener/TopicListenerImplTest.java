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

import com.hedera.mirror.grpc.GrpcIntegrationTest;

import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

import com.hedera.mirror.grpc.repository.TopicMessageRepository;

import io.r2dbc.spi.ConnectionFactory;
import javafx.util.Pair;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import javax.annotation.Resource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class TopicListenerImplTest extends GrpcIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private TopicListener topicListener;

    @Resource
    private ConnectionFactory connectionFactory;

     @Resource
    private TopicMessageRepository topicMessageRepository;

    @Test
    void verifyListenerReceivesMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.now())
                .build();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(i -> {
                    var message = domainBuilder.topicMessageAsync(t -> {});
                    return message;
                }
        )
                .doOnNext(s -> System.out.println("*****Emitted a message from generator!!! : " + s))
                .take(10)
                .doOnSubscribe(s -> System.out.println("*****Generator Subscribed!!!"))
                .subscribe();

        Flux<TopicMessage> receivedTopicMessages = topicListener.listen(filter)
                .doOnNext(s -> {System.out.println("*****Heard a message from notifier!!! : " + s);});

        StepVerifier.create(receivedTopicMessages)
                .expectNextCount(10)
                .thenCancel()
                .verify();
    }

    @Test
    void verifyListenerReceivesMessagesForSingleTopic() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(2L)
                .startTime(Instant.now())
                .build();

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(i -> {
                    return domainBuilder.topicMessageAsync(t -> t.topicNum(i.intValue()));
                }
        )
            .doOnNext(s -> System.out.println("*****Emitted a message from generator!!! : " + s))
            .take(10)
            .doOnSubscribe(s -> System.out.println("*****Generator Subscribed!!!"))
            .subscribe();

        Flux<TopicMessage> receivedTopicMessages = topicListener.listen(filter)
                .doOnNext(s -> {System.out.println("*****Heard a message from notifier!!! : " + s);});

        StepVerifier.create(receivedTopicMessages)
                .assertNext(topic -> Assertions.assertThat(topic.getTopicNum()).isEqualTo(2))
                .thenCancel()
                .verify();
    }

    @Test
    void verifyListenerReceivesMultipleMessagesForSingleTopic() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(2L)
                .startTime(Instant.now())
                .build();


        Mono<TopicMessage> topicMessage1 = domainBuilder.topicMessageAsync(t -> t.topicNum(1).sequenceNumber(1));
        Mono<TopicMessage> topicMessage2 = domainBuilder.topicMessageAsync(t -> t.topicNum(1).sequenceNumber(2));
        Mono<TopicMessage> topicMessage3 = domainBuilder.topicMessageAsync(t -> t.topicNum(1).sequenceNumber(3));

//        Flux.just(
//                domainBuilder.topicMessageAsync(t -> t.topicNum(0).sequenceNumber(1)),
//                domainBuilder.topicMessageAsync(t -> t.topicNum(0).sequenceNumber(1)),
//                topicMessage1,
//                topicMessage2,
//                topicMessage3,
//                domainBuilder.topicMessageAsync(t -> t.topicNum(2).sequenceNumber(1)))
//            .delayElements(Duration.ofMillis(5), Schedulers.single())
//            .doOnNext(s -> System.out.println("*****Emitted a message from generator!!! : " + s))
//            .take(10)
//            .doOnSubscribe(s -> System.out.println("*****Generator Subscribed!!!"))
//            .subscribe();

        var pair1 = new Pair<Integer, Integer>(1, 1);
        var pair2 = new Pair<Integer, Integer>(1, 2);
        var pair3 = new Pair<Integer, Integer>(1, 3);
        HashMap<Integer, Pair<Integer, Integer>> messageMappings = new HashMap<>();
        messageMappings.put(0, new Pair<Integer, Integer>(0, 1));
        messageMappings.put(1, new Pair<Integer, Integer>(0, 2));
        messageMappings.put(2, pair1);
        messageMappings.put(3, pair2);
        messageMappings.put(4, pair3);
        messageMappings.put(5, new Pair<Integer, Integer>(2, 1));

        Flux.interval(Duration.ofMillis(5), Schedulers.single()).flatMap(i -> {
                    var key = Math.toIntExact(i);
                    System.out.println("*****Getting element : " + key);
                    var pair = messageMappings.get(i);
                    return domainBuilder.topicMessageAsync(t -> t.topicNum(pair.getKey()).sequenceNumber(pair.getValue()));
                }
        )
                .doOnNext(s -> System.out.println("*****Emitted a message from generator!!! : " + s))
                .take(messageMappings.size())
                .doOnSubscribe(s -> System.out.println("*****Generator Subscribed!!!"))
                .subscribe();

        Flux<TopicMessage> receivedTopicMessages = topicListener.listen(filter)
                .doOnNext(s -> {System.out.println("*****Heard a message from notifier!!! : " + s);});

        StepVerifier.create(receivedTopicMessages)
                .expectNextMatches(u -> u.getTopicNum() == pair1.getKey() && u.getSequenceNumber() == pair1.getValue())
                .expectNextMatches(u -> u.getTopicNum() == pair2.getKey() && u.getSequenceNumber() == pair2.getValue())
                .expectNextMatches(u -> u.getTopicNum() == pair3.getKey() && u.getSequenceNumber() == pair3.getValue())
//                .expectNext(topicMessage1.block())
//                .expectNext(topicMessage2.block())
//                .expectNext(topicMessage3.block())
                .thenCancel()
                .verify();
    }
}
