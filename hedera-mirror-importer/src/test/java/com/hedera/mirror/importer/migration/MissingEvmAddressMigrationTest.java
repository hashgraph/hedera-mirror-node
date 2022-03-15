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

import com.google.common.collect.Range;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.repository.ContractRepository;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.55.4")
class MissingEvmAddressMigrationTest extends IntegrationTest {

    @Resource
    private ContractRepository contractRepository;

    @Resource
    private DomainBuilder domainBuilder;

    @Value("classpath:db/migration/v1/V1.55.5__missing_evm_address.sql")
    private File migrationSql;

    @Test
    void empty() {
        migrate();
        assertThat(contractRepository.findAll()).isEmpty();
        assertThat(findHistory(Contract.class)).isEmpty();
    }

    @Test
    void fillMissingEvmAddress() {
        // given
        List<Contract> expectedCurrentContracts = new ArrayList<>();
        List<Contract> expectedHistoricalContracts = new ArrayList<>();

        // current and historical contract rows without evm address
        var contract1 = persistHistoricalContract(domainBuilder.contract().get(), true, 1L);
        expectedHistoricalContracts.add(contract1);
        contract1 = persistCurrentContract(contract1, false);
        expectedCurrentContracts.add(contract1);

        // the oldest historical contract row has evm address and subsequent contract rows including the current copy
        // are missing evm address
        var contract2 = persistHistoricalContract(domainBuilder.contract().get(), false, 3L);
        byte[] evmAddress = contract2.getEvmAddress();
        expectedHistoricalContracts.add(contract2);
        contract2 = persistHistoricalContract(contract2, true, 3L);
        contract2.setEvmAddress(evmAddress); // add the expected evm address after it's persisted
        expectedHistoricalContracts.add(contract2);
        contract2 = persistCurrentContract(contract2, true);
        contract2.setEvmAddress(evmAddress);
        expectedCurrentContracts.add(contract2);

        // all contract rows have evm address
        var contract3 = persistHistoricalContract(domainBuilder.contract().get(), false, 2L);
        expectedHistoricalContracts.add(contract3);
        contract3 = persistHistoricalContract(contract3, false, 2L);
        expectedHistoricalContracts.add(contract3);
        contract3 = persistCurrentContract(contract3, false);
        expectedCurrentContracts.add(contract3);

        // when
        migrate();

        // then
        assertThat(contractRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedCurrentContracts);
        assertThat(findHistory(Contract.class))
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "evm_address", "timestamp_range")
                .containsExactlyInAnyOrderElementsOf(expectedHistoricalContracts);
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private Contract persistCurrentContract(Contract contract, boolean clearEvmAddress) {
        var clone = TestUtils.clone(contract);
        if (clearEvmAddress) {
            clone.setEvmAddress(null);
        }
        Long lower = clone.getTimestampUpper() == null ? clone.getCreatedTimestamp() : clone.getTimestampUpper();
        clone.setTimestampRange(Range.atLeast(lower));
        contractRepository.save(clone);
        return clone;
    }

    private Contract persistHistoricalContract(Contract contract, boolean clearEvmAddress, long validDuration) {
        var clone = TestUtils.clone(contract);
        if (clearEvmAddress) {
            clone.setEvmAddress(null);
        }

        if (clone.getTimestampUpper() == null) {
            clone.setTimestampUpper(clone.getTimestampLower() + validDuration);
        } else {
            clone.setTimestampLower(clone.getTimestampUpper());
            clone.setTimestampUpper(clone.getTimestampLower() + validDuration);
        }

        jdbcOperations.update(
                "insert into contract_history (id, created_timestamp, evm_address, num, realm, shard, type, " +
                        "timestamp_range) values (?,?,?,?,?,?,?::entity_type,?::int8range)",
                clone.getId(),
                clone.getCreatedTimestamp(),
                clone.getEvmAddress(),
                clone.getNum(),
                clone.getRealm(),
                clone.getShard(),
                clone.getType().toString(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(clone.getTimestampRange())
        );
        return clone;
    }
}
