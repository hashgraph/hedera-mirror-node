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
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class RecordFileDownloader extends Downloader {

    public RecordFileDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            NetworkAddressBook networkAddressBook, RecordDownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry) {
        super(s3Client, applicationStatusRepository, networkAddressBook, downloaderProperties, meterRegistry);
    }

    @Leader
    @Override
    @Scheduled(fixedRateString = "${hedera.mirror.importer.downloader.record.frequency:500}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected ApplicationStatusCode getLastValidDownloadedFileKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE;
    }

    @Override
    protected ApplicationStatusCode getLastValidDownloadedFileHashKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH;
    }

    /**
     * Checks that hash of data file matches the verified hash. Then checks that data file is next in line based on
     * previous file hash.
     */
    @Override
    protected boolean verifyDataFile(String filePath, byte[] verifiedHash) {
        RecordFile recordFile =  Utility.parseRecordFile(filePath, false);
        if (!recordFile.getFileHash().contentEquals(Hex.encodeHexString(verifiedHash))) {
            return false;
        }
        String fileName = Utility.getFileName(filePath);
        log.debug("Downloaded data file {} corresponding to verified hash", fileName);
        // Verifies that prevFileHash in given {@code file} matches that in application repository.
        String expectedPrevFileHash = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileHashKey());
        return Utility.verifyHashChain(recordFile.getPreviousHash(), expectedPrevFileHash,
                downloaderProperties.getMirrorProperties().getVerifyHashAfter(), fileName);
    }
}
