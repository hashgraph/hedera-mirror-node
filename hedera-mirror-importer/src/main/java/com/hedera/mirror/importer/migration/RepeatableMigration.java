/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.data.util.Version;

abstract class RepeatableMigration extends MirrorBaseJavaMigration {

    private static final MigrationProperties DEFAULT_MIGRATION_PROPERTIES = new MigrationProperties();

    protected final MigrationProperties migrationProperties;
    private static final AtomicReference<Version> lastVersion = new AtomicReference<>();

    protected RepeatableMigration(Map<String, MigrationProperties> migrationPropertiesMap) {
        String propertiesKey = StringUtils.uncapitalize(getClass().getSimpleName());
        migrationProperties = migrationPropertiesMap.getOrDefault(propertiesKey, DEFAULT_MIGRATION_PROPERTIES);
    }

    @Override
    public Integer getChecksum() {
        return migrationProperties.getChecksum();
    }

    @Override
    public final MigrationVersion getVersion() {
        return null; // Repeatable migration
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return true;
        }

        return super.skipMigration(configuration);
    }

    protected boolean shouldRerun(RecordFile streamFile, Version version, RecordFileRepository repository) {
        if ((streamFile.getHapiVersion()).isGreaterThanOrEqualTo(version) && lastVersion.get() == null) {
            var latestFile = repository.findLatestWithOffset(1).orElse(null);
            if (latestFile != null && latestFile.getHapiVersion().isLessThan(version)) {
                lastVersion.set(streamFile.getHapiVersion());
                return true;
            }
        }
        return false;
    }
}
