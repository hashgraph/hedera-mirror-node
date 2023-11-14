/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.notify;

import static com.hedera.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListenerTest;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import javax.sql.DataSource;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class NotifyingEntityListenerTest extends BatchEntityListenerTest {

    private final DataSource dataSource;

    @Autowired
    public NotifyingEntityListenerTest(
            NotifyingEntityListener entityListener, NotifyProperties properties, DataSource dataSource) {
        super(entityListener, properties);
        this.dataSource = dataSource;
    }

    @Test
    void onTopicMessagePayloadTooLong() throws InterruptedException {
        // given
        TopicMessage topicMessage = domainBuilder.topicMessage().get();
        topicMessage.setMessage(RandomUtils.nextBytes(5824)); // Just exceeds 8000B
        var topicMessages = subscribe(topicMessage.getTopicId());

        // when
        entityListener.onTopicMessage(topicMessage);
        entityListener.onSave(new EntityBatchSaveEvent(this));

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Override
    protected Flux<TopicMessage> subscribe(EntityId topicId) {
        try {
            var connection = dataSource.getConnection();
            var pgConnection = connection.unwrap(PgConnection.class);
            pgConnection.execSQLUpdate("listen topic_message");
            return Flux.defer(() -> getNotifications(pgConnection, topicId))
                    .repeat()
                    .subscribeOn(Schedulers.parallel())
                    .timeout(Duration.ofSeconds(3))
                    .doFinally(s -> {
                        try {
                            connection.close();
                        } catch (SQLException e) {
                            // Ignore
                        }
                    })
                    .doOnSubscribe(s -> log.info("Subscribed to {}", topicId));
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private Flux<TopicMessage> getNotifications(PgConnection pgConnection, EntityId topicId) {
        try {
            var topicMessages = new ArrayList<TopicMessage>();
            var notifications = pgConnection.getNotifications(100);

            if (notifications != null) {
                for (var pgNotification : notifications) {
                    var topicMessage = OBJECT_MAPPER.readValue(pgNotification.getParameter(), TopicMessage.class);
                    if (topicId.equals(topicMessage.getTopicId())) {
                        topicMessages.add(topicMessage);
                    }
                }
            }
            return Flux.fromIterable(topicMessages);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }
}
