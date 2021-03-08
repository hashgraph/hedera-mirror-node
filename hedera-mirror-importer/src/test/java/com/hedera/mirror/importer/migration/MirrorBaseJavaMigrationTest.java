package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.io.IOException;
import java.sql.Connection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MirrorBaseJavaMigrationTest {

    @DisplayName("Verify migration")
    @ParameterizedTest(name = "with version {0}, baseline {1} and target {2} produces {3}")
    @CsvSource({
            "0.0.5, 0, 0.0.4, false",
            "0.0.5, 1, 2, false",
            "0.0.5, 0, 2, true",
            "0.0.5, 0, latest, true",
            "1.0.0, , 2, true",
            "1.11.6, 1, 2, true",
            "1.11.6, 1.999.999, 2.999.999, false",
            "2.0.0, 1, 1.999.999, false",
            ", , 2, true",
            ", 1, 2, true"
    })
    void verify(String version, String baseline, String target, boolean result) throws IOException {
        MirrorBaseJavaMigrationSpy migration = new MirrorBaseJavaMigrationSpy(version);
        migration.migrate(new FlywayContext(getConfiguration(baseline, target)));
        assertThat(migration.isMigrationCompleted()).isEqualTo(result);
    }

    private ClassicConfiguration getConfiguration(String baseLine, String target) {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setBaselineVersionAsString(baseLine);
        configuration.setTargetAsString(target);
        return configuration;
    }

    @Data
    @RequiredArgsConstructor
    private class MirrorBaseJavaMigrationSpy extends MirrorBaseJavaMigration {

        private boolean migrationCompleted = false;
        private final String version;

        @Override
        public void doMigrate() {
            migrationCompleted = true;
        }

        @Override
        public MigrationVersion getVersion() {
            return version != null ? MigrationVersion.fromVersion(version) : null;
        }

        @Override
        public String getDescription() {
            return getClass().getSimpleName();
        }
    }

    @AllArgsConstructor
    private class FlywayContext implements Context {
        private final ClassicConfiguration configuration;

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    }
}
