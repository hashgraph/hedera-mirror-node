package com.hedera.datagenerator.domain.generators.transaction;
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

import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.EntityManager;
import com.hedera.datagenerator.common.FileTransactionProperties;
import com.hedera.datagenerator.common.TransactionGenerator;
import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.FrequencyDistribution;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

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
            FileTransactionProperties properties, EntityManager entityManager, EntityListener entityListener) {
        super(entityManager, entityListener, properties.getNumSeedFiles());
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
        EntityId newFileNum = entityManager.getFiles().newEntity();
        transaction.setEntityId(newFileNum);
        createFileData(transaction.getConsensusNs());
        log.trace("FILECREATE transaction: fileId {}", newFileNum);
    }

    private void appendFile(Transaction transaction) {
        transaction.setType(16);  // 16 = FILEAPPEND
        EntityId file = entityManager.getFiles().getRandomEntity();
        transaction.setEntityId(file);
        createFileData(transaction.getConsensusNs());
        log.trace("FILEAPPEND transaction: fileId {}", file);
    }

    private void updateFile(Transaction transaction) {
        transaction.setType(19);  // 19 = FILEUPDATE
        EntityId fileNum = entityManager.getFiles().getRandomEntity();
        transaction.setEntityId(fileNum);
        createFileData(transaction.getConsensusNs());
        log.trace("FILEUPDATE transaction: fileId {}", fileNum);
    }

    private void deleteFile(Transaction transaction) {
        transaction.setType(18);  // 18 = FILEDELETE
        EntityId fileNum = entityManager.getFiles().getRandomEntity();
        entityManager.getFiles().delete(fileNum);
        transaction.setEntityId(fileNum);
        log.trace("FILEDELETE transaction: fileId {}", fileNum);
    }

    private void createFileData(long consensusNs) {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(consensusNs);
        long fileDataSize = properties.getFileDataSize().sample();
        byte[] fileDataBytes = new byte[(int) fileDataSize];
        new Random().nextBytes(fileDataBytes);
        fileData.setFileData(fileDataBytes);
        entityListener.onFileData(fileData);
    }
}
