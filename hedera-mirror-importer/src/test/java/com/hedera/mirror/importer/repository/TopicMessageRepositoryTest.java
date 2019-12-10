package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import java.sql.Connection;
import java.sql.Statement;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.test.context.transaction.TestTransaction;

import com.hedera.mirror.importer.domain.TopicMessage;

public class TopicMessageRepositoryTest extends AbstractRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

    @Resource
    protected TopicMessageRepository topicMessageRepository;

    @Resource
    private DataSource dataSource;

    @Test
    void triggerCausesNotification() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement listenStatement = conn.createStatement()) {

            // obtain pg connection
            PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);

            // setup listener
            listenStatement.execute("LISTEN topic_message");

            // verify no notifications present yet
            PGNotification[] notifications = pgConn.getNotifications();
            assertThat(notifications).isNullOrEmpty();

            // verify notification can be picked up
            listenStatement.execute("NOTIFY topic_message");
            notifications = pgConn.getNotifications(5000);
            assertThat(notifications).isNotNull().hasSize(1);

            // insert new hcs topic message
            TopicMessage topicMessage = new TopicMessage();
            topicMessage.setConsensusTimestamp(1568491241176959000L);
            topicMessage.setRealmNum(1);
            topicMessage.setTopicNum(7);
            topicMessage.setMessage("Verify hcs message triggers notification out".getBytes());
            topicMessage.setRunningHash(new byte[] {(byte) 0x4D});
            topicMessage.setSequenceNumber(3L);

            // the @Transactional annotation on AbstractRepositoryTest means the session isn't auto flushed per command
            // to ensure that db is actually updated we make use of the TestTransaction class to ensure the
            // topicMessageRepository.save() is committed
            TestTransaction.flagForCommit();
            topicMessage = topicMessageRepository.save(topicMessage);
            TestTransaction.end();

            // verify repository db was updated
            assertThat(topicMessageRepository.findById(topicMessage.getConsensusTimestamp()))
                    .get()
                    .isEqualTo(topicMessage);

            // check for new notifications. Timeout after 5 secs
            notifications = pgConn.getNotifications(5000);
            assertThat(notifications).isNotNull()
                    .hasSize(1)
                    .extracting("name")
                    .containsExactly("topic_message");

            String notificationMessage = notifications[0].getParameter();
            assertThat(OBJECT_MAPPER.readValue(notificationMessage, TopicMessage.class)).isEqualTo(topicMessage);
        }
    }
}
