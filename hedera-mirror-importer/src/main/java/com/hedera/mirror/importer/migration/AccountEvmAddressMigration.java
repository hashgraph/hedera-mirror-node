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

import java.io.IOException;
import java.util.Map;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class AccountEvmAddressMigration extends MirrorBaseJavaMigration {

    private final NamedParameterJdbcOperations jdbcOperations;

    @Override
    protected void doMigrate() throws IOException {
        updateAlias(false);
        updateAlias(true);
    }

    // We search for aliases with a length of 35 since ECDSA secp256k1 aliases are 33 bytes w/ 2 bytes for proto prefix
    private void updateAlias(boolean history) {
        String suffix = history ? "_history" : "";
        var query = String.format("select id, alias from entity%s where evm_address is null and length(alias) = 35",
                suffix);
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
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("1.58.7");
    }
}
