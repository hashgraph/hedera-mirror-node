package com.hedera.mirror.importer.repository;

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

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;

public class FileDataRepositoryTest extends AbstractRepositoryTest {
    private final EntityId addressBookEntityId101 = EntityId.of("0.0.101", EntityTypeEnum.FILE);
    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Test
    void insert() {
        Transaction transaction = insertTransaction("FILECREATE");

        FileData fileData = fileData(transaction.getConsensusNs(), 123, TransactionTypeEnum.FILECREATE.getProtoId());
        fileData = fileDataRepository.save(fileData);

        Assertions.assertThat(fileDataRepository.findById(transaction.getConsensusNs()).get())
                .isNotNull()
                .isEqualTo(fileData);
    }

    @Test
    void findFilesInRange() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, 123, TransactionTypeEnum.FILECREATE.getProtoId()));
        fileDataList.add(fileData(2, addressBookEntityId102.getId(), TransactionTypeEnum.FILECREATE.getProtoId()));
        fileDataList.add(fileData(3, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(4, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(6, 123, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(7, 123, TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        Assertions.assertThat(fileDataRepository
                .findFilesInRange(
                        2, 7, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()))
                .isNotNull()
                .hasSize(3)
                .extracting(FileData::getConsensusTimestamp)
                .containsSequence(3L, 4L, 5L);
    }

    @Test
    void findFilesOfTransactionTypesInRange() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, 123, TransactionTypeEnum.FILECREATE.getProtoId()));
        FileData fileData = fileData(2, addressBookEntityId102.getId(), TransactionTypeEnum.FILEUPDATE.getProtoId());
        fileDataList.add(fileData);
        fileDataList.add(fileData(3, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(4, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(6, 123, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(7, 123, TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        List<Integer> transactionTypes = List
                .of(TransactionTypeEnum.FILECREATE.getProtoId(), TransactionTypeEnum.FILEUPDATE.getProtoId());
        Assertions.assertThat(fileDataRepository
                .findLatestMatchingFile(
                        5, addressBookEntityId102.getId(), transactionTypes)).get()
                .isNotNull()
                .isEqualTo(fileData);
    }

    @Test
    void findAddressBookFilesInRange() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, addressBookEntityId101.getId(), TransactionTypeEnum.FILECREATE.getProtoId()));
        fileDataList.add(fileData(2, addressBookEntityId102.getId(), TransactionTypeEnum.FILECREATE.getProtoId()));
        fileDataList.add(fileData(3, addressBookEntityId101.getId(), TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(4, addressBookEntityId101.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBookEntityId102.getId(), TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(6, addressBookEntityId102.getId(), TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(7, addressBookEntityId101.getId(), TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(8, addressBookEntityId102.getId(), TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        Assertions.assertThat(fileDataRepository
                .findAddressBooksAfter(
                        2, 5))
                .isNotNull()
                .hasSize(5)
                .extracting(FileData::getConsensusTimestamp)
                .containsSequence(3L, 4L, 5L, 6L, 7L);
    }

    private FileData fileData(long consensusTimestamp, long fileId, int transactionType) {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(consensusTimestamp);
        fileData.setFileData("some file data".getBytes());
        fileData.setEntityId(EntityId.of(0, 0, fileId, EntityTypeEnum.FILE));
        fileData.setTransactionType(transactionType);
        return fileData;
    }
}
