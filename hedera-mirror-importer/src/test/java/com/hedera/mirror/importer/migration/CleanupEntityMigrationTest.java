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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

@EnabledIfV1
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, statements = {"truncate table transaction restart " +
        "identity cascade"})
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, statements = {"truncate table transaction restart " +
        "identity cascade"})
@Tag("migration")
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

    @AfterEach
    void after() {
        cleanup();
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
        long[] ids = new long[] {1, 2, 3, 4, 5, 6};
        insertEntity(entity(ids[0], EntityTypeEnum.ACCOUNT));
        insertEntity(entity(ids[1], EntityTypeEnum.CONTRACT));
        insertEntity(entity(ids[2], EntityTypeEnum.FILE));
        insertEntity(entity(ids[3], EntityTypeEnum.TOPIC));
        insertEntity(entity(ids[4], EntityTypeEnum.TOKEN));
        insertEntity(entity(ids[5], EntityTypeEnum.SCHEDULE));

        long[] createTimestamps = new long[] {10, 20, 30, 40, 50, 60};
        insertTransaction(createTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(createTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);
        insertTransaction(createTimestamps[5], 6, EntityTypeEnum.SCHEDULE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.SCHEDULECREATE);

        assertEquals(ids.length, getTEntitiesCount());

        // migration
        migrate();

        assertEquals(createTimestamps.length, entityRepository.count());
        for (int i = 0; i < ids.length; i++) {
            Optional<Entity> entity = retrieveEntity(ids[i]);
            long consensusTimestamp = createTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(consensusTimestamp, Entity::getCreatedTimestamp)
                            .returns(consensusTimestamp, Entity::getModifiedTimestamp)
                            .returns("", Entity::getMemo)
            );
        }
    }

    private Optional<Entity> retrieveEntity(Long id) {
        return Optional.of(jdbcOperations.queryForObject(
                "select * from entity where id = ?",
                new Object[] {id},
                (rs, rowNum) -> {
                    Entity entity = new Entity();
                    entity.setAutoRenewAccountId(EntityIdEndec
                            .decode(rs.getLong("auto_renew_account_id"), EntityTypeEnum.ACCOUNT));
                    entity.setAutoRenewPeriod(rs.getLong("auto_renew_period"));
                    entity.setCreatedTimestamp(rs.getLong("created_timestamp"));
                    entity.setDeleted(rs.getBoolean("deleted"));
                    entity.setExpirationTimestamp(rs.getLong("expiration_timestamp"));
                    entity.setId(rs.getLong("id"));
                    entity.setKey(rs.getBytes("key"));
                    entity.setMemo(rs.getString("memo"));
                    entity.setModifiedTimestamp(rs.getLong("modified_timestamp"));
                    entity.setNum(rs.getLong("num"));
                    entity.setPublicKey(rs.getString("public_key"));
                    entity.setRealm(rs.getLong("realm"));
                    entity.setShard(rs.getLong("shard"));
                    entity.setSubmitKey(rs.getBytes("submit_key"));
                    entity.setType(rs.getInt("type"));

                    return entity;
                }));
    }

    @Test
    void verifyEntityMigrationCreationTransactionsWithFailures() throws Exception {
        long[] ids = new long[] {1, 2, 3, 4, 5, 6};
        insertEntity(entity(ids[0], EntityTypeEnum.ACCOUNT));
        insertEntity(entity(ids[1], EntityTypeEnum.CONTRACT));
        insertEntity(entity(ids[2], EntityTypeEnum.FILE));
        insertEntity(entity(ids[3], EntityTypeEnum.TOPIC));
        insertEntity(entity(ids[4], EntityTypeEnum.TOKEN));
        insertEntity(entity(ids[5], EntityTypeEnum.SCHEDULE));

        long[] createTimestamps = new long[] {10, 20, 30, 40, 50, 60};

        // failed create transactions
        insertTransaction(createTimestamps[0] - 1, 1, EntityTypeEnum.ACCOUNT,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1] - 1, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.INVALID_TRANSACTION,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2] - 1, 3, EntityTypeEnum.FILE, ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(createTimestamps[3] - 1, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.INVALID_NODE_ACCOUNT,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4] - 1, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.INVALID_SIGNATURE,
                TransactionTypeEnum.TOKENCREATION);
        insertTransaction(createTimestamps[5] - 1, 6, EntityTypeEnum.SCHEDULE, ResponseCodeEnum.MEMO_TOO_LONG,
                TransactionTypeEnum.SCHEDULECREATE);

        // successful create transactions
        insertTransaction(createTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(createTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);
        insertTransaction(createTimestamps[5], 6, EntityTypeEnum.SCHEDULE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.SCHEDULECREATE);

        // migration
        migrate();

        assertEquals(createTimestamps.length, entityRepository.count());
        for (int i = 0; i < ids.length; i++) {
            Optional<Entity> entity = retrieveEntity(ids[i]);
            long consensusTimestamp = createTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(consensusTimestamp, Entity::getCreatedTimestamp)
                            .returns(consensusTimestamp, Entity::getModifiedTimestamp)
                            .returns("", Entity::getMemo)
            );
        }
    }

    @Test
    void verifyEntityMigrationWithSingleUpdate() throws Exception {
        // excludes schedules as they can't yet be updated
        long[] ids = new long[] {1, 2, 3, 4, 5};
        insertEntity(entity(ids[0], EntityTypeEnum.ACCOUNT));
        insertEntity(entity(ids[1], EntityTypeEnum.CONTRACT));
        insertEntity(entity(ids[2], EntityTypeEnum.FILE));
        insertEntity(entity(ids[3], EntityTypeEnum.TOPIC));
        insertEntity(entity(ids[4], EntityTypeEnum.TOKEN));

        long[] createTimestamps = new long[] {10, 20, 30, 40, 50};

        // successful create transactions
        insertTransaction(createTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(createTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);

        // successful update transactions
        long[] modifiedTimestamps = new long[] {110, 120, 130, 140, 150};
        insertTransaction(modifiedTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOUPDATEACCOUNT);
        insertTransaction(modifiedTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTUPDATEINSTANCE);
        insertTransaction(modifiedTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILEUPDATE);
        insertTransaction(modifiedTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSUPDATETOPIC);
        insertTransaction(modifiedTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENUPDATE);

        // migration
        migrate();

        assertEquals(createTimestamps.length, entityRepository.count());
        for (int i = 0; i < ids.length; i++) {
            Optional<Entity> entity = retrieveEntity(ids[i]);
            long createdTimestamp = createTimestamps[i];
            long modifiedTimestamp = modifiedTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(createdTimestamp, Entity::getCreatedTimestamp)
                            .returns(modifiedTimestamp, Entity::getModifiedTimestamp)
            );
        }
    }

    @Test
    void verifyEntityMigrationWithMultipleUpdates() throws Exception {
        // excludes schedules as they can't yet be updated
        long[] ids = new long[] {1, 2, 3, 4, 5};
        insertEntity(entity(ids[0], EntityTypeEnum.ACCOUNT));
        insertEntity(entity(ids[1], EntityTypeEnum.CONTRACT));
        insertEntity(entity(ids[2], EntityTypeEnum.FILE));
        insertEntity(entity(ids[3], EntityTypeEnum.TOPIC));
        insertEntity(entity(ids[4], EntityTypeEnum.TOKEN));

        long[] createTimestamps = new long[] {10, 20, 30, 40, 50};

        // successful create transactions
        insertTransaction(createTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILECREATE);
        insertTransaction(createTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENCREATION);

        // successful update transactions
        long[] modifiedTimestamps = new long[] {110, 120, 130, 140, 150};
        insertTransaction(modifiedTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTOUPDATEACCOUNT);
        insertTransaction(modifiedTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTUPDATEINSTANCE);
        insertTransaction(modifiedTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILEUPDATE);
        insertTransaction(modifiedTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSUPDATETOPIC);
        insertTransaction(modifiedTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENUPDATE);

        long[] deletedTimestamps = new long[] {210, 220, 230, 240, 250};
        insertTransaction(deletedTimestamps[0], 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CRYPTODELETE);
        insertTransaction(deletedTimestamps[1], 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONTRACTDELETEINSTANCE);
        insertTransaction(deletedTimestamps[2], 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.FILEDELETE);
        insertTransaction(deletedTimestamps[3], 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.CONSENSUSDELETETOPIC);
        insertTransaction(deletedTimestamps[4], 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                TransactionTypeEnum.TOKENDELETION);

        // migration
        migrate();

        assertEquals(createTimestamps.length, entityRepository.count());
        for (int i = 0; i < ids.length; i++) {
            Optional<Entity> entity = retrieveEntity(ids[i]);
            long createdTimestamp = createTimestamps[i];
            long modifiedTimestamp = deletedTimestamps[i];
            assertAll(
                    () -> assertThat(entity).isPresent().get()
                            .returns(createdTimestamp, Entity::getCreatedTimestamp)
                            .returns(modifiedTimestamp, Entity::getModifiedTimestamp)
            );
        }
    }

    private Transaction transaction(long consensusNs, long entityNum, EntityTypeEnum entityType,
                                    ResponseCodeEnum result,
                                    TransactionTypeEnum transactionTypeEnum) {
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusNs(consensusNs);
        transaction.setEntityId(EntityId.of(0, 0, entityNum, entityType));
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

    private void insertTransaction(long consensusTimestamp, long entityNum, EntityTypeEnum entityType,
                                   ResponseCodeEnum result,
                                   TransactionTypeEnum transactionTypeEnum) {
        transactionRepository
                .save(transaction(consensusTimestamp, entityNum, entityType, result, transactionTypeEnum));
    }

    private Entity entity(long id, EntityTypeEnum entityType) {
        Entity entity = EntityIdEndec.decode(id, entityType).toEntity();
        entity.setAutoRenewAccountId(EntityId.of("1.2.3", EntityTypeEnum.ACCOUNT));
        entity.setProxyAccountId(EntityId.of("4.5.6", EntityTypeEnum.ACCOUNT));
        return entity;
    }

    private void migrate() throws IOException {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    /**
     * Insert entity object using only columns supported before V_1_36.2
     *
     * @param entity entity domain
     */
    private void insertEntity(Entity entity) {
        jdbcOperations
                .update("insert into t_entities (auto_renew_account_id, auto_renew_period, deleted, entity_num, " +
                                "entity_realm, entity_shard, ed25519_public_key_hex, exp_time_ns, fk_entity_type_id, " +
                                "id, key, memo, proxy_account_id, submit_key) values" +
                                " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        entity.getAutoRenewAccountId().getId(),
                        entity.getAutoRenewPeriod(),
                        entity.getDeleted() == null ? false : entity.getDeleted(),
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
     * Ensure entity tables match expected state before V_1_36.2
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

    private int getTEntitiesCount() {
        return jdbcOperations.queryForObject("select count(*) from t_entities", Integer.class);
    }

    private void cleanup() {
        jdbcOperations.execute("truncate table entity restart identity cascade;");

        jdbcOperations.execute("drop table if exists t_entities cascade;");
    }
}
