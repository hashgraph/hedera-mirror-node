/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.repository.AddressBookServiceEndpointRepository;
import jakarta.inject.Named;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.context.annotation.Lazy;

@Named
public class MissingAddressBooksMigration extends RepeatableMigration {

    private final AddressBookService addressBookService;
    private final AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @Lazy
    public MissingAddressBooksMigration(
            AddressBookService addressBookService,
            AddressBookServiceEndpointRepository addressBookServiceEndpointRepository,
            MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.addressBookService = addressBookService;
        this.addressBookServiceEndpointRepository = addressBookServiceEndpointRepository;
    }

    @Override
    public String getDescription() {
        return "Parse valid but unprocessed addressBook file_data rows into valid addressBooks";
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return true;
        }

        // skip when no address books with service endpoint exist. Allow normal flow migration to do initial population
        long serviceEndpointCount = 0;
        try {
            serviceEndpointCount = addressBookServiceEndpointRepository.count();
        } catch (Exception ex) {
            // catch ERROR: relation "address_book_service_endpoint" does not exist
            // this will occur in migration version before v1.37.1 where service endpoints were not supported by proto
            log.info("Error checking service endpoints: {}", ex.getMessage());
        }
        return serviceEndpointCount < 1;
    }

    @Override
    protected void doMigrate() {
        addressBookService.migrate();
    }
}
