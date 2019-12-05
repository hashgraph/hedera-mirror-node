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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import javax.annotation.Resource;
import javax.sql.DataSource;

import lombok.extern.log4j.Log4j2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.test.context.transaction.TestTransaction;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;

@Log4j2
public class TopicMessageRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected TopicMessageRepository topicMessageRepository;
    @Resource
    private DataSource dataSource;

    @Test
    void insert() {

        RecordFile recordfile = insertRecordFile();
        Entities entity = insertAccountEntity();
        Transaction transaction = insertTransaction(recordfile.getId(), entity.getId(), "CONTRACTCALL");

        TopicMessage topicMessageResult = new TopicMessage();
        topicMessageResult.setConsensusTimestamp(transaction.getConsensusNs());
        topicMessageResult.setRealmNum(1L);
        topicMessageResult.setTopicNum(2L);
        topicMessageResult.setMessage("TopicMessage".getBytes());
        topicMessageResult.setRunningHash("RunningHash".getBytes());
        topicMessageResult.setSequenceNumber(99L);
        topicMessageResult = topicMessageRepository.save(topicMessageResult);

        Assertions.assertThat(topicMessageRepository.findById(transaction.getConsensusNs()).get())
                .isNotNull()
                .isEqualTo(topicMessageResult);
    }

    @Test
    void triggerCausesNotification() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement listenStatement = conn.createStatement()) {

            // obtain pg connection
            PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);

            // setup listener
            listenStatement.execute("LISTEN topic_message");

            // verify no notifications present yet
            PGNotification[] notifications = pgConn.getNotifications();
            assertThat(notifications == null || notifications.length == 0).isTrue();

            // verify notification can be picked up
            listenStatement.execute("NOTIFY topic_message");
            notifications = pgConn.getNotifications(5000);
            assertThat(notifications).isNotNull().hasSize(1);

            // insert new hcs topic message
            long refConsensusTimeStamp = 1568491241176959000L;

            TopicMessage topicMessageResult = new TopicMessage();
            topicMessageResult.setConsensusTimestamp(1568491241176959000L);
            topicMessageResult.setRealmNum(1L);
            topicMessageResult.setTopicNum(7L);
            topicMessageResult.setMessage("Verify hcs message triggers notification out".getBytes());
            topicMessageResult.setRunningHash(new byte[] {(byte) 0x4D});
            topicMessageResult.setSequenceNumber(3L);

            // the @Transactional annotation on AbstractRepositoryTest means the session isn't auto flushed per command
            // to ensure that db is actually updated we make use of the TestTransaction class to ensure the
            // topicMessageRepository.save() is committed
            TestTransaction.flagForCommit();
            topicMessageResult = topicMessageRepository.save(topicMessageResult);
            TestTransaction.end();

            // verify repository db was updated
            Assertions.assertThat(topicMessageRepository.findById(refConsensusTimeStamp).get())
                    .isNotNull()
                    .isEqualTo(topicMessageResult);

            // check for new notifications. Timeout after 5 secs
            notifications = pgConn.getNotifications(5000);
            assertThat(notifications).isNotNull();
            assertThat(notifications.length).isEqualTo(1);

            String notificationName = notifications[0].getName();
            assertThat(notificationName).isEqualTo("topic_message");

            String hcsMessage = notifications[0].getParameter();
            assertThatJson(hcsMessage).isObject()
                    .containsEntry("consensus_timestamp", BigDecimal
                            .valueOf((topicMessageResult.getConsensusTimestamp())))
                    .containsEntry("realm_num", BigDecimal
                            .valueOf((topicMessageResult.getRealmNum())))
                    .containsEntry("topic_num", BigDecimal
                            .valueOf((topicMessageResult.getTopicNum())))
                    .containsEntry("message",
                            "\\x56657269667920686373206d657373616765207472696767657273206e6f74696669636174696f6e206f7574")
                    .containsEntry("running_hash", "\\x4d")
                    .containsEntry("sequence_number", BigDecimal
                            .valueOf((topicMessageResult.getSequenceNumber())));
        }
    }
}
