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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.ResetCacheTestExecutionListener;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;

@SpringBootTest
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@TestExecutionListeners(value = {ResetCacheTestExecutionListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class AddressBookServiceImplTest {

    private static final NodeAddressBook UPDATED = addressBook(10);
    private static final NodeAddressBook FINAL = addressBook(15);
    private static final EntityId FILE_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
    private static final int INITIAL_NODE_COUNT = 4;

    @TempDir
    Path dataPath;

    @Value("classpath:addressbook/testnet")
    private File initialAddressBook;

    private MirrorProperties mirrorProperties;

    @Resource
    protected AddressBookRepository addressBookRepository;
    @Resource
    protected AddressBookEntryRepository addressBookEntryRepository;
    @Resource
    protected FileDataRepository fileDataRepository;

    private static NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            long nodeId = 3 + i;
            builder.addNodeAddress(NodeAddress.newBuilder().setIpAddress(ByteString.copyFromUtf8("127.0.0." + nodeId))
                    .setPortno(i).setNodeId(nodeId).setMemo(ByteString.copyFromUtf8("0.0." + nodeId))
                    .setNodeAccountId(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(nodeId)
                            .build()).setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                    .setRSAPubKey("rsa+public/key").build());
        }
        return builder.build();
    }

    private byte[] initialAddressBook() throws IOException {
        return FileUtils.readFileToByteArray(initialAddressBook);
    }

    private static void update(AddressBookService addressBookServiceImpl, byte[] contents,
                               long consensusTimeStamp) {
        FileData fileData = new FileData(consensusTimeStamp, contents, FILE_ENTITY_ID, TransactionTypeEnum.FILEUPDATE
                .getProtoId());
        addressBookServiceImpl.update(fileData);
    }

    private static void append(AddressBookService addressBookServiceImpl, byte[] contents,
                               long consensusTimeStamp) {
        FileData fileData = new FileData(consensusTimeStamp, contents, FILE_ENTITY_ID, TransactionTypeEnum.FILEAPPEND
                .getProtoId());
        addressBookServiceImpl.update(fileData);
    }

    @BeforeEach
    void setup() throws Exception {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
    }

    @Test
    void startupWithOtherNetwork() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        assertThat(addressBookServiceImpl.getCurrent()).isNull();
    }

    @Test
    void updateCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        AddressBookServiceImpl addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes, 1L);

        // assert current addressBook is updated
        AddressBook addressBook = addressBookServiceImpl.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(2L);
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());

        // assert repositories contain updates
        assertAddressBookData(UPDATED.toByteArray(), 1);
        assertEquals(1, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount(), addressBookEntryRepository.count());

        List<AddressBook> addressBookList = addressBookRepository
                .findLatestAddressBooks(3L, FILE_ENTITY_ID);
        assertThat(addressBookList).isNotEmpty().hasSize(1);
    }

    @Test
    void updatePartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        AddressBookServiceImpl addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookPartial, 1L);

        // for tests current address book will be null in tests. In production migration will ensure DB population
        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    @Test
    void appendCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 3;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, index * 2);
        byte[] addressBookBytes3 = Arrays.copyOfRange(addressBookBytes, index * 2, addressBookBytes.length);

        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);

        // for tests current address book will be null in tests. In production migration will ensure DB population
        update(addressBookServiceImpl, addressBookBytes1, 1L);
        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        append(addressBookServiceImpl, addressBookBytes2, 3L);
        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        append(addressBookServiceImpl, addressBookBytes3, 5L);
        assertThat(addressBookServiceImpl.getCurrent().getEntries()).hasSize(UPDATED.getNodeAddressCount());
        assertAddressBookData(addressBookBytes, 5);

        assertEquals(1, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount(), addressBookEntryRepository.count());

        AddressBook addressBook = addressBookServiceImpl.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(6L);
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());
    }

    @Test
    void appendPartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);

        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes1, 1L);

        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    @Test
    void ignoreEmptyByteArray() throws Exception {
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, new byte[] {}, 1L);
        append(addressBookServiceImpl, new byte[] {}, 2L);

        assertThat(addressBookServiceImpl.getCurrent()).isNull();
        assertEquals(0, addressBookRepository.count());
        assertEquals(0, addressBookEntryRepository.count());
    }

    @Test
    void isAddressBook() {
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);

        EntityId fileID = EntityId.of(0, 0, 234, EntityTypeEnum.FILE);
        boolean isAddressBook = addressBookServiceImpl.isAddressBook(fileID);
        assertThat(isAddressBook).isFalse();

        fileID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
        isAddressBook = addressBookServiceImpl.isAddressBook(fileID);
        assertThat(isAddressBook).isTrue();

        fileID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
        isAddressBook = addressBookServiceImpl.isAddressBook(fileID);
        assertThat(isAddressBook).isTrue();
    }

    @Test
    void verifyAddressBookUpdateAcrossSessions() {
        // create network book, perform an update and append
        byte[] addressBookBytes = FINAL.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes1, 1L);

        // create new address book and submit another append to complete file
        addressBookServiceImpl = new AddressBookServiceImpl(addressBookRepository, fileDataRepository);
        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        append(addressBookServiceImpl, addressBookBytes2, 3L);

        // verify valid address book and repository update
        AddressBook addressBook = addressBookServiceImpl.getCurrent();
        assertThat(addressBook.getEntries()).hasSize(FINAL.getNodeAddressCount());
        assertAddressBookData(FINAL.toByteArray(), 3);
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(4L);
    }

    @Test
    void appendCompleteFileAcrossFileIds() throws Exception {
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
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count());

        // perform file 102 first update and confirm no change to current address book and nodes addresses
        update(addressBookServiceImpl, addressBookBytes1, 1L); // fileID 102
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count()); // initial and 102 update

        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        // perform file 101 update and verify only partial address book is changed
        EntityId file101ID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
        FileData fileData = new FileData(3L, addressBook101Bytes1, file101ID, TransactionTypeEnum.FILEUPDATE
                .getProtoId());
        addressBookServiceImpl.update(fileData);

        // perform file 101 append
        fileData = new FileData(5L, addressBook101Bytes2, file101ID, TransactionTypeEnum.FILEAPPEND
                .getProtoId());
        addressBookServiceImpl.update(fileData);

        // verify partial bytes match 101 complete address book update
        assertAddressBookData(FINAL.toByteArray(), 5);
        assertEquals(15, addressBookEntryRepository.count());
        assertEquals(1, addressBookRepository.count());

        // verify current address book bytes still match original load and not 101 update and append
        assertThat(addressBookServiceImpl.getCurrent()).isNull();

        // perform file 102 append
        append(addressBookServiceImpl, addressBookBytes2, 7L);

        // verify address book and node addresses are updated
        AddressBook addressBook = addressBookServiceImpl.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(8L);
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());

        // 15 (101 update) + 12 (102 update)
        assertEquals(UPDATED.getNodeAddressCount() + FINAL
                .getNodeAddressCount(), addressBookEntryRepository
                .count());
        assertAddressBookData(UPDATED.toByteArray(), 7);
        assertEquals(2, addressBookRepository.count());
    }

    @Test
    void verifyAddressBookEndPointsAreSet() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        AddressBookServiceImpl addressBookServiceImpl = new AddressBookServiceImpl(
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes, 0L);

        // assert current addressBook is updated
        AddressBook addressBook = addressBookServiceImpl.getCurrent();
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());
        assertThat(addressBook.getStartConsensusTimestamp()).isNotNull();
        assertThat(addressBook.getEndConsensusTimestamp()).isNull();

        byte[] newAddressBookBytes = FINAL.toByteArray();

        update(addressBookServiceImpl, newAddressBookBytes, 10L);
        AddressBook newAddressBook = addressBookServiceImpl.getCurrent();
        assertThat(newAddressBook.getEntries()).hasSize(FINAL.getNodeAddressCount());
        assertAddressBookData(newAddressBookBytes, 10);

        assertEquals(2, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(), addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        AddressBook prevAddressBook = addressBookRepository.findById(1L).get();
        assertThat(prevAddressBook.getStartConsensusTimestamp()).isNotNull();
        assertThat(prevAddressBook.getEndConsensusTimestamp()).isNotNull();
    }

    private void assertAddressBookData(byte[] expected, long consensusTimestamp) {
        // addressBook.startConsensusTimestamp = consensusTimestamp + 1
        AddressBook actualAddressBook = addressBookRepository.findById(consensusTimestamp + 1)
                .get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }
}
