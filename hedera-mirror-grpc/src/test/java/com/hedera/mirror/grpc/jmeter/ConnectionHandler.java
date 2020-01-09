package com.hedera.mirror.grpc.jmeter;

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

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import com.hedera.mirror.grpc.converter.InstantToLongConverter;

@Log4j2
public class ConnectionHandler {
    private final InstantToLongConverter itlc = new InstantToLongConverter();
    private final PostgresqlConnection connection;
    private final String host;
    private final int port;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    public ConnectionHandler(String host, int port, String dbName, String dbUser, String dbPassword) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;

        connection = getConnection();
    }

    public PostgresqlConnectionFactory getConnectionFactory() {
        log.trace("Initialize connectionFactory");
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(host)
                        .port(port)
                        .username(dbUser)
                        .password(dbPassword)
                        .database(dbName)
                        .build());

        return connectionFactory;
    }

    public DatabaseClient getDatabaseClient() {
        log.trace("Obtain databaseClient");
        PostgresqlConnectionFactory connectionFactory = getConnectionFactory();

        return DatabaseClient.builder()
                .connectionFactory(connectionFactory)
                .dataAccessStrategy(new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE))
                .build();
    }

    public PostgresqlConnection getConnection() {
        log.info("Obtain PostgresqlConnection");
        PostgresqlConnectionFactory connectionFactory = getConnectionFactory();

        return connectionFactory.create().block();
    }

    public void insertTopicMessage(int newTopicsMessageCount, long topicNum, Instant startTime, long seqStart) {

        if (newTopicsMessageCount == 0) {
            // no messages to create, abort and db logic
            return;
        }

        long nextSequenceNum = seqStart == -1 ? getNextAvailableSequenceNumber(topicNum) : seqStart;
        log.info("Insert {} topic messages starting from seqNum {}", newTopicsMessageCount, nextSequenceNum);
        for (int i = 0; i < newTopicsMessageCount; i++) {
            long sequenceNum = nextSequenceNum + i;
            Instant temp = startTime.plus(sequenceNum, ChronoUnit.NANOS);
            Long instalong = itlc.convert(temp);

            String topicMessageInsertSql = "insert into topic_message"
                    + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                    + " values ($1, $2, $3, $4, $5, $6)";

            PostgresqlStatement statement = connection.createStatement(topicMessageInsertSql)
                    .bind("$1", instalong)
                    .bind("$2", 0)
                    .bind("$3", topicNum)
                    .bind("$4", new byte[] {22, 33, 44})
                    .bind("$5", new byte[] {55, 66, 77})
                    .bind("$6", sequenceNum);
            statement.execute().blockLast();

            log.trace("Stored TopicMessage {}, Time: {}, count: {}, seq : {}", topicNum,
                    instalong, i, sequenceNum);
        }

        log.trace("Successfully inserted {} topic messages", newTopicsMessageCount);
    }

    public long getNextAvailableTopicID() {
        String nextTopicIdSql = "SELECT MAX(topic_num) FROM topic_message";
        PostgresqlStatement statement = connection.createStatement(nextTopicIdSql);

        long nextTopicId = statement.execute()
                .flatMap(result ->
                        result.map((row, metadata) -> {
                            Long topicNum = row.get(0, Long.class);

                            if (topicNum == null) {
                                throw new IllegalStateException("Topic num query failed");
                            }

                            return topicNum;
                        })).blockFirst();

        log.trace("Determined next available topid id number from table is {}", nextTopicId + 1);
        return nextTopicId + 1;
    }

    public long getNextAvailableSequenceNumber(long topicId) {
        String nextSeqSql = "SELECT MAX(sequence_number) FROM topic_message WHERE topic_num = $1";
        PostgresqlStatement statement = connection.createStatement(nextSeqSql)
                .bind("$1", topicId);

        long nextSeqNum = statement.execute()
                .flatMap(result ->
                        result.map((row, metadata) -> {
                            Long max = row.get(0, Long.class);

                            if (max == null) {
                                max = -1L;
                                log.trace("Max sequence num query failed, setting max to -1 as likely not messages " +
                                        "for this topic exist");
                            }

                            return max;
                        })).blockFirst();

        log.trace("Determined next available sequence number from table is {}", nextSeqNum + 1);
        return nextSeqNum + 1;
    }

    public void clearTopicMessages(long topicId, long seqNumFrom) {
        if (topicId < 0 || seqNumFrom < 0) {
            log.warn("TopicId : {} or SeqNum : {} are outside of acceptable range. clearTopicMessages() will be " +
                    "skipped.", topicId, seqNumFrom);
            return;
        }

        String delTopicMsgsSql = "delete from topic_message where topic_num = $1 and sequence_number >= $2";
        PostgresqlStatement statement = connection.createStatement(delTopicMsgsSql)
                .bind("$1", topicId)
                .bind("$2", seqNumFrom);

        statement.execute().blockLast();

        log.info("Cleared Topic Messages for TopicID {}, from Sequence {}", topicId, seqNumFrom);
    }

    public void close() {
        connection.close().block();
    }
}
