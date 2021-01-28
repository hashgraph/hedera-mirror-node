package com.hedera.mirror.importer.downloader.event;

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

import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import javax.inject.Named;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.event.EventFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@Named
public class EventFileDownloader extends Downloader {

    private final EventFileReader eventFileReader;

    public EventFileDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            AddressBookService addressBookService, EventDownloaderProperties downloaderProperties,
            TransactionTemplate transactionTemplate, MeterRegistry meterRegistry, EventFileReader eventFileReader,
            NodeSignatureVerifier nodeSignatureVerifier, SignatureFileReader signatureFileReader) {
        super(s3Client, applicationStatusRepository, addressBookService, downloaderProperties, transactionTemplate,
                meterRegistry, nodeSignatureVerifier, signatureFileReader);
        this.eventFileReader = eventFileReader;
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "${hedera.mirror.downloader.event.frequency:5000}")
    public void download() {
        downloadNextBatch();
    }

    /**
     * Reads the event file.
     *
     * @param file event file object
     * @return StreamFile object
     */
    @Override
    protected StreamFile readStreamFile(File file) {
        return eventFileReader.read(StreamFileData.from(file));
    }

    @Override
    protected void saveStreamFileRecord(StreamFile streamFile) {
        // no-op, save the EventFile record to db when event parser is implemented
    }
}
