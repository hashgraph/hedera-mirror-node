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
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class MirrorBaseJavaMigrationTest {
    V0_0_5__MirrorBaseJavaMigration migration = new V0_0_5__MirrorBaseJavaMigration();

    @Test
    void verifySkipMigrationVersionCurrentEmpty() {
        assertThat(migration.skipMigrationVersion(MigrationVersion.EMPTY, getConfiguration("1", "2"))).isTrue();
    }

    @Test
    void verifySkipMigrationVersionBaselineEmpty() {
        assertThat(migration.skipMigrationVersion(MigrationVersion.fromVersion("1.0.0"),
                getConfiguration(MigrationVersion.EMPTY.getVersion(), "2"))).isFalse();
    }

    @Test
    void verifySkipMigrationVersionCurrentAndBaselineEmpty() {
        assertThat(migration.skipMigrationVersion(MigrationVersion.EMPTY,
                getConfiguration(MigrationVersion.EMPTY.getVersion(), "2"))).isFalse();
    }

    @Test
    void verifySkipMigrationVersionBelowRange() {
        assertThat(migration.skipMigrationVersion(MigrationVersion.fromVersion("1.11.6"),
                getConfiguration("1.999.999", "2.999.999"))).isTrue();
    }

    @Test
    void verifySkipMigrationVersionInRange() {
        assertThat(migration.skipMigrationVersion(MigrationVersion.fromVersion("1.11.6"), getConfiguration("1", "2")))
                .isFalse();
    }

    @Test
    void verifySkipMigrationVersionAboveRange() {
        assertThat(migration
                .skipMigrationVersion(MigrationVersion.fromVersion("2.0.0"), getConfiguration("1", "1.999.999")))
                .isTrue();
    }

    @Test
    void verifyMigrateCalled() throws IOException {
        V0_0_5__MirrorBaseJavaMigration newMigration = new V0_0_5__MirrorBaseJavaMigration();
        newMigration.migrate(new FlywayContext(getConfiguration("0", "2")));
        assertThat(newMigration.isMigrationCompleted()).isTrue();
    }

    @Test
    void verifyMigrateSkippedWhenBelowRange() throws IOException {
        V0_0_5__MirrorBaseJavaMigration newMigration = new V0_0_5__MirrorBaseJavaMigration();
        newMigration.migrate(new FlywayContext(getConfiguration("1", "2")));
        assertThat(newMigration.isMigrationCompleted()).isFalse();
    }

    @Test
    void verifyMigrateSkippedWhenAboveRange() throws IOException {
        V0_0_5__MirrorBaseJavaMigration newMigration = new V0_0_5__MirrorBaseJavaMigration();
        newMigration.migrate(new FlywayContext(getConfiguration("0", "0.0.4")));
        assertThat(newMigration.isMigrationCompleted()).isFalse();
    }

    @Test
    void verifyMigrateWhenLatest() throws IOException {
        V0_0_5__MirrorBaseJavaMigration newMigration = new V0_0_5__MirrorBaseJavaMigration();
        newMigration.migrate(new FlywayContext(getConfiguration("0", "latest")));
        assertThat(newMigration.isMigrationCompleted()).isTrue();
    }

    private ClassicConfiguration getConfiguration(String baseLine, String target) {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setBaselineVersionAsString(baseLine);
        configuration.setTargetAsString(target);
        return configuration;
    }

    // flyway requires class name conforms with default naming convention e.g. V1_2_3__Description
    @Data
    private class V0_0_5__MirrorBaseJavaMigration extends MirrorBaseJavaMigration {
        private boolean migrationCompleted = false;

        @Override
        public void doMigrate() {
            migrationCompleted = true;
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
