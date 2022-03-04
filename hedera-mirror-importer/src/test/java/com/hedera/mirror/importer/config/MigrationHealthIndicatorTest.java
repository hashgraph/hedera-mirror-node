package com.hedera.mirror.importer.config;

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

import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class MigrationHealthIndicatorTest {

    private final MigrationHealthIndicator migrationHealthIndicator = new MigrationHealthIndicator();

    @Test
    void healthDownWhenMigrationsRunning() {
        assertThat(migrationHealthIndicator.health())
                .isNotNull()
                .extracting(Health::getStatus)
                .isEqualTo(Status.DOWN);
    }

    @Test
    void healthUpWhenMigrationsComplete() {
        migrationHealthIndicator.handle(Event.AFTER_MIGRATE, null);
        assertThat(migrationHealthIndicator.health())
                .isNotNull()
                .extracting(Health::getStatus)
                .isEqualTo(Status.UP);
    }

    @Test
    void supports() {
        assertThat(migrationHealthIndicator.supports(null, null)).isFalse();
        assertThat(migrationHealthIndicator.supports(Event.AFTER_BASELINE, null)).isFalse();
        assertThat(migrationHealthIndicator.supports(Event.AFTER_MIGRATE, null)).isTrue();
    }
}
