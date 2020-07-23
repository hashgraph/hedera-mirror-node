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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.FileDataRepository;

@Log4j2
@Named
@RequiredArgsConstructor
public class V1_27_3__Address_Book extends BaseJavaMigration {
    //    private final MirrorProperties mirrorProperties;
//    private final AddressBookRepository addressBookRepository;
//    private final NodeAddressRepository nodeAddressRepository;
    private final FileDataRepository fileDataRepository;
    //    private final AddressBookListener addressBookListener;
    private final NetworkAddressBook networkAddressBook;

    @Override
    public void migrate(Context context) throws Exception {
        // after loading bootstrap address book in sql migration process all file data entries
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicLong currentConsensusTimestamp = new AtomicLong(0);
        AtomicLong fileDataEntries = new AtomicLong(0);

        // starting from consensusTimeStamp = 0 retrieve pages of fileData entries
        List<FileData> fileDataList = getLatestFileData(currentConsensusTimestamp.get(), 100);
        while (CollectionUtils.isEmpty(fileDataList)) {
            fileDataList.forEach(fileData -> {
                fileDataEntries.incrementAndGet();
                // verify fileData has a matching addressBook fileNum
                if (networkAddressBook.isAddressBook(fileData.getFileId())) {
                    // call regular transaction parsing flow to parse and ingest address book entry
                    networkAddressBook
                            .updateFrom(fileData.getConsensusTimestamp(), fileData.getFileData(), fileData.getFileId(),
                                    fileData.getTransactionType() == TransactionTypeEnum.FILECREATE.ordinal());
                    currentConsensusTimestamp.set(fileData.getConsensusTimestamp());
                }
            });

            fileDataList = getLatestFileData(currentConsensusTimestamp.get(), 100);
        }
        log.info("Migration processed {} in {}", fileDataEntries.get(), stopwatch.elapsed(TimeUnit.SECONDS));
    }

    private List<FileData> getLatestFileData(long consensusTimestamp, int pageSize) {
        Pageable pageable = PageRequest.of(0, pageSize);
        return fileDataRepository.findLatest(consensusTimestamp, pageable);
    }
}
