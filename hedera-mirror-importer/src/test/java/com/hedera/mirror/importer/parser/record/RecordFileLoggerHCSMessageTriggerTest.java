package com.hedera.mirror.importer.parser.record;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.annotation.Resource;
import javax.sql.DataSource;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import com.hedera.mirror.importer.IntegrationTest;

@Log4j2
public class RecordFileLoggerHCSMessageTriggerTest extends IntegrationTest {
    @Resource
    private DataSource dataSource;

    @Test
    void triggerCausesNotification() throws Exception {
        Connection conn = null;
        PGConnection pgConn = null;
        Statement listenStatement = null;
        PreparedStatement sqlInsertTopicData = null;
        Statement triggerStatement = null;

        try {

            conn = dataSource.getConnection();

            // obtain pg connection
            pgConn = conn.unwrap(org.postgresql.PGConnection.class);

            // setup listener
            listenStatement = conn.createStatement();
            listenStatement.execute("LISTEN t_topic_message");

            // verify no notifications present yet
            PGNotification[] notifications = pgConn.getNotifications();
            assertThat(notifications == null || notifications.length == 0).isTrue();

            // insert new hcs message
            triggerStatement = conn.createStatement();
            long refConsensusTimeStamp = 1568491241176959000L;
            long realmNum = 1L;
            long topicNUm = 7L;
            byte[] message = "Verify hcs message triggers notification out".getBytes();
            byte[] runHash = new byte[] {(byte) 0x4D};
            byte[] seqNum = new byte[] {(byte) 0x5A};

            String triggerSQL = "INSERT INTO t_topic_message"
                    + "(consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                    + " VALUES (" + refConsensusTimeStamp + ", 0, 7, decode('DEADBEEF', 'hex'), decode('DEADBEEF', " +
                    "'hex'), " +
                    "decode('DEADBEEF', 'hex'))";
//            triggerStatement.executeUpdate(triggerSQL);

            sqlInsertTopicData = conn.prepareStatement("INSERT INTO t_topic_message"
                    + "(consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                    + " VALUES (?, ?, ?, ?, ?, ? )");

            sqlInsertTopicData.setLong(1, refConsensusTimeStamp);
            sqlInsertTopicData.setLong(2, realmNum);
            sqlInsertTopicData.setLong(3, topicNUm);
            sqlInsertTopicData.setBytes(4, message);
            sqlInsertTopicData.setBytes(5, runHash);
            sqlInsertTopicData.setBytes(6, seqNum);
            sqlInsertTopicData.executeUpdate();

            // check for new notifications. Timeout after 5 secs
            notifications = pgConn.getNotifications(5000);
            assertThat(notifications).isNotNull();
            assertThat(notifications.length).isEqualTo(1);

            String notificationName = notifications[0].getName();
            assertThat(notificationName).isEqualTo("t_topic_message");

            String hcsMessage = notifications[0].getParameter();
            assertThat(hcsMessage).contains("\"consensus_timestamp\":" + Long.toString(refConsensusTimeStamp));
            assertThat(hcsMessage).contains("\"realm_num\":" + Long.toString(realmNum));
            assertThat(hcsMessage).contains("\"topic_num\":" + Long.toString(topicNUm));
            assertThat(hcsMessage).contains(
                    "\"message\":\"" +
                            "\\\\x56657269667920686373206d657373616765207472696767657273206e6f74696669636174696f6e206f7574");
            assertThat(hcsMessage).contains("\"running_hash\":\"\\\\x4d\"");
            assertThat(hcsMessage).contains("\"sequence_number\":\"\\\\x5a\"");
        } catch (Exception ex) {
            log.error(ex.toString());
            throw ex;
        } finally {
            // cleanup connections and statements
            if (conn != null) {
                conn.close();
            }

            if (listenStatement != null && !listenStatement.isClosed()) {
                listenStatement.close();
            }

            if (sqlInsertTopicData != null && !sqlInsertTopicData.isClosed()) {
                sqlInsertTopicData.close();
            }

            if (triggerStatement != null && !triggerStatement.isClosed()) {
                triggerStatement.close();
            }
        }
    }
}

