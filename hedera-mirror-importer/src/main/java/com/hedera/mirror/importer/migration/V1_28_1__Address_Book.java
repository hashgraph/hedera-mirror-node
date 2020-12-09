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

import com.google.common.base.Stopwatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;

import com.hedera.mirror.importer.addressbook.AddressBookService;

@Log4j2
@Named
public class V1_28_1__Address_Book extends MirrorBaseJavaMigration {
    private final AddressBookService addressBookService;

    public V1_28_1__Address_Book(@Lazy AddressBookService addressBookService) {
        this.addressBookService = addressBookService;
    }

    @Override
    public void migrate(Context context) {
        if (skipMigrationVersion(getVersion(), context.getConfiguration())) {
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        addressBookService.migrate();

        log.info("Successfully processed address book migration in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }
}
