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

package com.hedera.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractAsyncJavaMigrationTest<T extends AsyncJavaMigration<?>> extends ImporterIntegrationTest {

    private static final String RESET_CHECKSUM_SQL = "update flyway_schema_history set checksum = -1 where script = ?";

    private static final String SELECT_LAST_CHECKSUM_SQL =
            """
            select (
              select checksum from flyway_schema_history
              where script = ?
              order by installed_rank desc
              limit 1
            )
            """;

    protected abstract T getMigration();

    protected abstract Class<T> getMigrationClass();

    @AfterEach
    @BeforeEach
    void resetChecksum() {
        jdbcOperations.update(RESET_CHECKSUM_SQL, getScript());
    }

    protected void waitForCompletion() {
        await().atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(isMigrationCompleted()).isTrue());
    }

    private String getScript() {
        return getMigrationClass().getName();
    }

    private boolean isMigrationCompleted() {
        var actual = jdbcOperations.queryForObject(SELECT_LAST_CHECKSUM_SQL, Integer.class, getScript());
        return Objects.equals(actual, getMigration().getSuccessChecksum());
    }
}
