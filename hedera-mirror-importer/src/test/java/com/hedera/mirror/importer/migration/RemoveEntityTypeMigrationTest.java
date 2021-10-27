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
import java.util.ArrayList;
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
import com.hedera.mirror.importer.domain.EntityTypeEnum;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.11")
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

        for (int i = 1; i < 7; i++) {
            entities.add(entity(i, i, EntityTypeEnum.fromId(i)));
            contracts.add(contract(i, i, EntityTypeEnum.fromId(i)));
        }
        persistEntities(entities);
        persistContracts(contracts);
        // when
        migrate();

        // then
        assertThat(findAllEntities()).extracting(MigrationEntityV1_47_0::getType).containsExactly(
                ACCOUNT, CONTRACT, FILE, TOPIC, TOKEN, SCHEDULE
        );
        assertThat(findAllEntitiesHistory()).extracting(MigrationEntityV1_47_0::getType).containsExactly(
                ACCOUNT, CONTRACT, FILE, TOPIC, TOKEN, SCHEDULE
        );
        assertThat(findAllContracts()).extracting(MigrationContractV1_47_0::getType).containsExactly(
                CONTRACT, CONTRACT, CONTRACT, CONTRACT, CONTRACT, CONTRACT
        );
        assertThat(findAllContractsHistory()).extracting(MigrationContractV1_47_0::getType).containsExactly(
                CONTRACT, CONTRACT, CONTRACT, CONTRACT, CONTRACT, CONTRACT
        );
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

    private void persistEntities(List<MigrationEntityV1_47_0> entities) {
        for (MigrationEntityV1_47_0 entity : entities) {
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

            jdbcOperations.update(
                    "insert into entity_history (id, created_timestamp, num, realm, shard, type, timestamp_range)" +
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

    private void persistContracts(List<MigrationContractV1_47_0> contracts) {
        for (MigrationContractV1_47_0 contract : contracts) {
            jdbcOperations.update(
                    "insert into contract (id, created_timestamp, memo, num, realm, shard, type, timestamp_range)" +
                            " values (?,?,?,?,?,?,?,?::int8range)",
                    contract.getId(),
                    contract.getCreatedTimestamp(),
                    contract.getMemo(),
                    contract.getNum(),
                    contract.getRealm(),
                    contract.getShard(),
                    contract.getType().getId(),
                    String.format("(%d, %d)", contract.getCreatedTimestamp(), contract.getTimestampRange()
                            .lowerEndpoint())
            );

            jdbcOperations.update(
                    "insert into contract_history (id, created_timestamp, memo, num, realm, shard, type, " +
                            "timestamp_range)" +
                            " values (?,?,?,?,?,?,?,?::int8range)",
                    contract.getId(),
                    contract.getCreatedTimestamp(),
                    contract.getMemo(),
                    contract.getNum(),
                    contract.getRealm(),
                    contract.getShard(),
                    contract.getType().getId(),
                    String.format("(%d, %d)", contract.getCreatedTimestamp(), contract.getTimestampRange()
                            .lowerEndpoint())
            );
        }
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
