package com.hedera.mirror.importer.downloader.event;

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
import org.apache.commons.codec.binary.Hex;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.fileencoding.event.FileParser;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@Log4j2
@Named
public class EventFileDownloader extends Downloader {

    public EventFileDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            NetworkAddressBook networkAddressBook, EventDownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry) {
        super(s3Client, applicationStatusRepository, networkAddressBook, downloaderProperties, meterRegistry);
    }

    @Leader
    @Override
    @Scheduled(fixedRateString = "${hedera.mirror.downloader.event.frequency:500}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected ApplicationStatusCode getLastValidDownloadedFileKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE;
    }

    @Override
    protected ApplicationStatusCode getLastValidDownloadedFileHashKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH;
    }

    /**
     * Checks that hash of data file matches the verified hash and that data file is next in line based on previous file
     * hash. Returns false if any condition is false.
     */
    @Override
    protected boolean verifyDataFile(File file, byte[] verifiedHash) {
        String expectedPrevFileHash = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileHashKey());
        try {
            EventFile eventFile = FileParser.parse(file.getPath(), expectedPrevFileHash,
                    downloaderProperties.getMirrorProperties().getVerifyHashAfter(), null);
            if (!eventFile.getFileHash().contentEquals(Hex.encodeHexString(verifiedHash))) {
                return false;
            }
        } catch (HashMismatchException e) {
            log.error(e);
            return false;
        }
        return true;
    }
}
