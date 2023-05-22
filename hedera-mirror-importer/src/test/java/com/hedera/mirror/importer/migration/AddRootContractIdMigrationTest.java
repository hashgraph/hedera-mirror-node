/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import jakarta.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.51.0")
class AddRootContractIdMigrationTest extends IntegrationTest {

    @Resource
    @Owner
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.51.1__contract_logs_root_id.sql")
    private File migrationSql;

    @BeforeEach
    void setup() {
        revertMigration();
    }

    @Test
    void verifyRootContractIdMigrationEmpty() throws Exception {
        migrate();
        assertThat(retrieveContractLogs()).isEmpty();
    }

    @Test
    void verifyRootContractIdMigration() throws Exception {

        persistContractResult(Arrays.asList(contractResult(1, 1L), contractResult(2, 2L), contractResult(3, null)));
        persistContractLog(Arrays.asList(
                contractLog(1, 1, 0),
                contractLog(1, 2, 1),
                contractLog(1, 3, 2),
                contractLog(2, 1, 0),
                contractLog(3, 1, 0)));
        // migration
        migrate();

        assertThat(retrieveContractLogs())
                .hasSize(5)
                .extracting(MigrationContractLog::getRootContractId)
                .containsExactly(1L, 1L, 1L, 2L, null);
    }

    private MigrationContractLog contractLog(long consensusTimestamp, long contractId, int index) {
        MigrationContractLog migrationContractLog = new MigrationContractLog();
        migrationContractLog.setConsensusTimestamp(consensusTimestamp);
        migrationContractLog.setContractId(contractId);
        migrationContractLog.setIndex(index);
        return migrationContractLog;
    }

    private MigrationContractResult contractResult(long consensusTimestamp, Long contractId) {
        MigrationContractResult migrationContractResult = new MigrationContractResult();
        migrationContractResult.setConsensusTimestamp(consensusTimestamp);
        migrationContractResult.setContractId(contractId);
        return migrationContractResult;
    }

    private void migrate() throws Exception {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private List<MigrationContractLog> retrieveContractLogs() {
        return jdbcOperations.query(
                "select consensus_timestamp, contract_id, root_contract_id from contract_log "
                        + "order by consensus_timestamp asc, index asc",
                new BeanPropertyRowMapper<>(MigrationContractLog.class));
    }

    private void persistContractLog(List<MigrationContractLog> contractLogs) {
        for (MigrationContractLog contractLog : contractLogs) {
            jdbcOperations.update(
                    "insert into contract_log (bloom, consensus_timestamp, contract_id, data, "
                            + "index, payer_account_id) "
                            + " values"
                            + " (?, ?, ?, ?, ?, ?)",
                    contractLog.getBloom(),
                    contractLog.getConsensusTimestamp(),
                    contractLog.getContractId(),
                    contractLog.getData(),
                    contractLog.getIndex(),
                    contractLog.getPayerAccountId());
        }
    }

    private void persistContractResult(List<MigrationContractResult> contractResults) {
        for (MigrationContractResult contractResult : contractResults) {
            jdbcOperations.update(
                    "insert into contract_result (consensus_timestamp, contract_id, function_parameters, "
                            + "gas_limit, gas_used, payer_account_id) "
                            + " values"
                            + " (?, ?, ?, ?, ?, ?)",
                    contractResult.getConsensusTimestamp(),
                    contractResult.getContractId(),
                    contractResult.getFunctionParameters(),
                    contractResult.getGasLimit(),
                    contractResult.getGasUsed(),
                    contractResult.getPayerAccountId());
        }
    }

    private void revertMigration() {
        jdbcOperations.update("alter table contract_log drop column if exists root_contract_id");
    }

    @Data
    @NoArgsConstructor
    private static class MigrationContractLog {
        private final byte[] bloom = new byte[] {2, 2};
        private long consensusTimestamp;
        private long contractId;
        private final byte[] data = new byte[] {2, 2};
        private int index = 0;
        private final long payerAccountId = 100;
        private Long rootContractId;
    }

    @Data
    @NoArgsConstructor
    private static class MigrationContractResult {
        private long consensusTimestamp;
        private Long contractId;
        private final byte[] functionParameters = new byte[] {2, 2};
        private final long gasLimit = 2;
        private final long gasUsed = 2;
        private final long payerAccountId = 100;
    }
}
