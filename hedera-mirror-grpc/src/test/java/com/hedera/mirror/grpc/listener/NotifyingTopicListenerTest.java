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

import java.time.Duration;
import java.time.Instant;
import javax.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class NotifyingTopicListenerTest extends AbstractTopicListenerTest {

    @Resource
    private NotifyingTopicListener topicListener;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    protected TopicListener getTopicListener() {
        return topicListener;
    }

    // Since importer manually calls pg_notify, we simulate that here with a rule that invokes pg_notify on insert
    @BeforeEach
    void createRule() {
        jdbcTemplate.execute("create rule topic_message_rule " +
                "as on insert to topic_message do also " +
                "select pg_notify('topic_message', (select row_to_json(payload)::text from (select NEW.chunk_num,NEW" +
                ".chunk_total,NEW.consensus_timestamp,encode(NEW.message, 'base64') as message,NEW.payer_account_id," +
                "NEW.realm_num,encode(NEW.running_hash, 'base64') as running_hash,NEW.running_hash_version,NEW" +
                ".sequence_number,NEW.topic_num,NEW.valid_start_timestamp) payload)) payload2");
    }

    @AfterEach
    void dropRule() {
        jdbcTemplate.execute("drop rule if exists topic_message_rule on topic_message");
    }

    // Test deserialization from JSON to verify contract with PostgreSQL listen/notify
    @Test
    void json() {
        String json = "{" +
                "\"chunk_num\":1," +
                "\"chunk_total\":2," +
                "\"consensus_timestamp\":1594401417000000000," +
                "\"message\":\"AQID\"," +
                "\"payer_account_id\":4294968296," +
                "\"realm_num\":0," +
                "\"running_hash\":\"BAUG\"," +
                "\"running_hash_version\":2," +
                "\"sequence_number\":1," +
                "\"topic_num\":1001," +
                "\"valid_start_timestamp\":1594401416000000000" +
                "}";

        TopicMessage topicMessage = TopicMessage.builder().chunkNum(1)
                .chunkTotal(2)
                .consensusTimestamp(Instant.ofEpochSecond(1594401417))
                .message(new byte[] {1, 2, 3})
                .payerAccountId(4294968296L)
                .realmNum(0)
                .runningHash(new byte[] {4, 5, 6})
                .runningHashVersion(2)
                .sequenceNumber(1L)
                .topicNum(1001)
                .validStartTimestamp(Instant.ofEpochSecond(1594401416)).build();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> jdbcTemplate.execute("NOTIFY topic_message, '" + json + "'"))
                .expectNext(topicMessage);
    }

    @Test
    void jsonError() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        // Parsing errors will be logged and ignored and the message will be lost
        topicListener.listen(filter)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> jdbcTemplate.execute("NOTIFY topic_message, 'invalid'"))
                .expectNoEvent(Duration.ofMillis(500L));
    }
}
