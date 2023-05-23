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

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.EntityHistoryRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.services.stream.proto.ContractBytecode;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;

@CustomLog
@Named
@RequiredArgsConstructor
public class SidecarContractMigration {

    private static final int BATCH_SIZE = 100;
    private static final int IN_CLAUSE_LIMIT = 32767;
    private static final String UPDATE_RUNTIME_BYTECODE_SQL =
            """
            insert into contract (id, runtime_bytecode)
            values (?, ?)
            on conflict (id)
            do update set runtime_bytecode = excluded.runtime_bytecode""";

    private final EntityHistoryRepository entityHistoryRepository;
    private final EntityRepository entityRepository;
    private final JdbcOperations jdbcOperations;

    public void migrate(List<ContractBytecode> contractBytecodes) {
        if (contractBytecodes == null || contractBytecodes.isEmpty()) {
            return;
        }

        var contractIds = new HashSet<Long>();
        var stopwatch = Stopwatch.createStarted();

        jdbcOperations.batchUpdate(
                UPDATE_RUNTIME_BYTECODE_SQL, contractBytecodes, BATCH_SIZE, (ps, contractBytecode) -> {
                    ps.setLong(1, EntityId.of(contractBytecode.getContractId()).getId());
                    ps.setBytes(2, DomainUtils.toBytes(contractBytecode.getRuntimeBytecode()));
                });

        // We only need to update entity history's type since ContractUpdateTransactionHandler will upsert the entity
        // with the correct type
        for (var contractBytecode : contractBytecodes) {
            long entityId = EntityId.of(contractBytecode.getContractId()).getId();
            contractIds.add(entityId);

            if (contractIds.size() >= IN_CLAUSE_LIMIT) {
                updateContractType(contractIds);
            }
        }

        updateContractType(contractIds);
        log.info("Migrated {} sidecar contract entities in {}", contractBytecodes.size(), stopwatch);
    }

    private void updateContractType(Collection<Long> contractIds) {
        if (!contractIds.isEmpty()) {
            entityRepository.updateContractType(contractIds);
            entityHistoryRepository.updateContractType(contractIds);
            contractIds.clear();
        }
    }
}
