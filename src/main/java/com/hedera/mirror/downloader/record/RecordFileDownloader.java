package com.hedera.mirror.downloader.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.amazonaws.services.s3.transfer.TransferManager;

import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.downloader.Downloader;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.mirror.parser.record.RecordFileParser;

import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import javax.inject.Named;

@Log4j2
@Named
public class RecordFileDownloader extends Downloader {

    public RecordFileDownloader(
            TransferManager transferManager, ApplicationStatusRepository applicationStatusRepository,
            NetworkAddressBook networkAddressBook, RecordDownloaderProperties downloaderProperties) {
        super(transferManager, applicationStatusRepository, networkAddressBook, downloaderProperties);
    }

    @Scheduled(fixedRateString = "${hedera.mirror.downloader.record.frequency:500}")
    public void download() {
        downloadNextBatch();
    }

    protected ApplicationStatusCode getLastValidDownloadedFileKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE;
    }

    protected ApplicationStatusCode getLastValidDownloadedFileHashKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH;
    }

    protected ApplicationStatusCode getBypassHashKey() {
        return ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER;
    }

    protected String getPrevFileHash(String filePath) {
        return RecordFileParser.readPrevFileHash(filePath);
    }
}
