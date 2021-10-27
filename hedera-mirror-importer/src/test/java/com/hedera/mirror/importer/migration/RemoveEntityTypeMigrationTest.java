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
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.converter.RangeToStringSerializer;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.11")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RemoveEntityTypeMigrationTest extends IntegrationTest {

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.47.0__replace_reference_tables.sql")
    private File migrationSql;

    @Test
    void verify() {
        // given
        List<MigrationEntityV1_47_0> entities = new ArrayList<>();
        List<MigrationContractV1_47_0> contracts = new ArrayList<>();
        List<MigrationEntityV1_47_0> badEntities = Arrays.asList(entity(1, 1, CONTRACT));
        List<MigrationContractV1_47_0> badContracts = Arrays.asList(contract(1, 1, FILE));

        for (int i = 1; i < 7; i++) {
            if (i == 2) {
                contracts.add(contract(i, i, EntityTypeEnum.fromId(i)));
            } else {
                entities.add(entity(i, i, EntityTypeEnum.fromId(i)));
            }
        }
        persistEntities(entities, false);
        persistEntitiesHistory(entities, false);
        persistContracts(contracts, false);
        persistContractsHistory(contracts, false);
        // when
        migrate();

        // then
        assertThat(findAllEntities()).extracting(MigrationEntityV1_47_0::getType).containsExactly(
                ACCOUNT, FILE, TOPIC, TOKEN, SCHEDULE
        );
        assertThat(findAllEntitiesHistory()).extracting(MigrationEntityV1_47_0::getType).containsExactly(
                ACCOUNT, FILE, TOPIC, TOKEN, SCHEDULE
        );
        assertThat(findAllContracts()).extracting(MigrationContractV1_47_0::getType).containsExactly(
                CONTRACT
        );
        assertThat(findAllContractsHistory()).extracting(MigrationContractV1_47_0::getType).containsExactly(
                CONTRACT
        );

        assertThrows(DataIntegrityViolationException.class, () -> {
            persistEntities(badEntities, true);
        });
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistEntitiesHistory(badEntities, true);
        });
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistContracts(badContracts, true);
        });
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistContractsHistory(badContracts, true);
        });
    }

    @Test
    void verifyEmpty() {
        // given

        // when
        migrate();

        // then
        assertThat(findAllEntities()).isEmpty();
        assertThat(findAllEntitiesHistory()).isEmpty();
        assertThat(findAllContracts()).isEmpty();
        assertThat(findAllContractsHistory()).isEmpty();
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.execute(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private MigrationEntityV1_47_0 entity(long id, long num, EntityTypeEnum type) {
        MigrationEntityV1_47_0 entity = new MigrationEntityV1_47_0();
        entity.setId(id);
        entity.setNum(num);
        entity.setType(type);
        return entity;
    }

    private MigrationContractV1_47_0 contract(long id, long num, EntityTypeEnum type) {
        MigrationContractV1_47_0 entity = new MigrationContractV1_47_0();
        entity.setId(id);
        entity.setNum(num);
        entity.setType(type);
        return entity;
    }

    private void persistEntities(List<MigrationEntityV1_47_0> entities, boolean migrationRan) {
        for (MigrationEntityV1_47_0 entity : entities) {
            jdbcOperations.update(
                    getEntitySql("entity", migrationRan),
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    migrationRan ? entity.getType().toString() : entity.getType().getId(),
                    String.format("(%d, %d)", entity.getCreatedTimestamp(), entity.getTimestampRange()
                            .lowerEndpoint())
            );
        }
    }

    private void persistEntitiesHistory(List<MigrationEntityV1_47_0> entities, boolean migrationRan) {
        for (MigrationEntityV1_47_0 entity : entities) {
            jdbcOperations.update(
                    getEntitySql("entity_history", migrationRan),
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    migrationRan ? entity.getType().toString() : entity.getType().getId(),
                    String.format("(%d, %d)", entity.getCreatedTimestamp(), entity.getTimestampRange()
                            .lowerEndpoint())
            );
        }
    }

    private void persistContracts(List<MigrationContractV1_47_0> contracts, boolean migrationRan) {
        for (MigrationContractV1_47_0 contract : contracts) {
            jdbcOperations.update(
                    getContractSql("contract", migrationRan),
                    contract.getId(),
                    contract.getCreatedTimestamp(),
                    contract.getMemo(),
                    contract.getNum(),
                    contract.getRealm(),
                    contract.getShard(),
                    migrationRan ? contract.getType().toString() : contract.getType().getId(),
                    String.format("(%d, %d)", contract.getCreatedTimestamp(), contract.getTimestampRange()
                            .lowerEndpoint())
            );
        }
    }

    private void persistContractsHistory(List<MigrationContractV1_47_0> contracts, boolean migrationRan) {
        for (MigrationContractV1_47_0 contract : contracts) {
            jdbcOperations.update(
                    getContractSql("contract_history", migrationRan),
                    contract.getId(),
                    contract.getCreatedTimestamp(),
                    contract.getMemo(),
                    contract.getNum(),
                    contract.getRealm(),
                    contract.getShard(),
                    migrationRan ? contract.getType().toString() : contract.getType().getId(),
                    String.format("(%d, %d)", contract.getCreatedTimestamp(), contract.getTimestampRange()
                            .lowerEndpoint())
            );
        }
    }

    private String getContractSql(String table, boolean useEnum) {
        return String.format("insert into %s (id, created_timestamp, memo, num, realm, shard, type, " +
                "timestamp_range)" +
                " values (?,?,?,?,?,?,%s,?::int8range)", table, useEnum ? "cast(? as entity_type)" : "?");
    }

    private String getEntitySql(String table, boolean useEnum) {
        return String.format("insert into %s (id, created_timestamp, num, realm, shard, type, " +
                "timestamp_range) values (?,?,?,?,?,%s,?::int8range)", table, useEnum ? "cast(? as entity_type)" : "?");
    }

    private List<MigrationEntityV1_47_0> findAllEntities() {
        return jdbcOperations.query("select id, type from entity order by id",
                new BeanPropertyRowMapper<>(MigrationEntityV1_47_0.class));
    }

    private List<MigrationEntityV1_47_0> findAllEntitiesHistory() {
        return jdbcOperations.query("select id, type from entity_history order by id",
                new BeanPropertyRowMapper<>(MigrationEntityV1_47_0.class));
    }

    private List<MigrationContractV1_47_0> findAllContracts() {
        return jdbcOperations.query("select id, type from contract order by id",
                new BeanPropertyRowMapper<>(MigrationContractV1_47_0.class));
    }

    private List<MigrationContractV1_47_0> findAllContractsHistory() {
        return jdbcOperations.query("select id, type from contract_history order by id",
                new BeanPropertyRowMapper<>(MigrationContractV1_47_0.class));
    }

    @Data
    @NoArgsConstructor
    private static class MigrationEntityV1_47_0 {
        private final long createdTimestamp = 1L;
        private long id;
        private long num;
        private final long realm = 0;
        private final long shard = 0;
        @JsonSerialize(using = RangeToStringSerializer.class)
        private final Range<Long> timestampRange = Range.atLeast(2L);
        private EntityTypeEnum type;
    }

    @Data
    @NoArgsConstructor
    private static class MigrationContractV1_47_0 {
        private long createdTimestamp = 1L;
        private long id;
        private String memo = "Migration test";
        private long num;
        private long realm = 0;
        private long shard = 0;
        @JsonSerialize(using = RangeToStringSerializer.class)
        private Range<Long> timestampRange = Range.atLeast(2L);
        private EntityTypeEnum type;
    }
}
