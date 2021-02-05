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
import javax.inject.Named;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.event.EventFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.repository.EventFileRepository;

@Named
public class EventFileDownloader extends Downloader {

    public EventFileDownloader(
            S3AsyncClient s3Client, EventFileRepository eventFileRepository,
            AddressBookService addressBookService, EventDownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry, EventFileReader eventFileReader,
            NodeSignatureVerifier nodeSignatureVerifier, SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier) {
        super(s3Client, eventFileRepository, addressBookService, downloaderProperties, meterRegistry,
                nodeSignatureVerifier, signatureFileReader, eventFileReader, streamFileNotifier);
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "${hedera.mirror.downloader.event.frequency:5000}")
    public void download() {
        downloadNextBatch();
    }
}
