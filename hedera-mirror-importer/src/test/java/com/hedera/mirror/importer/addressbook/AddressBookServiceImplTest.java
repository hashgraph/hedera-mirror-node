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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

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
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;

@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@SpringBootTest
public class AddressBookServiceImplTest {

    private static final NodeAddressBook UPDATED = addressBook(10);
    private static final NodeAddressBook FINAL = addressBook(15);
    private static final EntityId FILE_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
    private static final int CLASSPATH_BYTE_SIZE = 3428;
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
            builder.addNodeAddress(NodeAddress.newBuilder().setPortno(i).build());
        }
        return builder.build();
    }

    private byte[] initialAddressBook() throws IOException {
        return FileUtils.readFileToByteArray(initialAddressBook);
    }

    private static void update(AddressBookService addressBookServiceImpl, byte[] contents,
                               long consensusTimeStamp) {
        FileData fileData = new FileData(consensusTimeStamp, contents, FILE_ENTITY_ID, TransactionTypeEnum.FILEUPDATE
                .ordinal());
        addressBookServiceImpl.update(fileData);
    }

    private static void append(AddressBookService addressBookServiceImpl, byte[] contents,
                               long consensusTimeStamp) {
        FileData fileData = new FileData(consensusTimeStamp, contents, FILE_ENTITY_ID, TransactionTypeEnum.FILEAPPEND
                .ordinal());
        addressBookServiceImpl.update(fileData);
    }

    @BeforeEach
    void setup() throws Exception {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
    }

    @Test
    void startupWithClasspath() throws Exception {
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);

        assertThat(addressBookServiceImpl.getAddresses())
                .describedAs("Loads default address book from classpath")
                .hasSize(4);
        assertEquals(1, addressBookRepository.count());
        assertEquals(4, addressBookEntryRepository.count());

        byte[] addressBookBytes = initialAddressBook();
        assertAddressBookData(addressBookBytes, 0);
    }

    @Test
    void startupWithOtherNetwork() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        assertThatThrownBy(() -> new AddressBookServiceImpl(mirrorProperties, addressBookRepository,
                fileDataRepository))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        AddressBookServiceImpl addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes, 2L);

        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);
        assertAddressBookData(UPDATED.toByteArray(), 2);
        assertEquals(2, addressBookRepository.count());
        assertEquals(14, addressBookEntryRepository.count());

        List<AddressBook> addressBookList = addressBookRepository
                .findCompleteAddressBooks(3L, FILE_ENTITY_ID);
        assertThat(addressBookList).isNotEmpty().hasSize(2);

        // verify new address book is loaded on restart
        addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
    }

    @Test
    void updatePartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        AddressBookServiceImpl addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookPartial, 1L);

        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);
        assertEquals(1, addressBookRepository.count());
        assertEquals(4, addressBookEntryRepository.count());
    }

    @Test
    void appendCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 3;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, index * 2);
        byte[] addressBookBytes3 = Arrays.copyOfRange(addressBookBytes, index * 2, addressBookBytes.length);

        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes1, 1L);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        append(addressBookServiceImpl, addressBookBytes2, 2L);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        append(addressBookServiceImpl, addressBookBytes3, 3L);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);
        assertAddressBookData(addressBookBytes, 3);

        assertEquals(2, addressBookRepository.count());
        assertEquals(14, addressBookEntryRepository.count());

        // verify new address book is loaded on restart
        addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
    }

    @Test
    void appendPartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);

        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes1, 1L);

        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        assertEquals(1, addressBookRepository.count());
        assertEquals(4, addressBookEntryRepository.count());
    }

    @Test
    void ignoreEmptyByteArray() throws Exception {
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, new byte[] {}, 1L);
        append(addressBookServiceImpl, new byte[] {}, 2L);

        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);
        assertAddressBookData(initialAddressBook(), 0);
        assertEquals(1, addressBookRepository.count());
        assertEquals(4, addressBookEntryRepository.count());
    }

    @Test
    void isAddressBook() {
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);

        byte[] addressBookBytes = UPDATED.toByteArray();
        EntityId fileID = EntityId.of(0, 0, 234, EntityTypeEnum.FILE);
        FileData fileData = new FileData(2L, addressBookBytes, fileID, TransactionTypeEnum.FILEUPDATE
                .ordinal());
        addressBookServiceImpl.update(fileData);

        fileID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
        fileData = new FileData(2L, addressBookBytes, fileID, TransactionTypeEnum.FILEUPDATE
                .ordinal());
        addressBookServiceImpl.update(fileData);

        // only file 102 update was processed as an address book
        assertEquals(2, addressBookRepository.count());
        assertEquals(14, addressBookEntryRepository.count()); // bootstrap 4 + update 10
        assertAddressBookData(addressBookBytes, 2);
    }

    @Test
    void verifyPreviousAddressBookEndTimeUpdate() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes, 2L);

        // verify previous address book end date was updated
        List<AddressBook> addressBookList = addressBookRepository
                .findCompleteAddressBooks(2L, FILE_ENTITY_ID);
        assertThat(addressBookList).isNotEmpty().hasSize(2);
        assertThat(addressBookList.get(0).getEndConsensusTimestamp()).isGreaterThan(0).isLessThan(2L);
        assertThat(addressBookList.get(0).getEndConsensusTimestamp()).isGreaterThan(0)
                .isLessThan(addressBookList.get(1).getConsensusTimestamp());
    }

    @Test
    void verifyAddressBookUpdateAcrossSessions() {
        // create network book, perform an update and append
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        update(addressBookServiceImpl, addressBookBytes1, 1L);

        // create net address book and submit another append to complete file
        addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties, addressBookRepository,
                fileDataRepository);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        append(addressBookServiceImpl, addressBookBytes2, 2L);

        // verify valid address book and repository update
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);
        assertAddressBookData(UPDATED.toByteArray(), 2);

        // verify new address book is loaded on restart
        addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
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
        AddressBookService addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        assertEquals(INITIAL_NODE_COUNT, addressBookEntryRepository.count());
        assertEquals(1, addressBookRepository.count());

        // perform file 102 first update and confirm no change to current address book and nodes addresses
        update(addressBookServiceImpl, addressBookBytes1, 1L); // fileID 102
        assertEquals(INITIAL_NODE_COUNT, addressBookEntryRepository.count());
        assertEquals(1, addressBookRepository.count()); // initial and 102 update

        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        // perform file 101 update and verify only partial address book is changed
        EntityId file101ID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
        FileData fileData = new FileData(2L, addressBook101Bytes1, file101ID, TransactionTypeEnum.FILEUPDATE
                .ordinal());
        addressBookServiceImpl.update(fileData);

        // perform file 101 append
        fileData = new FileData(3L, addressBook101Bytes2, file101ID, TransactionTypeEnum.FILEAPPEND
                .ordinal());
        addressBookServiceImpl.update(fileData);

        // verify current address book bytes still match original load and not 101 update and append
        assertEquals(INITIAL_NODE_COUNT + FINAL.getNodeAddressCount(), addressBookEntryRepository.count());
        assertEquals(2, addressBookRepository.count());
        assertAddressBookData(initialAddressBook(), 0);

        // verify partial bytes match 101 complete address book update
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        // perform file 102 append
        append(addressBookServiceImpl, addressBookBytes2, 4L);

        // verify address book and node addresses are updated
        assertThat(addressBookServiceImpl.getAddresses()).hasSize(INITIAL_NODE_COUNT);

        assertEquals(INITIAL_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL
                .getNodeAddressCount(), addressBookEntryRepository
                .count());
        assertAddressBookData(UPDATED.toByteArray(), 4);
        assertEquals(3, addressBookRepository.count());

        // verify new address book is loaded on restart
        addressBookServiceImpl = new AddressBookServiceImpl(mirrorProperties,
                addressBookRepository,
                fileDataRepository);
        assertThat(addressBookServiceImpl.getAddresses())
                .hasSize(UPDATED.getNodeAddressCount());
    }

    private void assertAddressBookData(byte[] expected, long consensusTimestamp) {
        AddressBook actualAddressBook = addressBookRepository.findById(consensusTimestamp)
                .get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }
}
