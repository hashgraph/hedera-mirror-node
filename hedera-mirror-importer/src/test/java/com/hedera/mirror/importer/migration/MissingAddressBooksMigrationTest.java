package com.hedera.mirror.importer.migration;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import com.hedera.mirror.importer.addressbook.AddressBookServiceImpl;
import com.hedera.mirror.importer.domain.AddressBook;

import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

import com.hedera.mirror.importer.repository.AddressBookRepository;

import com.hedera.mirror.importer.repository.FileDataRepository;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Resource;
import lombok.AllArgsConstructor;
import org.assertj.core.api.ListAssert;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MissingAddressBooksMigrationTest extends IntegrationTest {

    private static final NodeAddressBook UPDATED = addressBook(10, 0);
    private static final NodeAddressBook FINAL = addressBook(15, 0);

    @Resource
    private MissingAddressBooksMigration missingAddressBooksMigration;

    @Resource
    private AddressBookRepository addressBookRepository;

    @Resource
    private FileDataRepository fileDataRepository;

    @Resource
    private EntityProperties entityProperties;

    @Test
    void verifyAddressBookMigrationWithNewFileDataAfterCurrentAddressBook() {
        // store initial address books
        addressBookRepository.save(addressBook(ab -> ab.fileId(AddressBookServiceImpl.ADDRESS_BOOK_101_ENTITY_ID), 1, 4));
        addressBookRepository.save(addressBook(ab -> ab.fileId(AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID), 2, 4));
        addressBookRepository.save(addressBook(ab -> ab.fileId(AddressBookServiceImpl.ADDRESS_BOOK_101_ENTITY_ID), 11, 8));
        addressBookRepository.save(addressBook(ab -> ab.fileId(AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID), 12, 8));
        assertEquals(4, addressBookRepository.count());

        // un-parsed file_data
        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);
        createAndStoreFileData(addressBook101Bytes1, 101, false, TransactionTypeEnum.FILEUPDATE);
        createAndStoreFileData(addressBook101Bytes2, 102, false, TransactionTypeEnum.FILEAPPEND);

        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBook102Bytes = FINAL.toByteArray();
        int index = addressBook102Bytes.length / 2;
        byte[] addressBook102Bytes1 = Arrays.copyOfRange(addressBook102Bytes, 0, index);
        byte[] addressBook102Bytes2 = Arrays.copyOfRange(addressBook102Bytes, index, addressBook102Bytes.length);
        createAndStoreFileData(addressBook102Bytes1, 201, true, TransactionTypeEnum.FILEUPDATE);
        createAndStoreFileData(addressBook102Bytes2, 202, true, TransactionTypeEnum.FILEAPPEND);
        assertEquals(4, fileDataRepository.count());

        // migration on startup
        missingAddressBooksMigration.doMigrate();
        assertEquals(6, addressBookRepository.count());
        AddressBook newAddressBook = addressBookRepository.findLatestAddressBook(205, AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID.getId()).get();
        assertThat(newAddressBook.getStartConsensusTimestamp()).isEqualTo(203L);
        assertAddressBook(newAddressBook, FINAL);
    }

    @DisplayName("Verify skipMigration")
    @ParameterizedTest(name = "with baseline {0} and target {1}")
    @CsvSource({
            "1.37.0, 1.999.0, true",
            "1.37.1, 1.999.0, false",
            "1.37.2, 1.999.0, false",
            "2.0.0, 2.999.999, false",
            "2.0.1, 2.999.999, false",
            "0, 2, true",
            ", 2, true"
    })
    void skipMigrationPreAddressBookService(String baseline, String target, boolean result) {
        assertThat(missingAddressBooksMigration.skipMigration(getConfiguration(baseline, target))).isEqualTo(result);
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer,
                                    long consensusTimestamp, int nodeCount) {
        long startConsensusTimestamp = consensusTimestamp + 1;
        List<AddressBookEntry> addressBookEntryList = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            long id = i;
            long nodeId = 3 + i;
            addressBookEntryList
                    .add(addressBookEntry(a -> a.id(new AddressBookEntry.Id(startConsensusTimestamp, nodeId))
                            .memo("0.0." + nodeId)
                            .nodeAccountId(EntityId.of("0.0." + nodeId, EntityTypeEnum.ACCOUNT))));
        }

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileData("address book memo".getBytes())
                .nodeCount(nodeCount)
                .fileId(AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID)
                .entries(addressBookEntryList);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }

    private AddressBookEntry addressBookEntry(Consumer<AddressBookEntry.AddressBookEntryBuilder> nodeAddressCustomizer) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .id(new AddressBookEntry.Id(Instant.now().getEpochSecond(), 5L))
                .description("address book entry")
                .publicKey("rsa+public/key")
                .memo("0.0.3")
                .nodeAccountId(EntityId.of("0.0.5", EntityTypeEnum.ACCOUNT))
                .nodeCertHash("nodeCertHash".getBytes())
                .stake(5L);

        if (nodeAddressCustomizer != null) {
            nodeAddressCustomizer.accept(builder);
        }

        return builder.build();
    }

    private static NodeAddressBook addressBook(int size, int endPointSize) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            long nodeId = 3 + i;
            NodeAddress.Builder nodeAddressBuilder = NodeAddress.newBuilder()
                    .setIpAddress(ByteString.copyFromUtf8("127.0.0." + nodeId))
                    .setPortno((int) nodeId)
                    .setNodeId(nodeId)
                    .setMemo(ByteString.copyFromUtf8("0.0." + nodeId))
                    .setNodeAccountId(AccountID.newBuilder().setAccountNum(nodeId))
                    .setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                    .setRSAPubKey("rsa+public/key");

            // add service endpoints
            if (endPointSize > 0) {
                List<ServiceEndpoint> serviceEndpoints = new ArrayList<>();
                for (int j = 1; j <= size; ++j) {
                    serviceEndpoints.add(ServiceEndpoint.newBuilder()
                            .setIpAddressV4(ByteString.copyFrom(new byte[] {127, 0, 0, (byte) j}))
                            .setPort(443 + j)
                            .build());
                }
            }

            builder.addNodeAddress(nodeAddressBuilder.build());
        }
        return builder.build();
    }

    private FileData createAndStoreFileData(byte[] contents, long consensusTimeStamp, boolean is102, TransactionTypeEnum transactionTypeEnum) {
        EntityId entityId = is102 ? AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID :
                AddressBookServiceImpl.ADDRESS_BOOK_101_ENTITY_ID;
        FileData fileData =  new FileData(consensusTimeStamp, contents, entityId, transactionTypeEnum.getProtoId());
        return fileDataRepository.save(fileData);
    }

    private void assertAddressBook(AddressBook actual, NodeAddressBook expected) {
        ListAssert<AddressBookEntry> listAssert = assertThat(actual.getEntries())
                .hasSize(expected.getNodeAddressCount());

        for (NodeAddress nodeAddress : expected.getNodeAddressList()) {
            listAssert.anySatisfy(abe -> {
                assertThat(abe.getMemo()).isEqualTo(nodeAddress.getMemo().toStringUtf8());
                assertThat(abe.getNodeAccountId()).isEqualTo(EntityId.of(nodeAddress.getNodeAccountId()));
                assertThat(abe.getNodeCertHash()).isEqualTo(nodeAddress.getNodeCertHash().toByteArray());
                assertThat(abe.getPublicKey()).isEqualTo(nodeAddress.getRSAPubKey());
                assertThat(abe.getId().getNodeId()).isEqualTo(nodeAddress.getNodeId());
            });
        }
    }

    private ClassicConfiguration getConfiguration(String baseLine, String target) {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setBaselineVersionAsString(baseLine);
        configuration.setTargetAsString(target);
        return configuration;
    }
}
