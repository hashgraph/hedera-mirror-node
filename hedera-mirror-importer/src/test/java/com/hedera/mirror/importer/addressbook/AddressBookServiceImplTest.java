package com.hedera.mirror.importer.addressbook;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;

class AddressBookServiceImplTest extends IntegrationTest {

    private static final NodeAddressBook UPDATED = addressBook(10);
    private static final NodeAddressBook FINAL = addressBook(15);

    @TempDir
    Path dataPath;

    private MirrorProperties mirrorProperties;

    @Resource
    private AddressBookRepository addressBookRepository;

    @Resource
    private AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    private FileDataRepository fileDataRepository;

    @Resource
    private AddressBookService addressBookService;

    @Qualifier(CacheConfiguration.EXPIRE_AFTER_5M)
    @Resource
    private CacheManager cacheManager;

    private static NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            long nodeId = 3 + i;
            builder.addNodeAddress(NodeAddress.newBuilder()
                    .setIpAddress(ByteString.copyFromUtf8("127.0.0." + nodeId))
                    .setPortno((int) nodeId)
                    .setNodeId(nodeId)
                    .setMemo(ByteString.copyFromUtf8("0.0." + nodeId))
                    .setNodeAccountId(AccountID.newBuilder().setAccountNum(nodeId))
                    .setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                    .setRSAPubKey("rsa+public/key")
                    .build());
        }
        return builder.build();
    }

    private void update(byte[] contents, long consensusTimeStamp, boolean is102) {
        EntityId entityId = is102 ? AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID :
                AddressBookServiceImpl.ADDRESS_BOOK_101_ENTITY_ID;
        FileData fileData = new FileData(consensusTimeStamp, contents, entityId, TransactionTypeEnum.FILEUPDATE
                .getProtoId());
        fileDataRepository.save(fileData);
        addressBookService.update(fileData);
    }

    private void append(byte[] contents, long consensusTimeStamp, boolean is102) {
        EntityId entityId = is102 ? AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID :
                AddressBookServiceImpl.ADDRESS_BOOK_101_ENTITY_ID;
        FileData fileData = new FileData(consensusTimeStamp, contents, entityId, TransactionTypeEnum.FILEAPPEND
                .getProtoId());
        fileDataRepository.save(fileData);
        addressBookService.update(fileData);
    }

    @BeforeEach
    void setup() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
    }

    @Test
    void startupWithOtherNetwork() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });
    }

    @Test
    void updateCompleteFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, 1L, true);

        // assert current addressBook is updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(2L);
        assertAddressBook(addressBook, UPDATED);

        // assert repositories contain updates
        assertAddressBookData(UPDATED.toByteArray(), 1);
        assertEquals(1, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount(), addressBookEntryRepository.count());

        assertEquals(1, addressBookRepository.count());
        assertEquals(10, addressBookEntryRepository.count());
    }

    @Test
    void cacheAddressBook() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, 1L, true);

        //verify cache is empty to start
        assertNull(cacheManager.getCache(AddressBookServiceImpl.ADDRESS_BOOK_102_CACHE_NAME)
                .get(AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID.getId()));

        //verify getCurrent() adds an entry to the cache
        AddressBook addressBookDb = addressBookService.getCurrent();
        AddressBook addressBookCache = (AddressBook) cacheManager
                .getCache(AddressBookServiceImpl.ADDRESS_BOOK_102_CACHE_NAME)
                .get(AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID.getId()).get();
        assertNotNull(addressBookCache);
        assertThat(addressBookCache).isEqualTo(addressBookDb);

        //verify updating the address book evicts the cache.
        update(addressBookBytes, 2L, true);
        assertNull(cacheManager.getCache(AddressBookServiceImpl.ADDRESS_BOOK_102_CACHE_NAME)
                .get(AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID.getId()));
    }

    @Test
    void updatePartialFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        update(addressBookPartial, 1L, true);

        // bootstrap address book will be missing in most tests. In production migration will ensure DB population
        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    @Test
    void appendCompleteFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 3;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, index * 2);
        byte[] addressBookBytes3 = Arrays.copyOfRange(addressBookBytes, index * 2, addressBookBytes.length);

        // bootstrap address book will be missing in most tests. In production migration will ensure DB population
        update(addressBookBytes1, 1L, true);
        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        append(addressBookBytes2, 3L, true);
        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        append(addressBookBytes3, 5L, true);
        assertAddressBook(addressBookService.getCurrent(), UPDATED);

        assertAddressBookData(addressBookBytes, 5);

        assertEquals(1, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount(), addressBookEntryRepository.count());

        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(6L);
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());
    }

    @Test
    void appendPartialFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        update(addressBookBytes1, 1L, true);

        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    @Test
    void ignoreEmptyByteArray() {
        update(new byte[] {}, 1L, true);
        append(new byte[] {}, 2L, true);

        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });
        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    @Test
    void isAddressBook() {
        EntityId fileID = EntityId.of(0, 0, 234, EntityTypeEnum.FILE);
        boolean isAddressBook = addressBookService.isAddressBook(fileID);
        assertThat(isAddressBook).isFalse();

        fileID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
        isAddressBook = addressBookService.isAddressBook(fileID);
        assertThat(isAddressBook).isTrue();

        fileID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
        isAddressBook = addressBookService.isAddressBook(fileID);
        assertThat(isAddressBook).isTrue();
    }

    @Test
    void verifyAddressBookUpdateAcrossSessions() {
        // create network book, perform an update and append
        byte[] addressBookBytes = FINAL.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        update(addressBookBytes1, 1L, true);

        // create new address book and submit another append to complete file
        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        append(addressBookBytes2, 3L, true);

        // verify valid address book and repository update
        AddressBook addressBook = addressBookService.getCurrent();
        assertAddressBook(addressBookService.getCurrent(), FINAL);
        assertAddressBookData(FINAL.toByteArray(), 3);
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(4L);
    }

    @Test
    void appendCompleteFileAcrossFileIds() {
        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);

        // init address book and verify initial state
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count());

        // perform file 102 first update and confirm no change to current address book and nodes addresses
        update(addressBookBytes1, 1L, true); // fileID 102
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count()); // initial and 102 update

        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        update(addressBook101Bytes1, 3L, false);
        append(addressBook101Bytes2, 5L, false);

        // verify partial bytes match 101 complete address book update
        assertAddressBookData(FINAL.toByteArray(), 5);
        assertEquals(15, addressBookEntryRepository.count());
        assertEquals(1, addressBookRepository.count());

        // verify current address book bytes still match original load and not 101 update and append
        assertThrows(IllegalStateException.class, () -> {
            addressBookService.getCurrent();
        });

        // perform file 102 append
        append(addressBookBytes2, 7L, true);

        // verify address book and node addresses are updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(8L);
        assertAddressBook(addressBookService.getCurrent(), UPDATED);

        // 15 (101 update) + 12 (102 update)
        assertEquals(UPDATED.getNodeAddressCount() + FINAL
                .getNodeAddressCount(), addressBookEntryRepository
                .count());
        assertAddressBookData(UPDATED.toByteArray(), 7);
        assertEquals(2, addressBookRepository.count());
    }

    @Test
    void verifyAddressBookEndPointsAreSet() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, 0L, true);

        // assert current addressBook is updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());
        assertThat(addressBook.getStartConsensusTimestamp()).isNotNull();
        assertThat(addressBook.getEndConsensusTimestamp()).isNull();

        byte[] newAddressBookBytes = FINAL.toByteArray();

        update(newAddressBookBytes, 10L, true);
        AddressBook newAddressBook = addressBookService.getCurrent();
        assertAddressBook(newAddressBook, FINAL);
        assertAddressBookData(newAddressBookBytes, 10);

        assertEquals(2, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(), addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        AddressBook prevAddressBook = addressBookRepository.findById(1L).get();
        assertThat(prevAddressBook.getStartConsensusTimestamp()).isNotNull();
        assertThat(prevAddressBook.getEndConsensusTimestamp()).isNotNull();
    }

    @Test
    void verifyAddressBookEntriesWithNodeIdAndPortNotSet() {
        // nodeId 0, port 0
        NodeAddress nodeAddress1 = NodeAddress.newBuilder()
                .setIpAddress(ByteString.copyFromUtf8("127.0.0.1"))
                .setMemo(ByteString.copyFromUtf8("0.0.3"))
                .setNodeAccountId(AccountID.newBuilder().setAccountNum(3))
                .setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                .setRSAPubKey("rsa+public/key")
                .build();
        // nodeId 0, port 50211
        NodeAddress nodeAddress2 = NodeAddress.newBuilder(nodeAddress1)
                .setIpAddress(ByteString.copyFromUtf8("127.0.0.2"))
                .setMemo(ByteString.copyFromUtf8("0.0.4"))
                .setNodeAccountId(AccountID.newBuilder().setAccountNum(4))
                .setPortno(50211)
                .build();
        NodeAddressBook nodeAddressBook = NodeAddressBook.newBuilder()
                .addAllNodeAddress(List.of(nodeAddress1, nodeAddress2))
                .build();

        byte[] addressBookBytes = nodeAddressBook.toByteArray();
        update(addressBookBytes, 1L, true);

        assertAddressBookData(addressBookBytes, 1L);
        AddressBook addressBook = addressBookService.getCurrent();
        ListAssert<AddressBookEntry> listAssert = assertThat(addressBook.getEntries())
                .hasSize(nodeAddressBook.getNodeAddressCount());
        for (NodeAddress nodeAddress : nodeAddressBook.getNodeAddressList()) {
            listAssert.anySatisfy(abe -> {
                assertThat(abe.getIp()).isEqualTo(nodeAddress.getIpAddress().toStringUtf8());
                assertThat(abe.getMemo()).isEqualTo(nodeAddress.getMemo().toStringUtf8());
                assertThat(abe.getNodeAccountId()).isEqualTo(EntityId.of(nodeAddress.getNodeAccountId()));
                assertThat(abe.getNodeCertHash()).isEqualTo(nodeAddress.getNodeCertHash().toByteArray());
                assertThat(abe.getPublicKey()).isEqualTo(nodeAddress.getRSAPubKey());
                assertThat(abe.getNodeId()).isNull(); // both entries have null node id
            });
        }
        // one entry has null port and the other's is 50211
        listAssert.anySatisfy(abe -> assertThat(abe.getPort()).isNull());
        listAssert.anySatisfy(abe -> assertThat(abe.getPort()).isEqualTo(50211));
    }

    private void assertAddressBookData(byte[] expected, long consensusTimestamp) {
        AddressBook actualAddressBook = addressBookRepository.findById(consensusTimestamp + 1).get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }

    private void assertAddressBook(AddressBook actual, NodeAddressBook expected) {
        ListAssert<AddressBookEntry> listAssert = assertThat(actual.getEntries())
                .hasSize(expected.getNodeAddressCount());

        for (NodeAddress nodeAddress : expected.getNodeAddressList()) {
            listAssert.anySatisfy(abe -> {
                assertThat(abe.getIp()).isEqualTo(nodeAddress.getIpAddress().toStringUtf8());
                assertThat(abe.getMemo()).isEqualTo(nodeAddress.getMemo().toStringUtf8());
                assertThat(abe.getNodeAccountId()).isEqualTo(EntityId.of(nodeAddress.getNodeAccountId()));
                assertThat(abe.getNodeCertHash()).isEqualTo(nodeAddress.getNodeCertHash().toByteArray());
                assertThat(abe.getPublicKey()).isEqualTo(nodeAddress.getRSAPubKey());
                assertThat(abe.getNodeId()).isEqualTo(nodeAddress.getNodeId());
                assertThat(abe.getPort()).isEqualTo(nodeAddress.getPortno());
            });
        }
    }
}
