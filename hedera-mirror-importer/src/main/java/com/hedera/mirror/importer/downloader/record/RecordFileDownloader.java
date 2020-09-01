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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.exception.HashMismatchException;
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
            MeterRegistry meterRegistry, RecordFileRepository recordFileRepository) {
        super(s3Client, applicationStatusRepository, addressBookService, downloaderProperties, meterRegistry);
        this.recordFileRepository = recordFileRepository;
    }

    @Leader
    @Scheduled(fixedRateString = "${hedera.mirror.importer.downloader.record.frequency:500}")
    public void download() {
        downloadNextBatch();
    }

    /**
     * Reads the data file and checks that hash of data file matches the verified hash and that data file is next in
     * line based on previous file hash.
     * @param file data file object
     * @param verifiedHash the verified hash in hex
     * @return StreamFile object
     */
    @Override
    protected StreamFile readAndVerifyDataFile(File file, String verifiedHash) {
        String expectedPrevFileHash = applicationStatusRepository.findByStatusCode(lastValidDownloadedFileHashKey);
        RecordFile recordFile = Utility.parseRecordFile(file.getPath(), expectedPrevFileHash,
                downloaderProperties.getMirrorProperties().getVerifyHashAfter(), null);
        if (!recordFile.getFileHash().contentEquals(verifiedHash)) {
            throw new HashMismatchException(file.getName(), verifiedHash, recordFile.getFileHash());
        }

        Instant consensusEnd = Instant.ofEpochSecond(0, recordFile.getConsensusEnd());
        downloadLatencyMetric.record(Duration.between(consensusEnd, Instant.now()));

        long streamClose = recordFile.getConsensusEnd() - recordFile.getConsensusStart();
        streamCloseMetric.record(streamClose, TimeUnit.NANOSECONDS);

        return recordFile;
    }

    @Override
    protected void saveStreamFileRecord(StreamFile streamFile) {
        recordFileRepository.save((RecordFile)streamFile);
    }
}
