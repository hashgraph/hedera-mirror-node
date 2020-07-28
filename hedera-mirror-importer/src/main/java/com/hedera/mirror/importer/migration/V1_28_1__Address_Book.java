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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.addressbook.AddressBookServiceImpl;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.repository.FileDataRepository;

@Log4j2
@Named
public class V1_28_1__Address_Book extends BaseJavaMigration {
    private final FileDataRepository fileDataRepository;
    private final AddressBookService addressBookService;

    // There's a circular dependency of Flyway -> this -> Repositories/JdbcOperations -> Flyway, so use @Lazy to
    // break it.
    // Correct way is to not use repositories and construct manually: new JdbcTemplate(context.getConnection())
    public V1_28_1__Address_Book(@Lazy AddressBookService addressBookService,
                                 @Lazy FileDataRepository fileDataRepository) {
        this.addressBookService = addressBookService;
        this.fileDataRepository = fileDataRepository;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicLong currentConsensusTimestamp = new AtomicLong(0);
        AtomicLong fileDataEntries = new AtomicLong(0);

        // starting from consensusTimeStamp = 0 retrieve pages of fileData entries
        int pageSize = 1000; // option to parameterize this
        List<FileData> fileDataList = getLatestFileData(currentConsensusTimestamp.get(), pageSize);
        while (!CollectionUtils.isEmpty(fileDataList)) {
            fileDataList.forEach(fileData -> {
                fileDataEntries.incrementAndGet();
                // call regular transaction parsing flow to parse and ingest address book entry
                addressBookService.update(fileData);
                currentConsensusTimestamp.set(fileData.getConsensusTimestamp());
            });

            fileDataList = getLatestFileData(currentConsensusTimestamp.get(), pageSize);
        }
        log.info("Migration processed {} in {} ms", fileDataEntries.get(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private List<FileData> getLatestFileData(long consensusTimestamp, int pageSize) {
        Pageable pageable = PageRequest.of(0, pageSize);
        List<FileData> fileDataList = fileDataRepository
                .findByConsensusTimestampAfterAndEntityIdInOrderByConsensusTimestampAsc(consensusTimestamp, List
                        .of(AddressBookServiceImpl.ADDRESS_BOOK_101_ENTITY_ID,
                                AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID), pageable);

        log.info("Retrieved {} file_data rows for address book processing", fileDataList.size());
        return fileDataList;
    }
}
