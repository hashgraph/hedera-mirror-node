package com.hedera.faker.domain.generators.transaction;
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

import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javax.inject.Named;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.faker.common.EntityManager;
import com.hedera.faker.common.FileTransactionProperties;
import com.hedera.faker.common.TransactionGenerator;
import com.hedera.faker.domain.writer.DomainWriter;
import com.hedera.faker.sampling.Distribution;
import com.hedera.faker.sampling.FrequencyDistribution;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.Transaction;

/**
 * Generates file transactions (FILECREATE, FILEAPPEND, FILEUPDATE, FILEDELETE).
 */
@Log4j2
@Named
public class FileTransactionGenerator extends TransactionGenerator {

    private final FileTransactionProperties properties;
    @Getter
    private final Distribution<Consumer<Transaction>> transactionDistribution;

    public FileTransactionGenerator(
            FileTransactionProperties properties, EntityManager entityManager, DomainWriter domainWriter) {
        super(entityManager, domainWriter, properties.getNumSeedFiles());
        this.properties = properties;
        transactionDistribution = new FrequencyDistribution<>(Map.of(
                this::createFile, this.properties.getCreatesFrequency(),
                this::appendFile, this.properties.getAppendsFrequency(),
                this::updateFile, this.properties.getUpdatesFrequency(),
                this::deleteFile, this.properties.getDeletesFrequency()
        ));
    }

    @Override
    public void seedEntity(Transaction transaction) {
        createFile(transaction);
    }

    private void createFile(Transaction transaction) {
        transaction.setType(17);  // 17 = FILECREATE
        Long newFileId = entityManager.getFiles().newEntity();
        transaction.setEntityId(newFileId);
        createFileData(transaction.getConsensusNs());
        log.trace("FILECREATE transaction: fileId {}", newFileId);
    }

    private void appendFile(Transaction transaction) {
        transaction.setType(16);  // 16 = FILEAPPEND
        Long fileId = entityManager.getFiles().getRandom();
        transaction.setEntityId(fileId);
        createFileData(transaction.getConsensusNs());
        log.trace("FILEAPPEND transaction: fileId {}", fileId);
    }

    private void updateFile(Transaction transaction) {
        transaction.setType(19);  // 19 = FILEUPDATE
        Long fileId = entityManager.getFiles().getRandom();
        transaction.setEntityId(fileId);
        createFileData(transaction.getConsensusNs());
        log.trace("FILEUPDATE transaction: fileId {}", fileId);
    }

    private void deleteFile(Transaction transaction) {
        transaction.setType(18);  // 18 = FILEDELETE
        Long fileId = entityManager.getFiles().getRandom();
        entityManager.getFiles().delete(fileId);
        transaction.setEntityId(fileId);
        log.trace("FILEDELETE transaction: fileId {}", fileId);
    }

    private void createFileData(long consensusNs) {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(consensusNs);
        long fileDataSize = properties.getFileDataSize().sample();
        byte[] fileDataBytes = new byte[(int) fileDataSize];
        new Random().nextBytes(fileDataBytes);
        fileData.setFileData(fileDataBytes);
        domainWriter.addFileData(fileData);
    }
}
