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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, statements = {"truncate table transaction restart " +
        "identity cascade"})
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, statements = {"truncate table transaction restart " +
        "identity cascade"})
@Tag("migration")
@Tag("v1")
@TestPropertySource(properties = "spring.flyway.target=1.35.5")
class CleanupEntityMigrationTest extends IntegrationTest {

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.36.2__entities_update.sql")
    private File migrationSql;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private MirrorProperties mirrorProperties;

    @Resource
    private TransactionRepository transactionRepository;

    @BeforeEach
    void before() {
        mirrorProperties.setStartDate(Instant.EPOCH);
        mirrorProperties.setEndDate(Instant.EPOCH.plusSeconds(1));
        setEntityTablesPreV_1_36();
    }

    @Test
    void verifyEntityTypeMigrationEmpty() throws Exception {
        // migration
        migrate();

        assertEquals(0, entityRepository.count());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void verifyEntityMigrationCreationTransactions() throws Exception {
        insertEntity(entity(1, EntityTypeEnum.ACCOUNT));
        insertEntity(entity(2, EntityTypeEnum.CONTRACT));
        insertEntity(entity(3, EntityTypeEnum.FILE));
        insertEntity(entity(4, EntityTypeEnum.TOPIC));
        insertEntity(entity(5, EntityTypeEnum.TOKEN));
        insertEntity(entity(6, EntityTypeEnum.SCHEDULE));

        long[] consensusTimestamps = new long[] {10, 20, 30, 40, 50, 60};
        insertTransaction(consensusTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(consensusTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(consensusTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(consensusTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(consensusTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);
        insertTransaction(consensusTimestamps[5], 6, EntityTypeEnum.SCHEDULE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.SCHEDULECREATE);

        // migration
        migrate();

        assertEquals(consensusTimestamps.length, entityRepository.count());
        for (int i = 0; i < 5; i++) {
            Optional<Entity> entity = entityRepository.findById((long) i);
            long consensusTimestamp = consensusTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(consensusTimestamp, Entity::getCreatedTimestamp)
                            .returns(consensusTimestamp, Entity::getModifiedTimestamp)
                            .returns("", Entity::getMemo)
            );
        }
    }

    @Test
    void verifyEntityMigrationCreationTransactionsWithFailures() throws Exception {
        insertEntity(entity(1, EntityTypeEnum.ACCOUNT));
        insertEntity(entity(2, EntityTypeEnum.CONTRACT));
        insertEntity(entity(3, EntityTypeEnum.FILE));
        insertEntity(entity(4, EntityTypeEnum.TOPIC));
        insertEntity(entity(5, EntityTypeEnum.TOKEN));
        insertEntity(entity(6, EntityTypeEnum.SCHEDULE));

        long[] consensusTimestamps = new long[] {10, 20, 30, 40, 50, 60};

        // failed create transactions
        insertTransaction(consensusTimestamps[0] - 1, 1, EntityTypeEnum.ACCOUNT,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(consensusTimestamps[1] - 1, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.INVALID_TRANSACTION,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(consensusTimestamps[2] - 1, 3, EntityTypeEnum.FILE, ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(consensusTimestamps[3] - 1, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.INVALID_NODE_ACCOUNT,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(consensusTimestamps[4] - 1, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.INVALID_SIGNATURE,
                TransactionTypeEnum.TOKENCREATION);
        insertTransaction(consensusTimestamps[5] - 1, 6, EntityTypeEnum.SCHEDULE, ResponseCodeEnum.MEMO_TOO_LONG,
                TransactionTypeEnum.SCHEDULECREATE);

        // successful create transactions
        insertTransaction(consensusTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(consensusTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(consensusTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(consensusTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(consensusTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);
        insertTransaction(consensusTimestamps[5], 6, EntityTypeEnum.SCHEDULE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.SCHEDULECREATE);

        // migration
        migrate();

        assertEquals(consensusTimestamps.length, entityRepository.count());
        for (int i = 0; i < consensusTimestamps.length; i++) {
            Optional<Entity> entity = entityRepository.findById((long) i);
            long consensusTimestamp = consensusTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(consensusTimestamp, Entity::getCreatedTimestamp)
                            .returns(consensusTimestamp, Entity::getModifiedTimestamp)
                            .returns("", Entity::getMemo)
            );
        }
    }

    @Test
    void verifyEntityMigrationWithUpdates() throws Exception {
        // excludes schedules as they can't yet be updated
        insertEntity(entity(1, EntityTypeEnum.ACCOUNT));
        insertEntity(entity(2, EntityTypeEnum.CONTRACT));
        insertEntity(entity(3, EntityTypeEnum.FILE));
        insertEntity(entity(4, EntityTypeEnum.TOPIC));
        insertEntity(entity(5, EntityTypeEnum.TOKEN));

        long[] consensusTimestamps = new long[] {10, 20, 30, 40, 50};

        // successful create transactions
        insertTransaction(consensusTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(consensusTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(consensusTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(consensusTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(consensusTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);

        // successful update transactions
        long timeBuffer = 100;
        insertTransaction(consensusTimestamps[0] + timeBuffer, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOUPDATEACCOUNT);
        insertTransaction(consensusTimestamps[1] + timeBuffer, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTUPDATEINSTANCE);
        insertTransaction(consensusTimestamps[2] + timeBuffer, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILEUPDATE);
        insertTransaction(consensusTimestamps[3] + timeBuffer, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSUPDATETOPIC);
        insertTransaction(consensusTimestamps[4] + timeBuffer, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENUPDATE);

        // migration
        migrate();

        assertEquals(consensusTimestamps.length, entityRepository.count());
        for (int i = 0; i < consensusTimestamps.length; i++) {
            Optional<Entity> entity = entityRepository.findById((long) i);
            long consensusTimestamp = consensusTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(consensusTimestamp, Entity::getCreatedTimestamp)
                            .returns(consensusTimestamp + timeBuffer, Entity::getModifiedTimestamp)
            );
        }
    }

    private Transaction transaction(long consensusNs, long id, EntityTypeEnum entityType, ResponseCodeEnum result,
                                    TransactionTypeEnum transactionTypeEnum) {
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusNs(consensusNs);
        transaction.setEntityId(EntityId.of(0, 1, id, entityType));
        transaction.setInitialBalance(1000L);
        transaction.setMemo("transaction memo".getBytes());
        transaction.setNodeAccountId(EntityId.of(0, 1, 3, EntityTypeEnum.ACCOUNT));
        transaction.setPayerAccountId(EntityId.of(0, 1, 98, EntityTypeEnum.ACCOUNT));
        transaction.setResult(result.getNumber());
        transaction.setType(transactionTypeEnum.getProtoId());
        transaction.setValidStartNs(20L);
        transaction.setValidDurationSeconds(11L);
        transaction.setMaxFee(33L);
        return transaction;
    }

    private void insertTransaction(long consensusTimestamp, long transactionId, EntityTypeEnum entityType,
                                   ResponseCodeEnum result,
                                   TransactionTypeEnum transactionTypeEnum) {
        transactionRepository
                .save(transaction(consensusTimestamp, transactionId, entityType, result, transactionTypeEnum));
    }

    private Entity entity(long id, EntityTypeEnum entityType) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setNum(1L);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(entityType.getId());
        entity.setMemo("abc" + (char) 0);
        entity.setAutoRenewAccountId(EntityId.of("1.2.3", EntityTypeEnum.ACCOUNT));
        entity.setProxyAccountId(EntityId.of("4.5.6", EntityTypeEnum.ACCOUNT));
        return entity;
    }

    private void migrate() throws IOException {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    /**
     * Insert entity object using only columns supported before V_1_36.1
     *
     * @param entity entity domain
     */
    private void insertEntity(Entity entity) {
        jdbcOperations
                .update("insert into t_entites (auto_renew_account_id, auto_renew_period, deleted, entity_num, " +
                                "entity_realm, entity_shard, ed25519_public_key_hex, exp_time_ns, fk_entity_type_id, " +
                                "id, key, memo, proxy_account_id, submit_key) values" +
                                " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        entity.getAutoRenewAccountId().getId(),
                        entity.getAutoRenewPeriod(),
                        entity.isDeleted(),
                        entity.getNum(),
                        entity.getRealm(),
                        entity.getShard(),
                        entity.getPublicKey(),
                        entity.getExpirationTimestamp(),
                        entity.getType(),
                        entity.getId(),
                        entity.getKey(),
                        entity.getMemo(),
                        entity.getProxyAccountId().getId(),
                        entity.getSubmitKey());
    }

    /**
     * Ensure entity tables match expected state before V_1_36.0
     */
    private void setEntityTablesPreV_1_36() {
        // remove entity table if present
        jdbcOperations
                .execute("drop table if exists entity cascade;");

        // add t_entities if not present
        jdbcOperations
                .execute("create table if not exists t_entities" +
                        "(\n" +
                        "    entity_num             bigint  not null,\n" +
                        "    entity_realm           bigint  not null,\n" +
                        "    entity_shard           bigint  not null,\n" +
                        "    fk_entity_type_id      integer not null,\n" +
                        "    auto_renew_period      bigint,\n" +
                        "    key                    bytea,\n" +
                        "    deleted                boolean default false,\n" +
                        "    exp_time_ns            bigint,\n" +
                        "    ed25519_public_key_hex character varying,\n" +
                        "    submit_key             bytea,\n" +
                        "    memo                   text,\n" +
                        "    auto_renew_account_id  bigint,\n" +
                        "    id                     bigint  not null,\n" +
                        "    proxy_account_id       bigint" +
                        ");");
    }
}
