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

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Map;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
public class AccountEvmAddressMigration extends RepeatableMigration {

    private final NamedParameterJdbcOperations jdbcOperations;

    @Lazy
    public AccountEvmAddressMigration(NamedParameterJdbcOperations jdbcOperations, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    protected void doMigrate() throws IOException {
        updateAlias(false);
        updateAlias(true);
    }

    // We search for aliases with a length of 35 since ECDSA secp256k1 aliases are 33 bytes w/ 2 bytes for proto prefix
    private void updateAlias(boolean history) {
        String suffix = history ? "_history" : "";
        var query = String.format(
                "select id, alias from entity%s where evm_address is null and length(alias) = 35", suffix);
        var update = String.format("update entity%s set evm_address = :evmAddress where id = :id", suffix);

        jdbcOperations.query(query, rs -> {
            long id = rs.getLong(1);
            byte[] alias = rs.getBytes(2);
            byte[] evmAddress = Utility.aliasToEvmAddress(alias);

            if (evmAddress != null) {
                jdbcOperations.update(update, Map.of("evmAddress", evmAddress, "id", id));
            }
        });
    }

    @Override
    public String getDescription() {
        return "Populates evm_address for accounts with an ECDSA secp256k1 alias";
    }

    @Override
    public MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.58.6");
    }
}
