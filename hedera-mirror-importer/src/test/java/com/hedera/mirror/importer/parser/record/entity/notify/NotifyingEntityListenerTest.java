package com.hedera.mirror.importer.parser.record.entity.notify;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import java.util.ArrayList;
import java.util.Collection;
import javax.sql.DataSource;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.JdbcUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListenerTest;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@EnabledIfV1
class NotifyingEntityListenerTest extends BatchEntityListenerTest {

    private final DataSource dataSource;

    @Autowired
    public NotifyingEntityListenerTest(NotifyingEntityListener entityListener, NotifyProperties properties,
                                       DataSource dataSource) {
        super(entityListener, properties);
        this.dataSource = dataSource;
    }

    @Test
    void onTopicMessagePayloadTooLong() throws InterruptedException {
        // given
        TopicMessage topicMessage = topicMessage();
        topicMessage.setMessage(RandomUtils.nextBytes(5824)); // Just exceeds 8000B
        Flux<TopicMessage> topicMessages = subscribe(topicMessage.getTopicNum());

        // when
        entityListener.onTopicMessage(topicMessage);
        entityListener.onSave(new EntityBatchSaveEvent(this));

        // then
        topicMessages.as(StepVerifier::create)
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Override
    protected Flux<TopicMessage> subscribe(long topicNum) {
        try {
            PgConnection connection = dataSource.getConnection().unwrap(PgConnection.class);
            connection.execSQLUpdate("listen topic_message");
            return Flux.defer(() -> getNotifications(connection))
                    .repeat()
                    .timeout(Duration.ofSeconds(2))
                    .doAfterTerminate(() -> JdbcUtils.closeConnection(connection));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Flux<TopicMessage> getNotifications(PgConnection pgConnection) {
        Collection<TopicMessage> topicMessages = new ArrayList<>();
        try {
            PGNotification[] notifications = pgConnection.getNotifications(100);
            if (notifications != null) {
                for (PGNotification pgNotification : notifications) {
                    TopicMessage topicMessage = NotifyingEntityListener.OBJECT_MAPPER
                            .readValue(pgNotification.getParameter(), TopicMessage.class);
                    topicMessages.add(topicMessage);
                }
            }
            return Flux.fromIterable(topicMessages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
