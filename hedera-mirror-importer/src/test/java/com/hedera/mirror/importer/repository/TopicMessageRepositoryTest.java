package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.annotation.Resource;
import javax.sql.DataSource;

import lombok.extern.log4j.Log4j2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;

@Log4j2
public class TopicMessageRepositoryTest extends AbstractRepositoryTest {
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
            listenStatement.execute("LISTEN topic_message");

            // verify no notifications present yet
            PGNotification[] notifications = pgConn.getNotifications();
            assertThat(notifications == null || notifications.length == 0).isTrue();

            // verify notification can be picked up
            listenStatement.execute("NOTIFY topic_message");
            notifications = pgConn.getNotifications(5000);
            assertThat(notifications).isNotNull();
            assertThat(notifications.length).isEqualTo(1);

            // insert new hcs message
            triggerStatement = conn.createStatement();
            long refConsensusTimeStamp = 1568491241176959000L;
            long realmNum = 1L;
            long topicNUm = 7L;
            byte[] message = "Verify hcs message triggers notification out".getBytes();
            byte[] runHash = new byte[] {(byte) 0x4D};
            long seqNum = 3L;

            TopicMessage topicMessageResult = new TopicMessage();
            topicMessageResult.setConsensusTimestamp(refConsensusTimeStamp);
            topicMessageResult.setRealmNum(realmNum);
            topicMessageResult.setTopicNum(topicNUm);
            topicMessageResult.setMessage(message);
            topicMessageResult.setRunningHash(runHash);
            topicMessageResult.setSequenceNumber(seqNum);
            topicMessageResult = topicMessageRepository.save(topicMessageResult);

            // check for new notifications. Timeout after 5 secs
            notifications = pgConn.getNotifications(35000);
            assertThat(notifications).isNotNull();
            assertThat(notifications.length).isEqualTo(1);

            String notificationName = notifications[0].getName();
            assertThat(notificationName).isEqualTo("topic_message");

            String hcsMessage = notifications[0].getParameter();
            assertThat(hcsMessage)
                    .contains("\"consensus_timestamp\":" + topicMessageResult.getConsensusTimestamp());
            assertThat(hcsMessage).contains("\"realm_num\":" + topicMessageResult.getRealmNum());
            assertThat(hcsMessage).contains("\"topic_num\":" + topicMessageResult.getTopicNum());
            assertThat(hcsMessage).contains(
                    "\"message\":\"" +
                            "\\\\x56657269667920686373206d657373616765207472696767657273206e6f74696669636174696f6e206f7574");
            assertThat(hcsMessage).contains("\"running_hash\":\"\\\\x4d\"");
            assertThat(hcsMessage)
                    .contains("\"sequence_number\":" + topicMessageResult.getSequenceNumber());
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
