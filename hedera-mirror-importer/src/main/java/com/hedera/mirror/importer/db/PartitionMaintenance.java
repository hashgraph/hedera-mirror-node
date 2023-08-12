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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import lombok.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@CustomLog
@Component
@Profile("v2")
public class PartitionMaintenance {
    private static final String RUN_MAINTENANCE_QUERY = "CALL mirror_node_create_partitions()";
    private final JdbcTemplate jdbcTemplate;
    private final Timer maintenanceMetric;

    public PartitionMaintenance(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.maintenanceMetric = Timer.builder(getClass().getCanonicalName())
                .description("The duration in seconds it took to create new partitions")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${hedera.mirror.importer.db.maintenance.cron:0 0 0 * * ?}")
    @EventListener(ApplicationReadyEvent.class)
    @Retryable
    public void runMaintenance() {
        log.info("Running partition maintenance");
        Instant start = Instant.now();
        jdbcTemplate.execute(RUN_MAINTENANCE_QUERY);
        log.info("Partition maintenance completed successfully");
        this.maintenanceMetric.record(Duration.between(start, Instant.now()));
    }
}
