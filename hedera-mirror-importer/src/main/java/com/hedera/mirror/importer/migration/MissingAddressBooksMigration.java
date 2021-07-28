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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.context.annotation.Lazy;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.repository.AddressBookServiceEndpointRepository;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class MissingAddressBooksMigration extends MirrorBaseJavaMigration {

    private final AddressBookService addressBookService;
    private final AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @Override
    public Integer getChecksum() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Parse valid but unprocessed addressBook file_data rows into valid addressBooks";
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        // skip when no address books with service endpoint exist. Allow normal flow migration to do initial population
        long serviceEndpointCount = 0;
        try {
            serviceEndpointCount = addressBookServiceEndpointRepository.count();
        } catch (Exception ex) {
            // catch ERROR: relation "address_book_service_endpoint" does not exist
            // this will occur in migration version before v1.37.1 where service endpoints were not supported by proto
            log.error("Error checking service endpoints: {}", ex);
        }
        return serviceEndpointCount < 1;
    }

    @Override
    protected void doMigrate() {
        addressBookService.migrate();
    }
}
