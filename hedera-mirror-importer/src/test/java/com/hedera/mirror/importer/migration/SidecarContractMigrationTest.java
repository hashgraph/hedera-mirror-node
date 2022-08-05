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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.services.stream.proto.ContractBytecode;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class SidecarContractMigrationTest extends IntegrationTest {

    private final ContractRepository contractRepository;
    private final EntityRepository entityRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SidecarContractMigration sidecarContractMigration;

    @Test
    void migrateWhenNull() {
        sidecarContractMigration.migrate(null);
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(contractRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenEmpty() {
        sidecarContractMigration.migrate(Collections.emptyList());
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(contractRepository.findAll()).isEmpty();
    }

    @Test
    void migrateRuntimeBytecode() {
        // given
        var entityRepository = mock(EntityRepository.class);
        var sidecarMigration = new SidecarContractMigration(contractRepository, entityRepository);

        var contract1 = domainBuilder.contract().persist();
        var contract2 = domainBuilder.contract().persist();
        var contract3 = domainBuilder.contract().persist();
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(ContractID.newBuilder().setContractNum(contract1.getId()).build())
                .setRuntimeBytecode(ByteString.copyFrom(new byte[] {1}))
                .build();
        var contractBytecode2 = ContractBytecode.newBuilder()
                .setContractId(ContractID.newBuilder().setContractNum(contract2.getId()).build())
                .setRuntimeBytecode(ByteString.copyFrom(new byte[] {2}))
                .build();
        var contractBytecode3 = ContractBytecode.newBuilder()
                .setContractId(ContractID.newBuilder().setContractNum(contract3.getId()).build())
                .setRuntimeBytecode(ByteString.EMPTY)
                .build();
        var bytecodes = List.of(contractBytecode, contractBytecode2, contractBytecode3);

        // when
        sidecarMigration.migrate(bytecodes);

        // then
        var contracts = contractRepository.findAll().iterator();
        bytecodes.stream()
                .map(ContractBytecode::getRuntimeBytecode)
                .forEach(runtimeBytecode -> {
                    assertThat(contracts).hasNext();
                    assertThat(contracts.next())
                            .returns(DomainUtils.toBytes(runtimeBytecode),
                                    Contract::getRuntimeBytecode);
                });
    }

    @Test
    void migrateEntityTypeToContract() {
        // given
        var contractRepository = mock(ContractRepository.class);
        var sidecarMigration = new SidecarContractMigration(contractRepository, entityRepository);

        var entities = new ArrayList<Entity>();
        var contractBytecodes = new ArrayList<ContractBytecode>();
        var contractBytecodeBuilder = ContractBytecode.newBuilder();
        var contractIdBuilder = ContractID.newBuilder();
        for (int i = 0; i < 66000; i++) {
            var entity = domainBuilder.entity().get();
            entities.add(entity);
            contractBytecodes.add(contractBytecodeBuilder
                    .setContractId(contractIdBuilder.setContractNum(entity.getId())).build());
        }
        persistEntities(entities);

        // when
        sidecarMigration.migrate(contractBytecodes);

        // then
        assertThat(entityRepository.findAll())
                .extracting(Entity::getType).containsOnly(CONTRACT);
    }

    private void persistEntities(List<Entity> entities) {
        jdbcTemplate.batchUpdate(
                "insert into entity (decline_reward, id, memo, num, realm, shard, timestamp_range, type) " +
                        "values (?, ?, ?, ?, ?, ?, ?::int8range, ?::entity_type)",
                entities,
                entities.size(),
                (ps, entity) -> {
                    ps.setBoolean(1, entity.getDeclineReward());
                    ps.setLong(2, entity.getId());
                    ps.setString(3, entity.getMemo());
                    ps.setLong(4, entity.getNum());
                    ps.setLong(5, entity.getRealm());
                    ps.setLong(6, entity.getShard());
                    ps.setString(7, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
                    ps.setString(8, entity.getType().toString());
                }
        );
    }
}
