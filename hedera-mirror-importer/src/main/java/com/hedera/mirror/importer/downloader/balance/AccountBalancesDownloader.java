package com.hedera.mirror.importer.downloader.balance;

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
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;

@Log4j2
@Named
public class AccountBalancesDownloader extends Downloader {

    public AccountBalancesDownloader(
            S3AsyncClient s3Client, AddressBookService addressBookService,
            BalanceDownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry, NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader, BalanceFileReader balanceFileReader,
            StreamFileNotifier streamFileNotifier,
            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(s3Client, addressBookService, downloaderProperties,
                meterRegistry, nodeSignatureVerifier, signatureFileReader, balanceFileReader, streamFileNotifier,
                mirrorDateRangePropertiesProcessor);
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "${hedera.mirror.importer.downloader.balance.frequency:30000}")
    public void download() {
        downloadNextBatch();
    }
}
