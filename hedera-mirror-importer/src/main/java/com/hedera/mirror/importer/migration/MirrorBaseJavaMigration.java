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

import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

@Log4j2
public abstract class MirrorBaseJavaMigration extends BaseJavaMigration {

    public abstract void doMigrate() throws Exception;

    @Override
    public void migrate(Context context) throws Exception {
        MigrationVersion current = getVersion();
        if (skipMigrationVersion(current, context.getConfiguration())) {
            log.trace("Migration {} will be skipped as it precedes baseline version {}",
                    current,
                    context.getConfiguration().getBaselineVersion());
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
    private boolean skipMigrationVersion(MigrationVersion current, Configuration migrationConfiguration) {
        MigrationVersion baselineVersion = migrationConfiguration.getBaselineVersion();
        if (baselineVersion.isNewerThan(current.getVersion()) && migrationConfiguration.isIgnoreMissingMigrations()) {
            return true;
        }

        return false;
    }
}
