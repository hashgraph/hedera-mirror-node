package com.hedera.mirror.importer.parser.record.entity.notify;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchEvent;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class NotifyingEntityListenerTest extends IntegrationTest {

    private final NotifyingEntityListener entityListener;
    private final NotifyProperties notifyProperties;
    private final DataSource dataSource;

    @Test
    void isEnabled() {
        notifyProperties.setEnabled(false);
        assertThat(entityListener.isEnabled()).isFalse();

        notifyProperties.setEnabled(true);
        assertThat(entityListener.isEnabled()).isTrue();
    }

    @Test
    void onTopicMessageNotify() throws Exception {
        // given
        TopicMessage topicMessage = topicMessage();
        String json = NotifyingEntityListener.OBJECT_MAPPER.writeValueAsString(topicMessage);

        try (PgConnection connection = dataSource.getConnection().unwrap(PgConnection.class)) {
            connection.execSQLUpdate("listen topic_message");

            // when
            entityListener.onTopicMessage(topicMessage);
            entityListener.onBatch(new EntityBatchEvent(this));
            PGNotification[] notifications = connection.getNotifications(500);

            // then
            assertEquals(1, notifications.length);
            assertThat(notifications)
                    .extracting(PGNotification::getParameter)
                    .first()
                    .isEqualTo(json);
        }
    }

    @Test
    void onTopicMessageNotifyPayloadTooLong() throws Exception {
        // given
        TopicMessage topicMessage = topicMessage();
        topicMessage.setMessage(RandomUtils.nextBytes(5824)); // Just exceeds 8000B

        try (PgConnection connection = dataSource.getConnection().unwrap(PgConnection.class)) {
            connection.execSQLUpdate("listen topic_message");

            // when
            entityListener.onTopicMessage(topicMessage);
            entityListener.onBatch(new EntityBatchEvent(this));
            PGNotification[] notifications = connection.getNotifications(500);

            // then
            assertThat(notifications).isNull();
        }
    }

    private TopicMessage topicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1L);
        topicMessage.setMessage("test message".getBytes());
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityTypeEnum.ACCOUNT));
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicNum(1001);
        topicMessage.setValidStartTimestamp(4L);
        return topicMessage;
    }
}
