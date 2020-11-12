package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Log4j2
@TestPropertySource(properties = "spring.flyway.target=1.31.0")
public class V1_31_1__Entity_Type_MismatchTest extends IntegrationTest {
    @Resource
    private V1_31_1__Entity_Type_Mismatch migration;

    @Resource
    private DataSource dataSource;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private MirrorProperties mirrorProperties;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private FlywayMigrationProperties flywayMigrationProperties;

    @BeforeEach
    void before() {
        mirrorProperties.setStartDate(Instant.EPOCH);
        mirrorProperties.setEndDate(Instant.EPOCH.plusSeconds(1));
    }

    @Test
    void verifyEntityTypeMigrationEmpty() throws Exception {
        // migration
        migration.migrate(new FlywayContext());

        assertEquals(0, entityRepository.count());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void verifyEntityTypeMigrationValidEntities() throws Exception {
        flywayMigrationProperties.setEntityMismatchReadPageSize(3);
        flywayMigrationProperties.setEntityMismatchWriteBatchSize(3);

        entityRepository.insertEntityId(entityId(1, EntityTypeEnum.ACCOUNT));
        entityRepository.insertEntityId(entityId(2, EntityTypeEnum.CONTRACT));
        entityRepository.insertEntityId(entityId(3, EntityTypeEnum.FILE));
        entityRepository.insertEntityId(entityId(4, EntityTypeEnum.TOPIC));
        entityRepository.insertEntityId(entityId(5, EntityTypeEnum.TOKEN));

        List<Transaction> transactionList = new ArrayList<>();
        transactionList
                .add(transaction(1, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(20, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(30, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum.FILECREATE));
        transactionList
                .add(transaction(40, 4, EntityTypeEnum.TOPIC, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONSENSUSCREATETOPIC));
        transactionList
                .add(transaction(50, 5, EntityTypeEnum.TOKEN, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.TOKENCREATION));
        transactionRepository.saveAll(transactionList);

        // migration
        migration.migrate(new FlywayContext());

        assertEquals(5, entityRepository.count());
        assertEquals(5, transactionRepository.count());
    }

    @Test
    void verifyEntityTypeMigrationInValidEntities() throws Exception {
        flywayMigrationProperties.setEntityMismatchReadPageSize(3);
        flywayMigrationProperties.setEntityMismatchWriteBatchSize(3);

        EntityId typeMismatchedAccountEntityId = entityId(1, EntityTypeEnum.TOPIC);
        EntityId typeMismatchedContractEntityId = entityId(2, EntityTypeEnum.TOKEN);
        EntityId typeMismatchedFileEntityId = entityId(3, EntityTypeEnum.CONTRACT);
        EntityId typeMismatchedTopicEntityId = entityId(4, EntityTypeEnum.ACCOUNT);
        EntityId typeMismatchedTokenEntityId = entityId(5, EntityTypeEnum.FILE);
        entityRepository.insertEntityId(typeMismatchedAccountEntityId);
        entityRepository.insertEntityId(typeMismatchedContractEntityId);
        entityRepository.insertEntityId(typeMismatchedFileEntityId);
        entityRepository.insertEntityId(typeMismatchedTopicEntityId);
        entityRepository.insertEntityId(typeMismatchedTokenEntityId);
        entityRepository.insertEntityId(entityId(50, EntityTypeEnum.TOPIC));
        entityRepository.insertEntityId(entityId(100, EntityTypeEnum.TOPIC));

        List<Transaction> transactionList = new ArrayList<>();
        transactionList
                .add(transaction(1, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(20, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(30, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum.FILECREATE));
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
        transactionRepository.saveAll(transactionList);

        // migration
        migration.migrate(new FlywayContext());

        assertEquals(7, entityRepository.count());
        assertEquals(7, transactionRepository.count());

        assertAll(
                () -> assertThat(entityRepository.findById(typeMismatchedAccountEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.ACCOUNT.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedContractEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.CONTRACT.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedFileEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.FILE.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedTopicEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.TOPIC.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedTokenEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.TOKEN.getId())
        );
    }

    @Test
    void verifyEntityTypeMigrationInValidEntitiesMultiBatch() throws Exception {
        flywayMigrationProperties.setEntityMismatchReadPageSize(4);
        flywayMigrationProperties.setEntityMismatchWriteBatchSize(4);

        entityRepository.insertEntityId(entityId(1, EntityTypeEnum.ACCOUNT));
        entityRepository.insertEntityId(entityId(2, EntityTypeEnum.CONTRACT));
        entityRepository.insertEntityId(entityId(3, EntityTypeEnum.FILE));
        entityRepository.insertEntityId(entityId(4, EntityTypeEnum.TOPIC));
        entityRepository.insertEntityId(entityId(5, EntityTypeEnum.TOKEN));

        EntityId typeMismatchedAccountEntityId = entityId(6, EntityTypeEnum.TOPIC);
        EntityId typeMismatchedContractEntityId = entityId(7, EntityTypeEnum.TOKEN);
        EntityId typeMismatchedFileEntityId = entityId(8, EntityTypeEnum.CONTRACT);
        EntityId typeMismatchedTopicEntityId = entityId(9, EntityTypeEnum.ACCOUNT);
        EntityId typeMismatchedTokenEntityId = entityId(10, EntityTypeEnum.FILE);
        entityRepository.insertEntityId(typeMismatchedAccountEntityId);
        entityRepository.insertEntityId(typeMismatchedContractEntityId);
        entityRepository.insertEntityId(typeMismatchedFileEntityId);
        entityRepository.insertEntityId(typeMismatchedTopicEntityId);
        entityRepository.insertEntityId(typeMismatchedTokenEntityId);
        entityRepository.insertEntityId(entityId(50, EntityTypeEnum.TOPIC));
        entityRepository.insertEntityId(entityId(100, EntityTypeEnum.TOPIC));

        List<Transaction> transactionList = new ArrayList<>();
        transactionList
                .add(transaction(1, 1, EntityTypeEnum.ACCOUNT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        transactionList
                .add(transaction(20, 2, EntityTypeEnum.CONTRACT, ResponseCodeEnum.SUCCESS,
                        TransactionTypeEnum.CONTRACTCREATEINSTANCE));
        transactionList
                .add(transaction(30, 3, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum.FILECREATE));
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
                .add(transaction(80, 8, EntityTypeEnum.FILE, ResponseCodeEnum.SUCCESS, TransactionTypeEnum.FILECREATE));
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
        transactionRepository.saveAll(transactionList);

        // migration
        migration.migrate(new FlywayContext());

        assertEquals(12, entityRepository.count());
        assertEquals(12, transactionRepository.count());

        assertAll(
                () -> assertThat(entityRepository.findById(typeMismatchedAccountEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.ACCOUNT.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedContractEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.CONTRACT.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedFileEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.FILE.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedTopicEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.TOPIC.getId()),
                () -> assertThat(entityRepository.findById(typeMismatchedTokenEntityId.getId())).isPresent().get()
                        .extracting(Entities::getEntityTypeId).isEqualTo(EntityTypeEnum.TOKEN.getId())
        );
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

    private EntityId entityId(long id, EntityTypeEnum entityType) {
        return EntityId.of(0, 1, id, entityType);
    }

    private class FlywayContext implements Context {

        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public Connection getConnection() {
            try {
                return dataSource.getConnection();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
