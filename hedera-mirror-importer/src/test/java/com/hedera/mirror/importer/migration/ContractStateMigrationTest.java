package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.repository.ContractStateRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.66.1")
class ContractStateMigrationTest extends IntegrationTest {

    private static final String REVERT_SQL = """
            drop table if exists contract_state;
            """;

    private final @Owner JdbcTemplate jdbcTemplate;
    @Value("classpath:db/migration/v1/V1.67.0__contract_state.sql")
    private final File migrationSql;
    private final ContractStateChangeRepository contractStateChangeRepository;
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
        long createdTimestamp = 1L;
        var contractStateChange1 = domainBuilder.contractStateChange()
                .customize(c -> c.migration(true).consensusTimestamp(createdTimestamp)).persist();

        contractStateChange1.setValueWritten(domainBuilder.bytes(100));
        contractStateChange1.setConsensusTimestamp(contractStateChange1.getConsensusTimestamp() + 1);
        contractStateChangeRepository.save(contractStateChange1);

        contractStateChange1.setValueWritten(domainBuilder.bytes(100));
        contractStateChange1.setConsensusTimestamp(contractStateChange1.getConsensusTimestamp() + 2);
        contractStateChangeRepository.save(contractStateChange1);

        // Migrate is false and valueWritten is null, this should not be migrated to contract_state
        domainBuilder.contractStateChange().customize(c -> c.valueWritten(null)).persist();

        var contractState3Slot = domainBuilder.bytes(5);
        var contractStateChange3 = domainBuilder.contractStateChange()
                .customize(c -> c.migration(true).consensusTimestamp(createdTimestamp)
                        .slot(contractState3Slot)).persist();

        var expected = new ArrayList<ContractState>();
        expected.add(this.convert(contractStateChange1, createdTimestamp));
        expected.add(this.convert(contractStateChange3, createdTimestamp));

        // when
        runMigration();

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private ContractState convert(ContractStateChange contractStateChange, long createdTimestamp) {
        return ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(contractStateChange.getValueWritten())
                .build();
    }

    @SneakyThrows
    private void runMigration() {
        jdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
