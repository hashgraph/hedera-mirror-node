package com.hedera.mirror.grpc.jmeter.handler;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import com.hedera.mirror.grpc.domain.TopicMessage;

@Log4j2
public class ConnectionHandler {

    private static final int BATCH_SIZE = 100;
    private static final byte[] BYTES = new byte[] {'a', 'b', 'c'};

    private final JdbcTemplate jdbcTemplate;

    public ConnectionHandler(String host, int port, String dbName, String dbUser, String dbPassword) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setServerNames(new String[] {host});
        dataSource.setDatabaseName(dbName);
        dataSource.setPassword(dbPassword);
        dataSource.setUser(dbUser);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void createTopic(long topicNum) {
        String sql = "insert into t_entities"
                + " (entity_num, entity_realm, entity_shard, fk_entity_type_id)"
                + " values (?, 0, 0, 4) on conflict do nothing";
        jdbcTemplate.update(sql, new Object[] {topicNum});
        log.info("Created new Topic {}", topicNum);
    }

    public void insertTopicMessage(int newTopicsMessageCount, long topicNum, Instant startTime, long seqStart) {
        if (newTopicsMessageCount <= 0) {
            // no messages to create, abort and db logic
            return;
        }

        createTopic(topicNum);

        long nextSequenceNum = seqStart == -1 ? getNextAvailableSequenceNumber(topicNum) : seqStart;
        log.info("Inserting {} topic messages starting from sequence number {} and time {}", newTopicsMessageCount,
                nextSequenceNum, startTime);

        List<SqlParameterSource> parameterSources = new ArrayList<>();
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getDataSource())
                .withTableName("topic_message");

        for (int i = 1; i <= newTopicsMessageCount; i++) {
            long sequenceNum = nextSequenceNum + i;
            Instant consensusInstant = startTime.plusNanos(sequenceNum);
            TopicMessage topicMessage = TopicMessage.builder()
                    .consensusTimestamp(consensusInstant)
                    .sequenceNumber(sequenceNum)
                    .message(BYTES)
                    .runningHash(BYTES)
                    .realmNum(0)
                    .build();
            parameterSources.add(new BeanPropertySqlParameterSource(topicMessage));

            if (i % BATCH_SIZE == 0) {
                simpleJdbcInsert.executeBatch(parameterSources.toArray(new SqlParameterSource[] {}));
                parameterSources.clear();
            }
        }

        if (!parameterSources.isEmpty()) {
            simpleJdbcInsert.executeBatch(parameterSources.toArray(new SqlParameterSource[] {}));
        }

        log.debug("Successfully inserted {} topic messages", newTopicsMessageCount);
    }

    public long getNextAvailableSequenceNumber(long topicId) {
        String sql = "SELECT MAX(sequence_number) FROM topic_message WHERE topic_num = ?";
        Long maxSequenceNumber = jdbcTemplate.queryForObject(sql, new Object[] {topicId}, Long.class);
        long nextSeqNum = maxSequenceNumber != null ? maxSequenceNumber + 1 : 0;
        log.trace("Next available topic ID sequence number is {}", nextSeqNum);
        return nextSeqNum;
    }

    public void clearTopicMessages(long topicId, long seqNumFrom) {
        if (topicId < 0 || seqNumFrom < 0) {
            log.warn("TopicId : {} or SeqNum : {} are outside of acceptable range. clearTopicMessages() will be " +
                    "skipped.", topicId, seqNumFrom);
            return;
        }

        jdbcTemplate
                .update("delete from topic_message where topic_num = ? and sequence_number >= ?",
                        new Object[] {topicId, seqNumFrom});
        log.info("Cleared topic messages for topic ID {} after sequence {}", topicId, seqNumFrom);
    }
}
