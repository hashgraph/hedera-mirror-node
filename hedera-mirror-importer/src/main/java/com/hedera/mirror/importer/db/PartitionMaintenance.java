/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.db;

import lombok.*;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@CustomLog
@Component
@RequiredArgsConstructor
@Profile("v2")
public class PartitionMaintenance {
    private final PartitionMaintenanceService service;

    @Scheduled(initialDelay = 0, fixedRate = 1000000000L)
    //    @Scheduled(cron = "${hedera.mirror.importer.db.maintenance.cron:0 0 0 1 * ?}")
    public void runMaintenance() {
        service.getNextPartitions().forEach(partitionInfo -> {
            try {
                service.createPartition(partitionInfo);
            } catch (Exception e) {
                log.error("Unable to create partition {}", partitionInfo, e);
            }
        });
    }
}
