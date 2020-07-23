package com.hedera.mirror.importer.migration;

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

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.NodeAddressRepository;

public class V1_27_3__Address_BookTest extends IntegrationTest {
    @Resource
    private V1_27_3__Address_Book migration;
    @Resource
    private MirrorProperties mirrorProperties;
    @Resource
    private AddressBookRepository addressBookRepository;
    @Resource
    private NodeAddressRepository nodeAddressRepository;
    @Resource
    private FileDataRepository fileDataRepository;

    //    private static final NodeAddressBook SMALL = addressBook(3);
//    private static final NodeAddressBook MEDIUM = addressBook(6);
//    private static final NodeAddressBook LARGE = addressBook(9);
    private static final EntityId FILE_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);

    @Test
    void verifyAddressBookMigration() throws Exception {
        byte[] mediumAddressBookBytes = addressBook(5).toByteArray();
        int mediumIndex = mediumAddressBookBytes.length / 2;
        byte[] mediumAddressBookBytes1 = Arrays.copyOfRange(mediumAddressBookBytes, 0, mediumIndex);
        byte[] mediumAddressBookBytes2 = Arrays
                .copyOfRange(mediumAddressBookBytes, mediumIndex, mediumAddressBookBytes.length);

        byte[] largeAddressBookBytes = addressBook(9).toByteArray();
        int largeIndex = largeAddressBookBytes.length / 3;
        byte[] largeAddressBookBytes1 = Arrays.copyOfRange(largeAddressBookBytes, 0, largeIndex);
        byte[] largeAddressBookBytes2 = Arrays.copyOfRange(largeAddressBookBytes, largeIndex, largeIndex * 2);
        byte[] largeAddressBookBytes3 = Arrays
                .copyOfRange(largeAddressBookBytes, largeIndex * 2, largeAddressBookBytes.length);

        // inserts fileData
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1L, mediumAddressBookBytes1, 102, TransactionTypeEnum.FILEUPDATE.ordinal()));
        fileDataList.add(fileData(1L, mediumAddressBookBytes2, 102, TransactionTypeEnum.FILEAPPEND.ordinal()));
        fileDataList.add(fileData(1L, 3, 101, TransactionTypeEnum.FILECREATE.ordinal()));
        fileDataList.add(fileData(1L, largeAddressBookBytes1, 102, TransactionTypeEnum.FILEUPDATE.ordinal()));
        fileDataList.add(fileData(1L, largeAddressBookBytes2, 102, TransactionTypeEnum.FILEAPPEND.ordinal()));
        fileDataList.add(fileData(1L, largeAddressBookBytes3, 102, TransactionTypeEnum.FILEAPPEND.ordinal()));
        fileDataList.add(fileData(1L, 3, 101, TransactionTypeEnum.FILEAPPEND.ordinal()));

        // migration
        migration.migrate(null);

        assertEquals(7, fileDataRepository.count());
        assertEquals(7, addressBookRepository.count()); // test + bootstrap
        assertEquals(24, nodeAddressRepository.count());
    }

    private FileData fileData(long consensusTimestamp, int nodeAddresses, int fileId, int transactionType) {
        NodeAddressBook nodeAddressBook = addressBook(nodeAddresses);
        return fileData(consensusTimestamp, nodeAddressBook.toByteArray(), fileId, transactionType);
    }

    private FileData fileData(long consensusTimestamp, byte[] contents, int fileId, int transactionType) {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(consensusTimestamp);
        fileData.setFileData(contents);
        fileData.setFileId(EntityId.of(0, 0, fileId, EntityTypeEnum.FILE));
        fileData.setTransactionType(transactionType);
        return fileData;
    }

    private NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            builder.addNodeAddress(NodeAddress.newBuilder().setPortno(i).build());
        }
        return builder.build();
    }
}
