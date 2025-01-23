/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.configuration.Configuration;

abstract class ConfigurableJavaMigration extends AbstractJavaMigration {

    private static final String ASYNC = "async";
    private static final MigrationProperties DEFAULT_MIGRATION_PROPERTIES = new MigrationProperties();

    protected final MigrationProperties migrationProperties;

    protected ConfigurableJavaMigration(Map<String, MigrationProperties> migrationPropertiesMap) {
        String propertiesKey = StringUtils.uncapitalize(getClass().getSimpleName());
        var defaultProperties = DEFAULT_MIGRATION_PROPERTIES;
        if (this instanceof AsyncJavaMigration<?>) {
            defaultProperties = migrationPropertiesMap.getOrDefault(ASYNC, DEFAULT_MIGRATION_PROPERTIES);
        }
        migrationProperties = migrationPropertiesMap.getOrDefault(propertiesKey, defaultProperties);
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return true;
        }

        return super.skipMigration(configuration);
    }
}
