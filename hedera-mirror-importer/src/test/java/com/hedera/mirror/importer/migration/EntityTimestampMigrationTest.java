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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

@Tag("migration")
@Tag("v1")
@TestPropertySource(properties = "spring.flyway.target=1.39.2")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup_v1.40.1.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup_v1.40.1.sql")
class EntityTimestampMigrationTest extends IntegrationTest {

    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001, EntityTypeEnum.ACCOUNT);

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.40.0__entity_timestamp.sql")
    private File migrationSql;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Test
    void verifyEntityTimestampMigrationEmpty() throws Exception {
        // migration
        migrate();

        assertThat(entityRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void verifyEntityTimestampMigration() throws Exception {
        // given
        entityRepository.saveAll(List.of(
                entity(9000, EntityTypeEnum.ACCOUNT, 99L, 99L),
                entity(9001, EntityTypeEnum.ACCOUNT, 100L, 101L),
                entity(9002, EntityTypeEnum.CONTRACT),
                entity(9003, EntityTypeEnum.FILE),
                entity(9004, EntityTypeEnum.TOPIC),
                entity(9005, EntityTypeEnum.TOKEN),
                entity(9006, EntityTypeEnum.SCHEDULE)
        ));

        transactionRepository.saveAll(List.of(
                transaction(102L, 9001, SUCCESS, TransactionTypeEnum.CRYPTOUPDATEACCOUNT),
                transaction(103L, 9002, SUCCESS, TransactionTypeEnum.CONTRACTCREATEINSTANCE),
                transaction(104L, 9002, INSUFFICIENT_TX_FEE, TransactionTypeEnum.CONTRACTUPDATEINSTANCE),
                transaction(105L, 9003, SUCCESS, TransactionTypeEnum.FILECREATE),
                transaction(106L, 9003, SUCCESS, TransactionTypeEnum.FILEDELETE),
                transaction(107L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSCREATETOPIC),
                transaction(108L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(109L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(110L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(111L, 9005, SUCCESS, TransactionTypeEnum.TOKENCREATION),
                transaction(112L, 9006, SUCCESS, TransactionTypeEnum.SCHEDULECREATE)
        ));

        List<Entity> expected = List.of(
                entity(9000, EntityTypeEnum.ACCOUNT, 99L, 99L), // no change
                entity(9001, EntityTypeEnum.ACCOUNT, 100L, 102L), // updated at 102L
                entity(9002, EntityTypeEnum.CONTRACT, 103L, 103L), // update transaction failed at 104L
                entity(9003, EntityTypeEnum.FILE, 105L, 106L), // created at 105L, deleted at 106L
                entity(9004, EntityTypeEnum.TOPIC, 107L, 110L), // last update at 110L
                entity(9005, EntityTypeEnum.TOKEN, 111L, 111L),
                entity(9006, EntityTypeEnum.SCHEDULE, 112L, 112L)
        );

        // when
        migrate();

        // then
        assertThat(entityRepository.findAll())
                .usingElementComparatorOnFields("id", "createdTimestamp", "modifiedTimestamp")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private Entity entity(long id, EntityTypeEnum entityTypeEnum) {
        return entity(id, entityTypeEnum, null, null);
    }

    private Entity entity(long id, EntityTypeEnum entityTypeEnum, Long createdTimestamp, Long modifiedTimestamp) {
        Entity entity = EntityIdEndec.decode(id, entityTypeEnum).toEntity();
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeleted(false);
        entity.setMemo("foobar");
        entity.setModifiedTimestamp(modifiedTimestamp);
        return entity;
    }

    private Transaction transaction(long consensusNs, long entityNum, ResponseCodeEnum result,
                                    TransactionTypeEnum type) {
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(consensusNs);
        transaction.setEntityId(EntityId.of(0, 0, entityNum, EntityTypeEnum.UNKNOWN));
        transaction.setNodeAccountId(NODE_ACCOUNT_ID);
        transaction.setPayerAccountId(PAYER_ID);
        transaction.setResult(result.getNumber());
        transaction.setType(type.getProtoId());
        transaction.setValidStartNs(consensusNs - 10);
        return transaction;
    }

    private void migrate() throws Exception {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
