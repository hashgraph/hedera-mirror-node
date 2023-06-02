/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.grpc.domain;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.repository.EntityRepository;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@Named("grpcDomainBuilder")
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    private final Instant now = Instant.now();
    private final EntityRepository entityRepository;
    private final TopicMessageRepository topicMessageRepository;
    private long sequenceNumber = 0L;

    @PostConstruct
    void setup() {
        entityRepository.deleteAll();
        topicMessageRepository.deleteAll();
    }

    public Mono<Entity> entity() {
        return entity(e -> {});
    }

    public Mono<Entity> entity(Consumer<Entity.EntityBuilder> customizer) {
        Entity.EntityBuilder builder = Entity.builder()
                .num(0L)
                .realm(0L)
                .shard(0L)
                .id(100L)
                .timestampRange(Range.atLeast(0L))
                .type(EntityType.TOPIC);

        customizer.accept(builder);
        Entity entity = builder.build();
        return insert(entity).thenReturn(entity);
    }

    public Mono<TopicMessage> topicMessage() {
        return topicMessage(t -> {});
    }

    /**
     * Generates a Topic Message with sane defaults and inserts it into the database. The consensusTimestamp and
     * sequenceNumber auto-increase by one on each call.
     *
     * @param customizer allows one to customize the TopicMessage before it is inserted
     * @return the inserted TopicMessage
     */
    public Mono<TopicMessage> topicMessage(Consumer<TopicMessage.TopicMessageBuilder> customizer) {

        TopicMessage.TopicMessageBuilder builder = TopicMessage.builder()
                .consensusTimestamp(now.plus(sequenceNumber, ChronoUnit.NANOS))
                .initialTransactionId(TransactionID.newBuilder()
                        .setAccountID(AccountID.newBuilder().setAccountNum(10).build())
                        .setTransactionValidStart(Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano()))
                        .setNonce(0)
                        .setScheduled(false)
                        .build()
                        .toByteArray())
                .message(new byte[] {0, 1, 2})
                .payerAccountId(10L)
                .runningHash(new byte[] {3, 4, 5})
                .sequenceNumber(++sequenceNumber)
                .topicId(100)
                .runningHashVersion(2);

        customizer.accept(builder);
        TopicMessage topicMessage = builder.build();
        return insert(topicMessage).thenReturn(topicMessage);
    }

    public Flux<TopicMessage> topicMessages(long count, Instant startTime) {
        List<Publisher<TopicMessage>> publishers = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            Instant consensusTimestamp = startTime.plusNanos(i);
            publishers.add(topicMessage(t -> t.consensusTimestamp(consensusTimestamp)));
        }
        return Flux.concat(publishers);
    }

    private Mono<Entity> insert(Entity entity) {
        return Mono.defer(() -> Mono.just(entityRepository.save(entity))).doOnNext(t -> log.trace("Inserted: {}", t));
    }

    private Mono<TopicMessage> insert(TopicMessage topicMessage) {
        return Mono.defer(() -> Mono.just(topicMessageRepository.save(topicMessage)))
                .doOnNext(t -> log.trace("Inserted: {}", t));
    }
}
