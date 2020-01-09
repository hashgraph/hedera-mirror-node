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
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.spi.ConnectionFactory;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

@Named
@Log4j2
public class NotifyingTopicListener implements TopicListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

    private final Flux<TopicMessage> topicMessages;

    public NotifyingTopicListener(ConnectionFactory connectionFactory) {
        ConnectionPool connectionPool = (ConnectionPool) connectionFactory;
        PostgresqlConnectionFactory postgresqlConnectionFactory = (PostgresqlConnectionFactory) connectionPool
                .unwrap();

        topicMessages = postgresqlConnectionFactory.create().flatMapMany(it -> {
            return it.createStatement("LISTEN topic_message")
                    .execute()
                    .thenMany(it.getNotifications())
                    .map(this::toTopicMessage);
        })
                .share()
                .name("notify")
                .metrics();
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return topicMessages.filter(t -> filterMessage(t, filter))
                .doOnSubscribe(s -> log.info("Listening for messages: {}", filter));
    }

    private TopicMessage toTopicMessage(Notification notification) {
        try {
            return OBJECT_MAPPER.readValue(notification.getParameter(), TopicMessage.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return filter.getRealmNum() == message.getRealmNum() &&
                filter.getTopicNum() == message.getTopicNum() &&
                !filter.getStartTime().isAfter(message.getConsensusTimestamp());
    }
}
