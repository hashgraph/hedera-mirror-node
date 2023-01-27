package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;

@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.52.0")
class TopicMessagePayerAccountIdMigrationTest extends IntegrationTest {

    @Resource
    @Owner
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.53.0__topic_message_add_payer_account_id_and_initial_transaction_id.sql")
    private File migrationSql;

    @AfterEach
    void cleanup() {
        revertMigration();
    }

    @Test
    void verifyTopicMessagePayerAccountIdMigrationEmpty() throws Exception {
        migrate();
        assertThat(retrieveTopicMessages()).isEmpty();
    }

    @Test
    void verifyTopicMessagePayerAccountIdMigration() throws Exception {

        persistTopicMessage(Arrays.asList(
                topicMessage(1, null, 1),
                topicMessage(2, null, 2),
                topicMessage(3, 1L, 3)
        ));

        persistTransactions(Arrays.asList(
                transaction(1, 1),
                transaction(2, 2),
                transaction(3, 3)
        ));
        // migration
        migrate();

        assertThat(retrieveTopicMessages())
                .hasSize(3).extracting(MigrationTopicMessage::getPayerAccountId)
                .containsExactly(1L, 2L, 1L);
    }

    private MigrationTopicMessage topicMessage(long consensusTimestamp, Long payerAccountId, long sequenceNumber) {
        MigrationTopicMessage topicMessage = new MigrationTopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp);
        topicMessage.setPayerAccountId(payerAccountId);
        topicMessage.setSequenceNumber(sequenceNumber);
        return topicMessage;
    }

    private MigrationTransaction transaction(long consensusTimestamp, long payerAccountId) {
        MigrationTransaction transaction = new MigrationTransaction();
        transaction.setConsensusTimestamp(consensusTimestamp);
        transaction.setPayerAccountId(payerAccountId);
        return transaction;
    }

    private void migrate() throws Exception {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private List<MigrationTopicMessage> retrieveTopicMessages() {
        return jdbcOperations.query("select consensus_timestamp, payer_account_id from topic_message " +
                        "order by consensus_timestamp",
                new BeanPropertyRowMapper<>(MigrationTopicMessage.class));
    }

    private void persistTopicMessage(List<MigrationTopicMessage> topicMessages) {
        for (MigrationTopicMessage topicMessage : topicMessages) {
            jdbcOperations.update("insert into topic_message (consensus_timestamp, message, payer_account_id, " +
                            "running_hash, running_hash_version, sequence_number, topic_id) " +
                            " values (?, ?, ?, ?, ?, ?, ?)",
                    topicMessage.getConsensusTimestamp(), topicMessage.getMessage(),
                    topicMessage.getPayerAccountId(), topicMessage.getRunninghHash(),
                    topicMessage.getRunningHashVersion(), topicMessage.getSequenceNumber(),
                    topicMessage.getTopicId());
        }
    }

    private void persistTransactions(List<MigrationTransaction> transactions) {
        transactions.forEach(transaction -> {
            jdbcOperations.update("insert into transaction " +
                            "(consensus_timestamp, entity_id, node_account_id, payer_account_id, result, type, " +
                            "valid_start_ns) " +
                            "values (?, ?, ?, ?, ?, ?, ?)",
                    transaction.getConsensusTimestamp(), transaction.getEntityId(),
                    transaction.getNodeAccountId(), transaction.getPayerAccountId(), transaction.getResult(),
                    transaction.getType(), transaction.getValidStartNs());
        });
    }

    private void revertMigration() {
        jdbcOperations.update("delete from topic_message");
        jdbcOperations.update("delete from transaction");
        jdbcOperations.update("alter table topic_message alter column payer_account_id drop not null");
        jdbcOperations.update("alter table topic_message drop column initial_transaction_id");
    }

    @Data
    @NoArgsConstructor
    private static class MigrationTopicMessage {
        private long consensusTimestamp;
        private final byte[] message = new byte[] {2, 3};
        private Long payerAccountId;
        private final byte[] runninghHash = new byte[] {2, 3};
        private final int runningHashVersion = 1;
        private long sequenceNumber;
        private final long topicId = 1;
    }

    @Data
    private class MigrationTransaction {
        private Long consensusTimestamp;
        private final long entityId = 1L;
        private final long nodeAccountId = 3L;
        private long payerAccountId;
        private int result = 1;
        private int type = 1;
        private long validStartNs = 1;
    }
}
