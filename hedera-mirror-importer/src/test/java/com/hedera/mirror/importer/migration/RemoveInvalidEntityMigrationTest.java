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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.31.1")
class RemoveInvalidEntityMigrationTest extends IntegrationTest {

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.31.2__remove_invalid_entities.sql")
    private File migrationSql;

    @Resource
    private MirrorProperties mirrorProperties;

    @Resource
    private TransactionRepository transactionRepository;

    @BeforeEach
    void before() {
        mirrorProperties.setStartDate(Instant.EPOCH);
        mirrorProperties.setEndDate(Instant.EPOCH.plusSeconds(1));
    }

    @Test
    void verifyEntityTypeMigrationEmpty() throws Exception {
        // migration
        migrate();

        assertEquals(0, getEntityCount());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void verifyEntityTypeMigrationValidEntities() throws Exception {
        insertEntity(entityId(1, EntityTypeEnum.ACCOUNT));
        insertEntity(entityId(2, EntityTypeEnum.CONTRACT));
        insertEntity(entityId(3, EntityTypeEnum.FILE));
        insertEntity(entityId(4, EntityTypeEnum.TOPIC));
        insertEntity(entityId(5, EntityTypeEnum.TOKEN));

        List<Transaction> transactionList = new ArrayList<>();
        transactionList
                .add(transaction(1, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(20, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(30, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum
                        .FILECREATE));
        transactionList
                .add(transaction(40, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONSENSUSCREATETOPIC));
        transactionList
                .add(transaction(50, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.TOKENCREATION));
        transactionList.forEach(this::insertTransaction);

        // migration
        migrate();

        assertEquals(5, getEntityCount());
        assertEquals(5, transactionRepository.count());
    }

    @Test
    void verifyEntityTypeMigrationInvalidEntities() throws Exception {
        EntityId typeMismatchedAccountEntityId = entityId(1, EntityTypeEnum.TOPIC);
        EntityId typeMismatchedContractEntityId = entityId(2, EntityTypeEnum.TOKEN);
        EntityId typeMismatchedFileEntityId = entityId(3, EntityTypeEnum.CONTRACT);
        EntityId typeMismatchedTopicEntityId = entityId(4, EntityTypeEnum.ACCOUNT);
        EntityId typeMismatchedTokenEntityId = entityId(5, EntityTypeEnum.FILE);
        insertEntity(typeMismatchedAccountEntityId);
        insertEntity(typeMismatchedContractEntityId);
        insertEntity(typeMismatchedFileEntityId);
        insertEntity(typeMismatchedTopicEntityId);
        insertEntity(typeMismatchedTokenEntityId);

        List<Transaction> transactionList = new ArrayList<>();
        transactionList
                .add(transaction(1, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(20, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(30, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum
                        .FILECREATE));
        transactionList
                .add(transaction(40, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONSENSUSCREATETOPIC));
        transactionList
                .add(transaction(50, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.TOKENCREATION));
        transactionList
                .add(transaction(70, 50, EntityTypeEnum.TOPIC, ResponseCodeEnum.INVALID_TOPIC_ID,
                        TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        transactionList
                .add(transaction(80, 100, EntityTypeEnum.TOPIC, ResponseCodeEnum.TOPIC_EXPIRED,
                        TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        transactionList.forEach(this::insertTransaction);

        // migration
        migrate();

        assertEquals(5, getEntityCount());
        assertEquals(7, transactionRepository.count());

        assertAll(
                () -> assertThat(findEntityById(typeMismatchedAccountEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.ACCOUNT),
                () -> assertThat(findEntityById(typeMismatchedContractEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.CONTRACT),
                () -> assertThat(findEntityById(typeMismatchedFileEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.FILE),
                () -> assertThat(findEntityById(typeMismatchedTopicEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.TOPIC),
                () -> assertThat(findEntityById(typeMismatchedTokenEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.TOKEN)
        );
    }

    @Test
    void verifyEntityTypeMigrationInvalidEntitiesMultiBatch() throws Exception {
        insertEntity(entityId(1, EntityTypeEnum.ACCOUNT));
        insertEntity(entityId(2, EntityTypeEnum.CONTRACT));
        insertEntity(entityId(3, EntityTypeEnum.FILE));
        insertEntity(entityId(4, EntityTypeEnum.TOPIC));
        insertEntity(entityId(5, EntityTypeEnum.TOKEN));

        EntityId typeMismatchedAccountEntityId = entityId(6, EntityTypeEnum.TOPIC);
        EntityId typeMismatchedContractEntityId = entityId(7, EntityTypeEnum.TOKEN);
        EntityId typeMismatchedFileEntityId = entityId(8, EntityTypeEnum.CONTRACT);
        EntityId typeMismatchedTopicEntityId = entityId(9, EntityTypeEnum.ACCOUNT);
        EntityId typeMismatchedTokenEntityId = entityId(10, EntityTypeEnum.FILE);
        insertEntity(typeMismatchedAccountEntityId);
        insertEntity(typeMismatchedContractEntityId);
        insertEntity(typeMismatchedFileEntityId);
        insertEntity(typeMismatchedTopicEntityId);
        insertEntity(typeMismatchedTokenEntityId);

        List<Transaction> transactionList = new ArrayList<>();
        transactionList
                .add(transaction(1, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(20, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(30, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum
                        .FILECREATE));
        transactionList
                .add(transaction(40, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONSENSUSCREATETOPIC));
        transactionList
                .add(transaction(50, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.TOKENCREATION));
        transactionList
                .add(transaction(60, 6, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(70, 7, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(80, 8, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum
                        .FILECREATE));
        transactionList
                .add(transaction(90, 9, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONSENSUSCREATETOPIC));
        transactionList
                .add(transaction(100, 10, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.TOKENCREATION));
        transactionList
                .add(transaction(500, 50, EntityTypeEnum.TOPIC, ResponseCodeEnum.INVALID_TOPIC_ID,
                        TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        transactionList
                .add(transaction(1000, 100, EntityTypeEnum.TOPIC, ResponseCodeEnum.TOPIC_EXPIRED,
                        TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        transactionList.forEach(this::insertTransaction);

        // migration
        migrate();

        assertEquals(10, getEntityCount());
        assertEquals(12, transactionRepository.count());

        assertAll(
                () -> assertThat(findEntityById(typeMismatchedAccountEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.ACCOUNT),
                () -> assertThat(findEntityById(typeMismatchedContractEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.CONTRACT),
                () -> assertThat(findEntityById(typeMismatchedFileEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.FILE),
                () -> assertThat(findEntityById(typeMismatchedTopicEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.TOPIC),
                () -> assertThat(findEntityById(typeMismatchedTokenEntityId.getId()))
                        .extracting(Entity::getType).isEqualTo(EntityTypeEnum.TOKEN)
        );
    }

    private Transaction transaction(long consensusNs, long id, EntityTypeEnum entityType, ResponseCodeEnum result,
                                    TransactionTypeEnum transactionTypeEnum) {
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusTimestamp(consensusNs);
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

    private EntityId entityId(long id, EntityTypeEnum entityType) {
        return EntityId.of(0, 1, id, entityType);
    }

    private void migrate() throws IOException {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    /**
     * Insert transaction object using only columns supported in V_1_31_2
     *
     * @param transaction transaction domain
     */
    private void insertTransaction(Transaction transaction) {
        jdbcOperations
                .update("insert into transaction (charged_tx_fee, entity_id, initial_balance, max_fee, memo, " +
                                "node_account_id, payer_account_id, result, transaction_bytes, " +
                                "transaction_hash, type, valid_duration_seconds, valid_start_ns, consensus_ns)" +
                                " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        transaction.getChargedTxFee(),
                        transaction.getEntityId().getId(),
                        transaction.getInitialBalance(),
                        transaction.getMaxFee(),
                        transaction.getMemo(),
                        transaction.getNodeAccountId().getId(),
                        transaction.getPayerAccountId().getId(),
                        transaction.getResult(),
                        transaction.getTransactionBytes(),
                        transaction.getTransactionHash(),
                        transaction.getType(),
                        transaction.getValidDurationSeconds(),
                        transaction.getValidStartNs(),
                        transaction.getConsensusTimestamp());
    }

    /**
     * Insert entity object using only columns supported before V_1_36.2
     *
     * @param entityId entityId domain
     */
    private void insertEntity(EntityId entityId) {
        Entity entity = new Entity();
        entity.setId(entityId.getId());
        entity.setNum(entityId.getEntityNum());
        entity.setRealm(entityId.getRealmNum());
        entity.setShard(entityId.getShardNum());
        entity.setType(entityId.getType());
        entity.setMemo("abc" + (char) 0);
        entity.setAutoRenewAccountId(EntityId.of("1.2.3", EntityTypeEnum.ACCOUNT));
        entity.setProxyAccountId(EntityId.of("4.5.6", EntityTypeEnum.ACCOUNT));

        jdbcOperations
                .update("insert into t_entities (auto_renew_account_id, auto_renew_period, deleted, entity_num, " +
                                "entity_realm, entity_shard, ed25519_public_key_hex, exp_time_ns, fk_entity_type_id, " +
                                "id, key, memo, proxy_account_id, submit_key) values" +
                                " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        entity.getAutoRenewAccountId().getId(),
                        entity.getAutoRenewPeriod(),
                        entity.getDeleted(),
                        entity.getNum(),
                        entity.getRealm(),
                        entity.getShard(),
                        entity.getPublicKey(),
                        entity.getExpirationTimestamp(),
                        entity.getType().getId(),
                        entity.getId(),
                        entity.getKey(),
                        entity.getMemo(),
                        entity.getProxyAccountId().getId(),
                        entity.getSubmitKey());
    }

    private int getEntityCount() {
        return jdbcOperations.queryForObject("select count(*) from t_entities", Integer.class);
    }

    private Entity findEntityById(long id) {
        return jdbcOperations.queryForObject(
                "select * from t_entities where id = ?",
                new Object[] {id},
                (rs, rowNum) -> {
                    Entity entity = new Entity();
                    entity.setAutoRenewAccountId(EntityIdEndec
                            .decode(rs.getLong("auto_renew_account_id"), EntityTypeEnum.ACCOUNT));
                    entity.setAutoRenewPeriod(rs.getLong("auto_renew_period"));
                    entity.setDeleted(rs.getBoolean("deleted"));
                    entity.setExpirationTimestamp(rs.getLong("exp_time_ns"));
                    entity.setId(rs.getLong("id"));
                    entity.setKey(rs.getBytes("key"));
                    entity.setMemo(rs.getString("memo"));
                    entity.setNum(rs.getLong("entity_num"));
                    entity.setRealm(rs.getLong("entity_realm"));
                    entity.setShard(rs.getLong("entity_shard"));
                    entity.setProxyAccountId(EntityIdEndec
                            .decode(rs.getLong("proxy_account_id"), EntityTypeEnum.ACCOUNT));
                    entity.setSubmitKey(rs.getBytes("submit_key"));
                    entity.setType(EntityTypeEnum.fromId(rs.getInt("fk_entity_type_id")));
                    return entity;
                });
    }
}
