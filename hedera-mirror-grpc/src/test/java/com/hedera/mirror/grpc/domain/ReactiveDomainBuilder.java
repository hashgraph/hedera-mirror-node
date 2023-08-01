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

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.grpc.repository.EntityRepository;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
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
public class ReactiveDomainBuilder {

    private final long now = DomainUtils.now();
    private final EntityRepository entityRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final DomainBuilder domainBuilder;
    private long sequenceNumber = 0L;

    @PostConstruct
    void setup() {
        entityRepository.deleteAll();
        topicMessageRepository.deleteAll();
    }

    public Mono<Entity> entity() {
        return entity(e -> {});
    }

    public Mono<Entity> entity(Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        Entity entity = domainBuilder
                .entity()
                .customize(e -> e.id(100L).type(EntityType.TOPIC))
                .customize(customizer)
                .get();
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
        TopicMessage topicMessage = domainBuilder
                .topicMessage()
                .customize(e -> e.consensusTimestamp(now + sequenceNumber)
                        .sequenceNumber(++sequenceNumber)
                        .topicId(EntityId.of(100L, EntityType.TOPIC)))
                .customize(customizer)
                .get();
        return insert(topicMessage).thenReturn(topicMessage);
    }

    public Flux<TopicMessage> topicMessages(long count, long startTime) {
        List<Publisher<TopicMessage>> publishers = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            long consensusTimestamp = startTime + i;
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
