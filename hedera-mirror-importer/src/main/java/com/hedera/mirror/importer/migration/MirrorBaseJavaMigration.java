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

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public abstract class MirrorBaseJavaMigration extends BaseJavaMigration {
    protected final Logger log = LogManager.getLogger(getClass());

    protected abstract void doMigrate() throws IOException;

    @Override
    public void migrate(Context context) throws IOException {
        MigrationVersion current = getVersion();
        if (skipMigrationVersion(current, context.getConfiguration())) {
            log.info("Migration {} will be skipped as it does not fall between the baseline: {} and target: {} range",
                    current,
                    context.getConfiguration().getBaselineVersion(),
                    context.getConfiguration().getTarget().getVersion());
            return;
        }

        doMigrate();
    }

    /**
     * Determine whether a java migration should be skipped based on version and isIgnoreMissingMigrations setting
     *
     * @param current                The current java migration version
     * @param migrationConfiguration flyway Configuration
     * @return
     */
    protected boolean skipMigrationVersion(MigrationVersion current, Configuration migrationConfiguration) {
        // skip when current version is older than baseline
        MigrationVersion baselineVersion = migrationConfiguration.getBaselineVersion();
        if (baselineVersion.isNewerThan(current.getVersion())) {
            return true;
        }

        // skip when current version is newer than target
        MigrationVersion targetVersion = migrationConfiguration.getTarget();
        return targetVersion != null && current.isNewerThan(targetVersion.getVersion());
    }
}
