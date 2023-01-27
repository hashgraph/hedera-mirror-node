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
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.ContractStateRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.67.0")
class ContractStateMigrationTest extends IntegrationTest {

    private static final String REVERT_SQL = """
            drop table if exists contract_state;
            """;

    private final @Owner JdbcTemplate jdbcTemplate;
    @Value("classpath:db/migration/v1/V1.67.1__contract_state.sql")
    private final File migrationSql;
    private final ContractStateRepository contractStateRepository;

    @AfterEach
    @SneakyThrows
    void teardown() {
        jdbcTemplate.execute(REVERT_SQL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(contractStateRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var builder = domainBuilder.contractStateChange()
                .customize(c -> c
                        .consensusTimestamp(1L)
                        .migration(true)
                        .contractId(1000)
                        .slot(new byte[]{1})
                        .valueRead("a".getBytes())
                        .valueWritten(null)
                );

        builder.persist();
        var contractStateChange2 = builder.customize(c -> c
                .consensusTimestamp(2L)
                .valueRead("b".getBytes())
        ).persist();
        builder.customize(c -> c
                .slot(new byte[]{2})
                .valueRead("c".getBytes())
        ).persist();
        var contractStateChange4 = builder.customize(c -> c
                .contractId(1001)
                .consensusTimestamp(2L)
                .slot(new byte[]{1})
                .valueRead("d".getBytes())
        ).persist();
        var contractStateChange5 = builder.customize(c -> c
                .contractId(1000)
                .consensusTimestamp(3L)
                .migration(false)
                .slot(new byte[]{2})
                .valueRead("c".getBytes())
                .valueWritten("e".getBytes())
        ).persist();
        builder.customize(c -> c
                .contractId(1001)
                .consensusTimestamp(4L)
                .migration(false)
                .slot(new byte[]{1})
                .valueRead("f".getBytes())
                .valueWritten(null)
        ).persist();

        // when
        runMigration();

        var expected = new ArrayList<ContractState>();
        expected.add(this.convert(contractStateChange2, 1L));
        expected.add(this.convert(contractStateChange4, 2L));
        expected.add(this.convert(contractStateChange5, 2L));

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private ContractState convert(ContractStateChange contractStateChange, long createdTimestamp) {
        var value = contractStateChange.getValueWritten() == null ?
                contractStateChange.getValueRead() : contractStateChange.getValueWritten();
        return ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(value)
                .build();
    }

    @SneakyThrows
    private void runMigration() {
        jdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
