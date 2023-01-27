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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class RepeatableMigrationTest {

    private final Context defaultContext = new Context() {
        @Override
        public Configuration getConfiguration() {
            return new FluentConfiguration().baselineVersion("1.0.0").target("100.0.0");
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    };

    @Test
    void checksum() {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setChecksum(2);
        var migration = new TestMigration(Map.of("testMigration", migrationProperties));
        migrate(migration);
        assertThat(migration)
                .returns(2, TestMigration::getChecksum)
                .returns(true, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @Test
    void caseInsensitivity() {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setChecksum(4);
        CaseInsensitiveMap<String, MigrationProperties> migrationMap = new CaseInsensitiveMap<>();
        migrationMap.put("TESTMIGRATION", migrationProperties);
        var migration = new TestMigration(migrationMap);
        migrate(migration);
        assertThat(migration)
                .returns(4, TestMigration::getChecksum)
                .returns(true, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);

    }

    @Test
    void defaultMigrationProperties() {
        var migration = new TestMigration(Collections.emptyMap());
        migrate(migration);
        assertThat(migration)
                .returns(1, TestMigration::getChecksum)
                .returns(true, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @Test
    void disabled() {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setEnabled(false);
        var migration = new TestMigration(Map.of("testMigration", migrationProperties));
        migrate(migration);
        assertThat(migration)
                .returns(1, TestMigration::getChecksum)
                .returns(false, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @SneakyThrows
    private void migrate(RepeatableMigration migration) {
        migration.migrate(defaultContext);
    }

    private static class TestMigration extends RepeatableMigration {

        @Getter
        private boolean migrated = false;

        public TestMigration(Map<String, MigrationProperties> migrationPropertiesMap) {
            super(migrationPropertiesMap);
        }

        @Override
        protected void doMigrate() {
            migrated = true;
        }

        @Override
        public String getDescription() {
            return "Test migration";
        }
    }
}
