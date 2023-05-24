/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromBytes;
import static com.hedera.mirror.importer.TestUtils.toContractId;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.EntityHistoryRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hederahashgraph.api.proto.java.ContractID;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class SidecarContractMigrationTest extends IntegrationTest {

    private final ContractRepository contractRepository;
    private final EntityHistoryRepository entityHistoryRepository;
    private final EntityRepository entityRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SidecarContractMigration sidecarContractMigration;

    @Test
    void migrateWhenNull() {
        sidecarContractMigration.migrate(null);
        assertThat(contractRepository.findAll()).isEmpty();
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(entityHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenEmpty() {
        sidecarContractMigration.migrate(Collections.emptyList());
        assertThat(contractRepository.findAll()).isEmpty();
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(entityHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenMissingContract() {
        // given
        var runtimeBytecode = new byte[] {0, 1, 2, 3};
        var contract = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).type(CONTRACT))
                .persist();
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(toContractId(contract))
                .setRuntimeBytecode(fromBytes(runtimeBytecode))
                .build();

        // when
        sidecarContractMigration.migrate(List.of(contractBytecode));

        // then
        assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .returns(runtimeBytecode, Contract::getRuntimeBytecode)
                .returns(contract.getId(), Contract::getId);
    }

    @Test
    void migrateEntityTypeToContract() {
        // given
        var entities = new ArrayList<Entity>();
        var contracts = new ArrayList<Contract>();
        var contractBytecodesMap = new HashMap<Long, ContractBytecode>();
        var contractBytecodeBuilder = ContractBytecode.newBuilder();
        var contractIdBuilder = ContractID.newBuilder();

        for (int i = 0; i < 66000; i++) {
            var entity = domainBuilder.entity().get();
            var entityId = entity.getId();

            entities.add(entity);
            contracts.add(
                    domainBuilder.contract().customize(c -> c.id(entityId)).get());
            contractBytecodesMap.put(
                    entityId,
                    contractBytecodeBuilder
                            .setContractId(contractIdBuilder.setContractNum(entityId))
                            .setRuntimeBytecode(fromBytes(domainBuilder.bytes(4)))
                            .build());
        }

        persistEntities(entities);
        persistContracts(contracts);

        var contractBytecodes = contractBytecodesMap.values().stream().toList();

        // when
        sidecarContractMigration.migrate(contractBytecodes);

        // then
        assertThat(entityRepository.findAll()).extracting(Entity::getType).containsOnly(CONTRACT);
        assertThat(entityHistoryRepository.findAll())
                .extracting(EntityHistory::getType)
                .containsOnly(CONTRACT);

        var contractsIterator = contractRepository.findAll().iterator();
        contractsIterator.forEachRemaining(savedContract -> {
            var contractBytecode = contractBytecodesMap.remove(savedContract.getId());
            assertThat(DomainUtils.toBytes(contractBytecode.getRuntimeBytecode()))
                    .isEqualTo(savedContract.getRuntimeBytecode());
        });
        assertThat(contractsIterator).isExhausted();
        assertThat(contractBytecodesMap).isEmpty();
    }

    // These persist methods are not functionally necessary but greatly speed up bulk insertion.
    private void persistEntities(List<Entity> entities) {
        jdbcTemplate.batchUpdate(
                "insert into entity (id, num, realm, shard, timestamp_range, type) "
                        + "values (?, ?, ?, ?, ?::int8range, ?::entity_type)",
                entities,
                entities.size(),
                (ps, entity) -> {
                    ps.setLong(1, entity.getId());
                    ps.setLong(2, entity.getNum());
                    ps.setLong(3, entity.getRealm());
                    ps.setLong(4, entity.getShard());
                    ps.setString(5, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
                    ps.setString(6, entity.getType().toString());
                });

        jdbcTemplate.batchUpdate(
                "insert into entity_history (id, num, realm, shard, timestamp_range, type) "
                        + "values (?, ?, ?, ?, ?::int8range, ?::entity_type)",
                entities,
                entities.size(),
                (ps, entity) -> {
                    ps.setLong(1, entity.getId());
                    ps.setLong(2, entity.getNum());
                    ps.setLong(3, entity.getRealm());
                    ps.setLong(4, entity.getShard());
                    ps.setString(5, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
                    ps.setString(6, entity.getType().toString());
                });
    }

    private void persistContracts(List<Contract> contracts) {
        jdbcTemplate.batchUpdate(
                "insert into contract (id, runtime_bytecode) values (?, ?)",
                contracts,
                contracts.size(),
                (ps, contract) -> {
                    ps.setLong(1, contract.getId());
                    ps.setBytes(2, contract.getRuntimeBytecode());
                });
    }
}
