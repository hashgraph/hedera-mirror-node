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

import com.hedera.mirror.importer.parser.record.sidecar.SidecarProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;

public class GasConsumedMigration extends AbstractJavaMigration {

    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("1.94.0");
    private final JdbcOperations jdbcOperations;
    private final SidecarProperties sidecarProperties;
    static final DataClassRowMapper<MigrationSidecar> resultRowMapper;
    private static final String UPDATE_CONTRACT_RESULT_SQL =
            """
                    insert into contract_result(gas_consumed)
                    values(:gasConsumed)
                    where id = :id;
                    """;

    private static final long TX_DATA_ZERO_COST = 4L;
    private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
    private static final long TX_BASE_COST = 21_000L;
    private static final long TX_CREATE_EXTRA = 32_000L;

    static {
        resultRowMapper = new DataClassRowMapper<>(MigrationSidecar.class);
    }

    @Lazy
    public GasConsumedMigration(JdbcOperations jdbcOperations, SidecarProperties sidecarProperties) {
        this.jdbcOperations = jdbcOperations;
        this.sidecarProperties = sidecarProperties;
    }

    @Override
    protected void doMigrate() throws IOException {
        // check if sidecars are enabled

        // get records
        final var select =
                """
                select id, initcode, gas_used, gas_consumed from contract
                join contract_action on id = consensus_timestamp
                """;
        jdbcOperations.query(select, rs -> {
            updateGasConsumed(Objects.requireNonNull(resultRowMapper.mapRow(rs, rs.getRow())));
        });
        // then update
        jdbcOperations.update(UPDATE_CONTRACT_RESULT_SQL);
    }

    @Override
    public MigrationVersion getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add gasConsumed values prior records having sidecars from consensus nodes.";
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        return !sidecarProperties.isEnabled() || super.skipMigration(configuration);
    }

    private void updateGasConsumed(MigrationSidecar sidecar) {

        var gasConsumed = BigInteger.ZERO;
        final var initCode = sidecar.getInitcode();
        if (initCode == null || initCode.length == 0) {
            gasConsumed = sidecar.gasConsumed.add(BigInteger.valueOf(TX_BASE_COST));
        } else {
            int zeros = 0;
            for (byte b : initCode) {
                if (b == 0) {
                    ++zeros;
                }
            }
            final int nonZeros = initCode.length - zeros;

            long costForByteCode = TX_BASE_COST + TX_DATA_ZERO_COST * zeros + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;

            gasConsumed = sidecar.gasConsumed
                    .add(BigInteger.valueOf(costForByteCode))
                    .add(sidecar.getGasUsed())
                    .add(BigInteger.valueOf(TX_CREATE_EXTRA));
        }
        jdbcOperations.update(UPDATE_CONTRACT_RESULT_SQL, Map.of("id", sidecar.getId(), "gasConsumed", gasConsumed));
    }

    @Data
    static class MigrationSidecar {
        private long id;
        private byte[] initcode;
        private BigInteger gasUsed;
        private BigInteger gasConsumed;
    }
}
