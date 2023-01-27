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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.EntityHistoryRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.services.stream.proto.ContractBytecode;

@CustomLog
@Named
@RequiredArgsConstructor
public class SidecarContractMigration {

    private static final int BATCH_SIZE = 100;
    private static final int IN_CLAUSE_LIMIT = 32767;
    private static final String UPDATE_RUNTIME_BYTECODE_SQL = "update contract set runtime_bytecode = ? where id = ?";

    private final EntityHistoryRepository entityHistoryRepository;
    private final EntityRepository entityRepository;
    private final JdbcOperations jdbcOperations;

    public void migrate(List<ContractBytecode> contractBytecodes) {
        if (contractBytecodes == null || contractBytecodes.isEmpty()) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var sidecarMigrationContractIds = new ArrayList<Long>();
        for (var contractBytecode : contractBytecodes) {
            long entityId = EntityId.of(contractBytecode.getContractId()).getId();
            sidecarMigrationContractIds.add(entityId);
        }

        jdbcOperations.batchUpdate(
                UPDATE_RUNTIME_BYTECODE_SQL,
                contractBytecodes,
                BATCH_SIZE,
                (ps, contractBytecode) -> {
                    ps.setBytes(1, DomainUtils.toBytes(contractBytecode.getRuntimeBytecode()));
                    ps.setLong(2, EntityId.of(contractBytecode.getContractId()).getId());
                });

        int count = 0;
        var partitions = Iterables.partition(sidecarMigrationContractIds, IN_CLAUSE_LIMIT);
        for (var partition : partitions) {
            count += entityRepository.updateContractType(partition);
            entityHistoryRepository.updateContractType(partition);
        }
        log.info("Migrated {} sidecar contract entities in {}", count, stopwatch);
    }
}
