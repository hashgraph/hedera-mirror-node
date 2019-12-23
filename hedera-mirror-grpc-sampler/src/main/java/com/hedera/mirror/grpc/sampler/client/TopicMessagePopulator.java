package com.hedera.mirror.grpc.sampler.client;

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

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

@Named
@Log4j2
public class TopicMessagePopulator {

    private final PostgresqlConnectionFactory postgressqlConnectionFactory;
    private final Instant now = Instant.now();

    public TopicMessagePopulator(ConnectionFactory connectionFactory) {
        ConnectionPool connectionPool = (ConnectionPool) connectionFactory;
        postgressqlConnectionFactory = (PostgresqlConnectionFactory) connectionPool
                .unwrap();
    }

    public void AddTopicMessages(long topicNum, int seqStart, int messages) {
        String topicMessageInsertSql = "insert into topic_message "
                + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                + " values ({}, {}, {}, {}, {})";

        postgressqlConnectionFactory.create().flatMapMany(it -> {
            for (int i = seqStart; i < messages + seqStart; i++) {
                return it.createStatement(String
                        .format(topicMessageInsertSql, now
                                .plus(i, ChronoUnit.NANOS), 0, topicNum, new byte[] {0, 1, 2}, new byte[] {4, 5, 6}, i))
                        .execute();
            }

            return null;
        });
    }
}
