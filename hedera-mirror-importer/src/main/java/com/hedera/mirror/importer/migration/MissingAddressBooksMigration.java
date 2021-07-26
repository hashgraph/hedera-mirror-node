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

import com.google.common.base.Stopwatch;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.context.annotation.Lazy;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class MissingAddressBooksMigration extends MirrorBaseJavaMigration {

    private final EntityProperties entityProperties;
    private final AddressBookService addressBookService;

    @Override
    public Integer getChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    @Override
    public String getDescription() {
        return "Parse valid but unprocessed addressBook file_data rows into valid addressBooks";
    }

    @Override
    public MigrationVersion getVersion() {
        return null; // Repeatable migration
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        MigrationVersion baselineVersion = configuration.getBaselineVersion();
        // skip for mirror node versions prior to v0.33 when migration 1.37.1 was added
        MigrationVersion addressBookServiceEndpointsMigration =  MigrationVersion.fromVersion("1.37.1");

        return addressBookServiceEndpointsMigration.isNewerThan(baselineVersion.getVersion());
    }

    @Override
    protected void doMigrate() {
        log.info("Parsing address book file data rows");
        Stopwatch stopwatch = Stopwatch.createStarted();
        AddressBook addressBook = addressBookService.migrate();

        log.info("Successfully migrated address book file data in {}. Latest addressBook is {}", stopwatch, addressBook);
    }
}
