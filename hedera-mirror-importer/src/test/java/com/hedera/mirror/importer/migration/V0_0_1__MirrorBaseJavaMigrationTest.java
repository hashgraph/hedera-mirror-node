package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.junit.jupiter.api.Test;

// flyway requires class name conforms with naming convention
public class V0_0_1__MirrorBaseJavaMigrationTest extends MirrorBaseJavaMigration {
    @Override
    public void doMigrate() {
        // do nothing
    }

    @Test
    void verifySkipMigrationVersionCurrentEmpty() {
        assertThat(skipMigrationVersion(MigrationVersion.EMPTY, getConfiguration("1", "2"))).isTrue();
    }

    @Test
    void verifySkipMigrationVersionBaselineEmpty() {
        assertThat(skipMigrationVersion(MigrationVersion.fromVersion("1.0.0"),
                getConfiguration(MigrationVersion.EMPTY.getVersion(), "2"))).isFalse();
    }

    @Test
    void verifySkipMigrationVersionCurrentAndBaselineEmpty() {
        assertThat(skipMigrationVersion(MigrationVersion.EMPTY,
                getConfiguration(MigrationVersion.EMPTY.getVersion(), "2"))).isFalse();
    }

    @Test
    void verifySkipMigrationVersionBelowRange() {
        assertThat(skipMigrationVersion(MigrationVersion.fromVersion("1.11.6"),
                getConfiguration("1.999.999", "2.999.999"))).isTrue();
    }

    @Test
    void verifySkipMigrationVersionInRange() {
        assertThat(skipMigrationVersion(MigrationVersion.fromVersion("1.11.6"), getConfiguration("1", "2"))).isFalse();
    }

    @Test
    void verifySkipMigrationVersionAboveRange() {
        assertThat(skipMigrationVersion(MigrationVersion.fromVersion("2.0.0"), getConfiguration("1", "1.999.999")))
                .isTrue();
    }

    private ClassicConfiguration getConfiguration(String baseLine, String target) {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setBaselineVersionAsString(baseLine);
        configuration.setTargetAsString(target);
        return configuration;
    }
}
