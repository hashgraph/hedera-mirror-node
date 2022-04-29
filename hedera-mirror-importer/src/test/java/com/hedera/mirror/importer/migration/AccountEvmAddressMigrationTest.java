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

import static com.hedera.mirror.importer.util.UtilityTest.EVM_ADDRESS;
import static com.hedera.mirror.importer.util.UtilityTest.ALIAS_ECDSA_SECP256K1;
import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.58.5")
class AccountEvmAddressMigrationTest extends IntegrationTest {

    private final JdbcOperations jdbcOperations;
    private final AccountEvmAddressMigration migration;

    @Test
    void noAliases() throws Exception {
        insertEntity(1L, null, null);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isNull();
    }

    @Test
    void evmAddressAlreadySet() throws Exception {
        insertEntity(1L, ALIAS_ECDSA_SECP256K1, EVM_ADDRESS);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isEqualTo(EVM_ADDRESS);
    }

    @Test
    void aliasEd25519() throws Exception {
        byte[] aliasEd25519 = Hex.decode("1220000038746a20d630ceb81a24bd43798159108ec144e185c1c60a5e39fb933e2a");
        insertEntity(1L, aliasEd25519, null);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isNull();
    }

    @Test
    void evmAddressSet() throws Exception {
        insertEntity(1L, ALIAS_ECDSA_SECP256K1, null);
        insertEntity(2L, ALIAS_ECDSA_SECP256K1, null);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isEqualTo(EVM_ADDRESS);
        assertThat(findEvmAddress(2L)).isEqualTo(EVM_ADDRESS);
    }

    @Test
    void history() throws Exception {
        insertEntityHistory(1L, ALIAS_ECDSA_SECP256K1, null);
        insertEntityHistory(2L, ALIAS_ECDSA_SECP256K1, EVM_ADDRESS);
        migration.doMigrate();
        assertThat(findHistoryEvmAddress(1L)).isEqualTo(EVM_ADDRESS);
        assertThat(findHistoryEvmAddress(2L)).isEqualTo(EVM_ADDRESS);
    }

    private byte[] findEvmAddress(long id) {
        return jdbcOperations.queryForObject("select evm_address from entity where id = ?", byte[].class, id);
    }

    private byte[] findHistoryEvmAddress(long id) {
        return jdbcOperations.queryForObject("select evm_address from entity_history where id = ?", byte[].class, id);
    }

    private void insertEntity(long id, byte[] alias, byte[] evmAddress) {
        doInsertEntity(false, id, alias, evmAddress);
    }

    private void insertEntityHistory(long id, byte[] alias, byte[] evmAddress) {
        doInsertEntity(true, id, alias, evmAddress);
    }

    private void doInsertEntity(boolean history, long id, byte[] alias, byte[] evmAddress) {
        String suffix = history ? "_history" : "";
        String sql = String.format("insert into entity%s (alias, created_timestamp, evm_address, id, num, realm, " +
                "shard, timestamp_range, type) values (?,1,?,?,?,0,0,'[1,)','ACCOUNT')", suffix);
        jdbcOperations.update(sql, alias, evmAddress, id, id);
    }
}
