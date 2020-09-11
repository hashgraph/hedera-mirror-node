package com.hedera.mirror.importer.downloader.record;

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

import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class RecordFileDownloader extends Downloader {

    private final RecordFileRepository recordFileRepository;

    public RecordFileDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            AddressBookService addressBookService, RecordDownloaderProperties downloaderProperties,
            TransactionTemplate transactionTemplate, MeterRegistry meterRegistry, RecordFileRepository recordFileRepository) {
        super(s3Client, applicationStatusRepository, addressBookService, downloaderProperties, transactionTemplate, meterRegistry);
        this.recordFileRepository = recordFileRepository;
    }

    @Leader
    @Scheduled(fixedRateString = "${hedera.mirror.importer.downloader.record.frequency:500}")
    public void download() {
        downloadNextBatch();
    }

    /**
     * Reads the record file.
     * @param file data file object
     * @return StreamFile object
     */
    @Override
    protected StreamFile readStreamFile(File file) {
        return Utility.parseRecordFile(file.getPath(), null);
    }

    @Override
    protected void saveStreamFileRecord(StreamFile streamFile) {
        recordFileRepository.save((RecordFile)streamFile);
    }
}
