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

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.leader.Leader;
import jakarta.inject.Named;
import lombok.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@Profile("v2")
@RequiredArgsConstructor
public class PartitionMaintenance {
    private static final String RUN_MAINTENANCE_QUERY = "CALL mirror_node_create_partitions()";

    @Owner
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${hedera.mirror.importer.db.maintenance.cron:0 0 0 * * ?}")
    @EventListener(ApplicationReadyEvent.class)
    @Retryable
    @Leader
    public void runMaintenance() {
        log.info("Running partition maintenance");
        Stopwatch stopwatch = Stopwatch.createStarted();
        jdbcTemplate.execute(RUN_MAINTENANCE_QUERY);
        log.info("Partition maintenance completed successfully in {}", stopwatch);
    }
}
