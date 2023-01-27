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
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

abstract class MirrorBaseJavaMigration implements JavaMigration {

    protected final Logger log = LogManager.getLogger(getClass());

    public boolean isBaselineMigration() {
        return false;
    }

    protected MigrationVersion getMinimumVersion() {
        return null;
    }

    @Override
    public void migrate(Context context) throws IOException {
        Configuration configuration = context.getConfiguration();

        if (skipMigration(configuration)) {
            var version = getVersion();
            if (version != null) {
                log.info("Skip migration {} as it does not fall between the baseline: {} and target: {} range",
                        version, configuration.getBaselineVersion(), configuration.getTarget().getVersion());
            }

            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        doMigrate();
        log.info("Ran migration {} in {}.", getDescription(), stopwatch);
    }

    protected abstract void doMigrate() throws IOException;

    /**
     * Determine whether a java migration should be skipped based on version and isIgnoreMissingMigrations setting
     *
     * @param configuration flyway Configuration
     * @return whether it should be skipped or not
     */
    protected boolean skipMigration(Configuration configuration) {
        MigrationVersion current = getVersion();

        // The only case where we should skip a repeatable migration,
        // is when the target migration is not greater or equal to the required one.
        if (current == null && !hasMinimumRequiredVersion(configuration)) {
            return true;
        }

        // Don't skip repeatable migration
        if (current == null) {
            return false;
        }

        MigrationVersion baselineVersion = configuration.getBaselineVersion();
        // Skip when current version is older than baseline
        if (baselineVersion.isNewerThan(current.getVersion())) {
            return true;
        }

        // Skip when current version is newer than target
        MigrationVersion targetVersion = configuration.getTarget();
        return targetVersion != null && current.isNewerThan(targetVersion.getVersion());
    }

    private boolean hasMinimumRequiredVersion(Configuration configuration) {
        MigrationVersion minimumRequiredVersion = getMinimumVersion();
        if (minimumRequiredVersion == null) {
            return true;
        }

        MigrationVersion targetVersion = configuration.getTarget();
        if (targetVersion == null) {
            return true;
        }

        return minimumRequiredVersion.compareTo(targetVersion) <= 0;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean isUndo() {
        return false;
    }
}
