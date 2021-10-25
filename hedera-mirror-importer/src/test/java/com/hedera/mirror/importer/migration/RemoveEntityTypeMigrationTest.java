package com.hedera.mirror.importer.migration;

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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.CONTRACT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.FILE;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.SCHEDULE;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.io.File;
import java.util.List;
import javax.annotation.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.converter.RangeToStringSerializer;
import com.hedera.mirror.importer.domain.DomainBuilder;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.7")
class RemoveEntityTypeMigrationTest extends IntegrationTest {

    @Resource
    private JdbcOperations jdbcOperations;

    @Resource
    private DomainBuilder domainBuilder;

    @Value("classpath:db/migration/v1/V1.47.0__replace_reference_tables.sql")
    private File migrationSql;

    @Test
    void verify() {
        // given
        // entities
        // - 2 ft classes
        //   - deleted, account1's token dissociate includes token transfer
        //   - still alive
        // - 3 nft classes
        //   - deleted, account1's token dissociate doesn't include token transfer, account2's includes
        //   - deleted, account1's token dissociate doesn't include token transfer, account2's dissociate happened
        //     before token deletion
        //   - still alive
        MigrationEntityV1_46 account = entity(1, 1, ACCOUNT);
        MigrationEntityV1_46 contract = entity(2, 2, CONTRACT);
        MigrationEntityV1_46 file = entity(3, 3, FILE);
        MigrationEntityV1_46 topic = entity(4, 4, TOPIC);
        MigrationEntityV1_46 token = entity(5, 5, TOKEN);
        MigrationEntityV1_46 schedule = entity(6, 6, SCHEDULE);

        persistEntities(List.of(account, contract, file, topic, token, schedule));
        // when
        migrate();

        // then
        assertThat(findAllEntities()).extracting(MigrationEntityV1_46::getType).containsExactly(
                ACCOUNT, CONTRACT, FILE, TOPIC, TOKEN, SCHEDULE
        );
//        assertThat(findAllEntitiesHistory()).extracting(MigrationEntityV1_46::getType).containsExactly(
//                ACCOUNT, CONTRACT, FILE, TOPIC, TOKEN, SCHEDULE
//        );
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.execute(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private MigrationEntityV1_46 entity(long id, long num, EntityTypeEnum type) {
        MigrationEntityV1_46 entity = new MigrationEntityV1_46();
        entity.setId(id);
        entity.setNum(num);
        entity.setType(type);
        return entity;
    }

    private void persistEntities(List<MigrationEntityV1_46> entities) {
        for (MigrationEntityV1_46 entity : entities) {
            jdbcOperations.update(
                    "insert into entity (id, created_timestamp, num, realm, shard, type, timestamp_range)" +
                            " values (?,?,?,?,?,?,?::int8range)",
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    entity.getType().getId(),
                    String.format("(%d, %d)", entity.getCreatedTimestamp(), entity.getTimestampRange()
                            .lowerEndpoint())
            );
        }
    }

    //    private void persistTransactions(List<Transaction> transactions) {
//        for (Transaction transaction : transactions) {
//            jdbcOperations
//                    .update("insert into transaction (charged_tx_fee, consensus_ns, entity_id, initial_balance, " +
//                                    "max_fee, " +
//                                    "memo, " +
//                                    "node_account_id, payer_account_id, result, scheduled, transaction_bytes, " +
//                                    "transaction_hash, type, valid_duration_seconds, valid_start_ns)" +
//                                    " values" +
//                                    " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
//                            transaction.getChargedTxFee(),
//                            transaction.getConsensusTimestamp(),
//                            transaction.getEntityId().getId(),
//                            transaction.getInitialBalance(),
//                            transaction.getMaxFee(),
//                            transaction.getMemo(),
//                            transaction.getNodeAccountId().getId(),
//                            transaction.getPayerAccountId().getId(),
//                            transaction.getResult(),
//                            transaction.isScheduled(),
//                            transaction.getTransactionBytes(),
//                            transaction.getTransactionHash(),
//                            transaction.getType(),
//                            transaction.getValidDurationSeconds(),
//                            transaction.getValidStartNs());
//        }
//    }
//
//    private List<Transaction> findAllTransactions() {
//        return jdbcOperations.query("select * from transaction", new RowMapper<>() {
//
//            @Override
//            public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
//                Transaction transaction = new Transaction();
//                transaction.setConsensusTimestamp(rs.getLong("consensus_ns"));
//                transaction.setEntityId(EntityId.of(0, 0, rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT));
//                transaction.setMemo(rs.getBytes("transaction_bytes"));
//                transaction.setNodeAccountId(EntityId.of(0, 0, rs.getLong("node_account_id"), EntityTypeEnum
//                .ACCOUNT));
//                transaction
//                        .setPayerAccountId(EntityId.of(0, 0, rs.getLong("payer_account_id"), EntityTypeEnum.ACCOUNT));
//                transaction.setResult(rs.getInt("result"));
//                transaction.setType(rs.getInt("type"));
//                transaction.setValidStartNs(rs.getLong("valid_start_ns"));
//                return transaction;
//            }
//        });
//    }
//
    private List<MigrationEntityV1_46> findAllEntities() {
        return jdbcOperations.query("select id, type from entity order by id",
                new BeanPropertyRowMapper<>(MigrationEntityV1_46.class));
    }

    private List<MigrationEntityV1_46> findAllEntitiesHistory() {
        return jdbcOperations.query("select id, type from entity_history order by id",
                new BeanPropertyRowMapper<>(MigrationEntityV1_46.class));
    }

    @Data
    @NoArgsConstructor
    private static class MigrationEntityV1_46 {
        private long createdTimestamp = 1L;
        private long id;
        private long num;
        private long realm = 0;
        private long shard = 0;
        @JsonSerialize(using = RangeToStringSerializer.class)
        private Range<Long> timestampRange = Range.atLeast(2L);
        private EntityTypeEnum type;
    }
}
