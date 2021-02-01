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
import java.io.File;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@Log4j2
@Named
public class AccountBalancesDownloader extends Downloader {

    private final BalanceFileReader balanceFileReader;
    private final AccountBalanceFileRepository accountBalanceFileRepository;

    public AccountBalancesDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            AddressBookService addressBookService, BalanceDownloaderProperties downloaderProperties,
            TransactionTemplate transactionTemplate, MeterRegistry meterRegistry,
            AccountBalanceFileRepository accountBalanceFileRepository, NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader, BalanceFileReader balanceFileReader) {
        super(s3Client, applicationStatusRepository, addressBookService, downloaderProperties, transactionTemplate,
                meterRegistry, nodeSignatureVerifier, signatureFileReader);
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.balanceFileReader = balanceFileReader;
    }

    /**
     * Reads the account balance file and checks that the file hash matches the verified hash.
     *
     * @param file account balance file object
     * @return StreamFile object
     */
    @Override
    protected StreamFile readStreamFile(File file) {
        return balanceFileReader.read(StreamFileData.from(file));
    }

    @Override
    protected void saveStreamFileRecord(StreamFile streamFile) {
        accountBalanceFileRepository.save((AccountBalanceFile) streamFile);
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "${hedera.mirror.importer.downloader.balance.frequency:30000}")
    public void download() {
        downloadNextBatch();
    }
}
