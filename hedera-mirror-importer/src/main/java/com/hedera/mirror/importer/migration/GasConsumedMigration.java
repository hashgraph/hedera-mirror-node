/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.parser.record.sidecar.SidecarProperties;
import com.hedera.mirror.importer.util.GasCalculatorHelper;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Map;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Named
public class GasConsumedMigration extends AbstractJavaMigration {

    private static final String ADD_GAS_CONSUMED_COLUMN =
            """
                    ALTER TABLE contract_result
                    ADD COLUMN IF NOT EXISTS gas_consumed bigint null;
                    """;

    private static final String SELECT_CONTRACT_TRANSACTIONS_SQL =
            """
                    SELECT consensus_timestamp, entity_id
                    FROM contract_transaction;
                    """;

    private static final String SELECT_GAS_USED_SQL =
            """
                    SELECT gas_used
                    FROM contract_action
                    WHERE consensus_timestamp = :consensusTimestamp;
                    """;

    private static final String SELECT_INIT_CODE_SQL =
            """
                    SELECT init_code
                    FROM contract
                    WHERE entity_id = :entityId;
                    """;

    private static final String UPDATE_CONTRACT_RESULT_SQL =
            """
                    UPDATE contract_result
                    SET gas_consumed = :gasConsumed
                    WHERE consensus_timestamp = :consensusTimestamp;
                    """;

    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("1.93.0");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SidecarProperties sidecarProperties;

    @Lazy
    public GasConsumedMigration(final @Owner JdbcTemplate jdbcTemplate, final SidecarProperties sidecarProperties) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.sidecarProperties = sidecarProperties;
    }

    @Override
    protected void doMigrate() throws IOException {
        jdbcTemplate.getJdbcOperations().execute(ADD_GAS_CONSUMED_COLUMN);

        if (!sidecarProperties.isEnabled()) {
            return;
        }

        jdbcTemplate.query(SELECT_CONTRACT_TRANSACTIONS_SQL, rs -> {
            long consensusTimestamp = rs.getLong("consensus_timestamp");
            long entityId = rs.getLong("entity_id");

            long gasConsumed = calculateTotalGasUsed(consensusTimestamp);
            var initByteCode = fetchInitCode(entityId);

            gasConsumed = GasCalculatorHelper.addIntrinsicGas(gasConsumed, initByteCode);

            jdbcTemplate.update(
                    UPDATE_CONTRACT_RESULT_SQL,
                    Map.of("consensusTimestamp", consensusTimestamp, "gasConsumed", gasConsumed));
        });
    }

    private long calculateTotalGasUsed(long consensusTimestamp) {
        return jdbcTemplate
                .queryForList(SELECT_GAS_USED_SQL, Map.of("consensusTimestamp", consensusTimestamp), Long.class)
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private ByteString fetchInitCode(long entityId) {
        try {
            byte[] initCodeBytes =
                    jdbcTemplate.queryForObject(SELECT_INIT_CODE_SQL, Map.of("entityId", entityId), byte[].class);
            return initCodeBytes != null ? ByteString.copyFrom(initCodeBytes) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public MigrationVersion getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add gasConsumed field to contract_result and populate it with data derived from sidecars associated with contract transactions.";
    }
}
