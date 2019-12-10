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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import com.hedera.mirror.grpc.domain.TopicMessage;

import com.hedera.mirror.grpc.domain.TopicMessageFilter;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import io.r2dbc.postgresql.api.Notification;
import reactor.core.publisher.Mono;

@Component
@Log4j2
public class TopicListenerImpl implements TopicListener {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

    private final Flux<TopicMessage> topicMessages;

    public TopicListenerImpl(ConnectionFactory connectionFactory) {
        ConnectionPool connectionPool = (ConnectionPool) connectionFactory;
        PostgresqlConnectionFactory postgressqlConnectionFactory = (PostgresqlConnectionFactory) connectionPool.unwrap();
        topicMessages = postgressqlConnectionFactory.create().flatMapMany(it -> {
            return it.createStatement("LISTEN topic_message")
                    .execute()
                    .thenMany(it.getNotifications())
                    .map(this::toTopicMessage);
        }).share();
    }

    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return topicMessages.filterWhen(t -> asyncFilterMessage(t, filter));
    }

    private TopicMessage toTopicMessage(Notification notification) {
        String parameter = notification.getParameter();
        TopicMessage topicMessage = null;
        try {
            topicMessage = OBJECT_MAPPER.readValue(parameter, TopicMessage.class);
            log.debug("Listener: {}", topicMessage.getSequenceNumber());
        }
        catch (Exception ex) {
            log.error("OBJECT_MAPPER failed to parse TopicMessage type from string: {}", parameter, ex);
        }

        return topicMessage;
    }

    private Mono<Boolean> asyncFilterMessage(TopicMessage message, TopicMessageFilter filter) {

        if (message.getRealmNum() != filter.getRealmNum()) {
            return Mono.just(false);
        }

        if (message.getTopicNum() != filter.getTopicNum()) {
            return Mono.just(false);
        }

        if (filter.getStartTime().isBefore(message.getConsensusTimestamp())) {
            return Mono.just(false);
        }

        if (filter.getEndTime() != null && filter.getEndTime().isBefore(message.getConsensusTimestamp())) {
            return Mono.just(false);
        }

        return Mono.just(true);
    }
}
