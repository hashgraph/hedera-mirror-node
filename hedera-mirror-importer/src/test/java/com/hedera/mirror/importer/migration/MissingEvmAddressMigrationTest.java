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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;

@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.55.4")
class MissingEvmAddressMigrationTest extends IntegrationTest {

    private static final String TABLE_IDS = "id";

    private static final String TABLE_NAME = "contract";

    @Value("classpath:db/migration/v1/V1.55.5__missing_evm_address.sql")
    private File migrationSql;

    private final AtomicLong id = new AtomicLong(10);

    private final AtomicLong timestamp = new AtomicLong(200);

    @Test
    void empty() {
        migrate();
        assertThat(findEntity(MigrationContract.class, TABLE_IDS, TABLE_NAME)).isEmpty();
        assertThat(findHistory(MigrationContract.class, TABLE_IDS, TABLE_NAME)).isEmpty();
    }

    @Test
    void fillMissingEvmAddress() {
        // given
        List<MigrationContract> expectedCurrentContracts = new ArrayList<>();
        List<MigrationContract> expectedHistoricalContracts = new ArrayList<>();

        // current and historical contract rows without evm address
        var contract1 = persistHistoricalContract(contract(), true, 1L);
        expectedHistoricalContracts.add(contract1);
        contract1 = persistCurrentContract(contract1, false);
        expectedCurrentContracts.add(contract1);

        // the oldest historical contract row has evm address and subsequent contract rows including the current copy
        // are missing evm address
        var contract2 = persistHistoricalContract(contract(), false, 3L);
        byte[] evmAddress = contract2.getEvmAddress();
        expectedHistoricalContracts.add(contract2);
        contract2 = persistHistoricalContract(contract2, true, 3L);
        contract2.setEvmAddress(evmAddress); // add the expected evm address after it's persisted
        expectedHistoricalContracts.add(contract2);
        contract2 = persistCurrentContract(contract2, true);
        contract2.setEvmAddress(evmAddress); // add the expected evm address after it's persisted
        expectedCurrentContracts.add(contract2);

        // all contract rows have evm address
        var contract3 = persistHistoricalContract(contract(), false, 2L);
        expectedHistoricalContracts.add(contract3);
        contract3 = persistHistoricalContract(contract3, false, 2L);
        expectedHistoricalContracts.add(contract3);
        contract3 = persistCurrentContract(contract3, false);
        expectedCurrentContracts.add(contract3);

        // when
        migrate();

        // then
        assertThat(findEntity(MigrationContract.class, TABLE_IDS, TABLE_NAME))
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "evm_address", "timestamp_range")
                .containsExactlyInAnyOrderElementsOf(expectedCurrentContracts);
        assertThat(findHistory(MigrationContract.class, TABLE_IDS, TABLE_NAME))
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "evm_address", "timestamp_range")
                .containsExactlyInAnyOrderElementsOf(expectedHistoricalContracts);
    }

    private MigrationContract clone(MigrationContract contract) {
        return contract.toBuilder().build();
    }

    private MigrationContract contract() {
        var contractId = id.getAndIncrement();
        var createdTimestamp = timestamp.getAndIncrement();
        return MigrationContract.builder()
                .createdTimestamp(createdTimestamp)
                .evmAddress(domainBuilder.evmAddress())
                .id(contractId)
                .num(contractId)
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(CONTRACT)
                .build();
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void persistContract(MigrationContract contract) {
        String table = TABLE_NAME;
        if (contract.getTimestampRange().hasUpperBound()) {
            table = String.format("%s_history", TABLE_NAME);
        }
        String sql = String.format("insert into %s (id,created_timestamp,evm_address,num,realm,shard,type," +
                "timestamp_range) values (?,?,?,?,?,?,?::entity_type,?::int8range)", table);
        jdbcOperations.update(
                sql,
                contract.getId(),
                contract.getCreatedTimestamp(),
                contract.getEvmAddress(),
                contract.getNum(),
                contract.getRealm(),
                contract.getShard(),
                contract.getType().toString(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(contract.getTimestampRange())
        );
    }

    private MigrationContract persistCurrentContract(MigrationContract contract, boolean clearEvmAddress) {
        contract = clone(contract);
        if (clearEvmAddress) {
            contract.setEvmAddress(null);
        }

        var lower = contract.getTimestampUpper() == null ? contract.getCreatedTimestamp() :
                contract.getTimestampUpper();
        contract.setTimestampRange(Range.atLeast(lower));

        persistContract(contract);
        return contract;
    }

    private MigrationContract persistHistoricalContract(MigrationContract contract, boolean clearEvmAddress,
                                                        long validDuration) {
        contract = clone(contract);
        if (clearEvmAddress) {
            contract.setEvmAddress(null);
        }

        if (contract.getTimestampUpper() == null) {
            contract.setTimestampUpper(contract.getTimestampLower() + validDuration);
        } else {
            contract.setTimestampLower(contract.getTimestampUpper());
            contract.setTimestampUpper(contract.getTimestampLower() + validDuration);
        }

        persistContract(contract);
        return contract;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    private static class MigrationContract implements History {
        private long createdTimestamp;
        private byte[] evmAddress;
        private long id;
        private long num;
        private long realm;
        private long shard;
        private Range<Long> timestampRange;
        private EntityType type;
    }
}
