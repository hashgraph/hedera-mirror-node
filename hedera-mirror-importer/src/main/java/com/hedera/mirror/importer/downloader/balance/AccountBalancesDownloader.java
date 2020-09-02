package com.hedera.mirror.importer.downloader.balance;

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
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class AccountBalancesDownloader extends Downloader {

    private AccountBalanceFileRepository accountBalanceFileRepository;

    public AccountBalancesDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            AddressBookService addressBookService, BalanceDownloaderProperties downloaderProperties,
            PlatformTransactionManager platformTransactionManager, MeterRegistry meterRegistry, AccountBalanceFileRepository accountBalanceFileRepository) {
        super(s3Client, applicationStatusRepository, addressBookService, downloaderProperties, platformTransactionManager, meterRegistry);
        this.accountBalanceFileRepository = accountBalanceFileRepository;
    }

    /**
     * Reads the account balance file and checks that the file hash matches the verified hash.
     * @param file account balance file object
     * @param verifiedHash the verified hash in hex
     * @return StreamFile object
     */
    @Override
    protected StreamFile readAndVerifyDataFile(File file, String verifiedHash) {
        String fileHash = Utility.getBalanceFileHash(file.getPath());
        if (!verifiedHash.contentEquals(fileHash)) {
            throw new HashMismatchException(file.getName(), verifiedHash, fileHash);
        }

        AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setName(file.getName());
        accountBalanceFile.setConsensusTimestamp(Utility.getTimestampFromFilename(file.getName()));
        accountBalanceFile.setFileHash(fileHash);
        accountBalanceFile.setCount(0L);
        return accountBalanceFile;
    }

    @Override
    protected void saveStreamFileRecord(StreamFile streamFile) {
        accountBalanceFileRepository.save((AccountBalanceFile)streamFile);
    }

    @Leader
    @Scheduled(fixedRateString = "${hedera.mirror.importer.downloader.balance.frequency:30000}")
    public void download() {
        downloadNextBatch();
    }
}
