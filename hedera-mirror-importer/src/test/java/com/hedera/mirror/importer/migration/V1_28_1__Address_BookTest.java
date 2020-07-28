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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;

@TestPropertySource(properties = "spring.flyway.target=1.28.0")
//@Disabled
public class V1_28_1__Address_BookTest extends IntegrationTest {
    @Resource
    private V1_28_1__Address_Book migration;
    @Resource
    private DataSource dataSource;
    @Resource
    private AddressBookRepository addressBookRepository;
    @Resource
    private AddressBookEntryRepository addressBookEntryRepository;
    @Resource
    private FileDataRepository fileDataRepository;

    @Test
    void verifyAddressBookMigration() throws Exception {
        byte[] mediumAddressBookBytes = addressBook(5).toByteArray();
        int mediumIndex = mediumAddressBookBytes.length / 2;
        byte[] mediumAddressBookBytes1 = Arrays.copyOfRange(mediumAddressBookBytes, 0, mediumIndex);
        byte[] mediumAddressBookBytes2 = Arrays
                .copyOfRange(mediumAddressBookBytes, mediumIndex, mediumAddressBookBytes.length);

        byte[] largeAddressBookBytes = addressBook(10).toByteArray();
        int largeIndex = largeAddressBookBytes.length / 3;
        byte[] largeAddressBookBytes1 = Arrays.copyOfRange(largeAddressBookBytes, 0, largeIndex);
        byte[] largeAddressBookBytes2 = Arrays.copyOfRange(largeAddressBookBytes, largeIndex, largeIndex * 2);
        byte[] largeAddressBookBytes3 = Arrays
                .copyOfRange(largeAddressBookBytes, largeIndex * 2, largeAddressBookBytes.length);

        // inserts fileData
        List<FileData> fileDataList = new ArrayList<>();
        // 101 create w 3 nodes
        fileDataList.add(fileData(1L, 3, 101, TransactionTypeEnum.FILECREATE.getProtoId()));

        // 101 update -> append w 5 nodes
        fileDataList.add(fileData(2L, mediumAddressBookBytes1, 101, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(3L, mediumAddressBookBytes2, 101, TransactionTypeEnum.FILEAPPEND.getProtoId()));

        // 102 update -> append w 5 nodes
        fileDataList.add(fileData(4L, mediumAddressBookBytes1, 102, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(5L, mediumAddressBookBytes2, 102, TransactionTypeEnum.FILEAPPEND.getProtoId()));

        // 101 update -> append -> append w 10 nodes
        fileDataList.add(fileData(6L, largeAddressBookBytes1, 101, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(7L, largeAddressBookBytes2, 101, TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(8L, largeAddressBookBytes3, 101, TransactionTypeEnum.FILEAPPEND.getProtoId()));

        // 102 update -> append -> append w 10 nodes
        fileDataList.add(fileData(9L, largeAddressBookBytes1, 102, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(10L, largeAddressBookBytes2, 102, TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(11L, largeAddressBookBytes3, 102, TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        // migration
        migration.migrate(new FlywayContext());

        assertEquals(11, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count());
        assertEquals(33, addressBookEntryRepository.count());
    }

    @Test
    void verifyMigrationNonApplicableFileIDs() throws Exception {
        byte[] mediumAddressBookBytes = addressBook(5).toByteArray();
        int mediumIndex = mediumAddressBookBytes.length / 2;
        byte[] mediumAddressBookBytes1 = Arrays.copyOfRange(mediumAddressBookBytes, 0, mediumIndex);
        byte[] mediumAddressBookBytes2 = Arrays
                .copyOfRange(mediumAddressBookBytes, mediumIndex, mediumAddressBookBytes.length);

        byte[] largeAddressBookBytes = addressBook(10).toByteArray();
        int largeIndex = largeAddressBookBytes.length / 3;
        byte[] largeAddressBookBytes1 = Arrays.copyOfRange(largeAddressBookBytes, 0, largeIndex);
        byte[] largeAddressBookBytes2 = Arrays.copyOfRange(largeAddressBookBytes, largeIndex, largeIndex * 2);
        byte[] largeAddressBookBytes3 = Arrays
                .copyOfRange(largeAddressBookBytes, largeIndex * 2, largeAddressBookBytes.length);

        // inserts fileData
        List<FileData> fileDataList = new ArrayList<>();
        // 100 create w 3 nodes
        fileDataList.add(fileData(1L, 3, 100, TransactionTypeEnum.FILECREATE.getProtoId()));

        // 103 update -> append w 5 nodes
        fileDataList.add(fileData(2L, mediumAddressBookBytes1, 103, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(3L, mediumAddressBookBytes2, 103, TransactionTypeEnum.FILEAPPEND.getProtoId()));

        // 104 update -> append -> append w 10 nodes
        fileDataList.add(fileData(4L, largeAddressBookBytes1, 104, TransactionTypeEnum.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(5L, largeAddressBookBytes2, 104, TransactionTypeEnum.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(6L, largeAddressBookBytes3, 104, TransactionTypeEnum.FILEAPPEND.getProtoId()));

        fileDataRepository.saveAll(fileDataList);

        // migration
        migration.migrate(new FlywayContext());

        assertEquals(6, fileDataRepository.count());
        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    private FileData fileData(long consensusTimestamp, int nodeAddresses, int fileId, int transactionType) {
        NodeAddressBook nodeAddressBook = addressBook(nodeAddresses);
        return fileData(consensusTimestamp, nodeAddressBook.toByteArray(), fileId, transactionType);
    }

    private FileData fileData(long consensusTimestamp, byte[] contents, int fileId, int transactionType) {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(consensusTimestamp);
        fileData.setFileData(contents);
        fileData.setEntityId(EntityId.of(0, 0, fileId, EntityTypeEnum.FILE));
        fileData.setTransactionType(transactionType);
        return fileData;
    }

    private NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            long nodeId = 3 + i;
            builder.addNodeAddress(NodeAddress.newBuilder().setIpAddress(ByteString.copyFromUtf8("127.0.0." + nodeId))
                    .setPortno(50211 + i).setNodeId(nodeId).setMemo(ByteString.copyFromUtf8("0.0." + nodeId))
                    .setNodeAccountId(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(nodeId)
                            .build()).setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                    .setRSAPubKey("rsa+public/key").build());
        }
        return builder.build();
    }

    private class FlywayContext implements Context {

        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public Connection getConnection() {
            try {
                return dataSource.getConnection();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
