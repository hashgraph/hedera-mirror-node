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

import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.NodeAddressRepository;

@SpringBootTest
public class NetworkAddressBookTest {

    private static final NodeAddressBook INITIAL = addressBook(5);
    private static final NodeAddressBook UPDATED = addressBook(10);
    private static final NodeAddressBook FINAL = addressBook(15);
    private static final EntityId FILE_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
    private static final int CLASSPATH_BYTE_SIZE = 3428;

    @TempDir
    Path dataPath;

    private MirrorProperties mirrorProperties;
    private Path addressBookPath;

    @Resource
    protected AddressBookRepository addressBookRepository;
    @Resource
    protected NodeAddressRepository nodeAddressRepository;
    
    private static NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            builder.addNodeAddress(NodeAddress.newBuilder().setPortno(i).build());
        }
        return builder.build();
    }

    private static void update(NetworkAddressBook networkAddressBook, byte[] contents, long consensusTimeStamp) {
        networkAddressBook.updateFrom(
                consensusTimeStamp,
                contents,
                FILE_ENTITY_ID,
                false);
    }

    private static void append(NetworkAddressBook networkAddressBook, byte[] contents, long consensusTimeStamp) {
        networkAddressBook.updateFrom(
                consensusTimeStamp,
                contents,
                FILE_ENTITY_ID,
                true);
    }

    @BeforeEach
    void setup() throws Exception {
        addressBookRepository.deleteAll();
        nodeAddressRepository.deleteAll();
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        addressBookPath = mirrorProperties.getAddressBookPath();
        Files.write(addressBookPath, INITIAL.toByteArray());
    }

    @Test
    void startupWithClasspath() throws Exception {
        Files.deleteIfExists(addressBookPath);
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Loads default address book from classpath")
                .hasSize(4);
        assertThat(networkAddressBook.getCurrentAddressBook().getFileData()).isNotEmpty()
                .hasSize(CLASSPATH_BYTE_SIZE);
    }

    @Test
    void startupWithInitial() throws Exception {
        Path initialAddressBook = dataPath.resolve("initial.bin");
        Files.write(initialAddressBook, UPDATED.toByteArray());
        Files.deleteIfExists(addressBookPath);
        mirrorProperties.setInitialAddressBook(initialAddressBook);
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Loads default address book from filesystem")
                .hasSize(UPDATED.getNodeAddressCount());

        assertThat(networkAddressBook.getCurrentAddressBook().getFileData()).isNotEmpty()
                .hasSize(UPDATED.toByteArray().length);
    }

    @Test
    void startupWithExisting() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Continue using previous address book")
                .hasSize(INITIAL.getNodeAddressCount());
        assertThat(addressBookPath).exists().hasBinaryContent(INITIAL.toByteArray());
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void startupWithInvalid() throws Exception {
        Files.write(addressBookPath, new byte[] {'a', 'b', 'c'});
        assertThatThrownBy(() -> new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupWithOtherNetwork() throws Exception {
        Files.deleteIfExists(addressBookPath);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        assertThatThrownBy(() -> new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        assertThat(networkAddressBook.getCurrentAddressBook().getEndConsensusTimestamp()).isNull();
        update(networkAddressBook, addressBookBytes, 2L);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());

        // verify previous address book end date was updated
        List<AddressBook> addressBookList = addressBookRepository
                .findCompleteAddressBooks(Instant.now().getEpochSecond(), EntityId.of(FILE_ENTITY_ID));
        assertThat(addressBookList).isNotEmpty().hasSize(2);
        assertThat(addressBookList.get(0).getEndConsensusTimestamp()).isGreaterThan(0).isLessThan(2L);
        assertThat(addressBookList.get(0).getEndConsensusTimestamp()).isGreaterThan(0)
                .isLessThan(addressBookList.get(1).getConsensusTimestamp());
    }

    @Test
    void updatePartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookPartial, 1L);

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookPartial, networkAddressBook.getPartialAddressBook().getFileData());
    }

    @Test
    void appendCompleteFile() throws Exception {
        byte[] addressBookBytes = FINAL.toByteArray();
        int index = addressBookBytes.length / 3;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, index * 2);
        byte[] addressBookBytes2Partial = Arrays.copyOfRange(addressBookBytes, 0, index * 2);
        byte[] addressBookBytes3 = Arrays.copyOfRange(addressBookBytes, index * 2, addressBookBytes.length);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookBytes1, 1L);
        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());

        append(networkAddressBook, addressBookBytes2, 2L);
        assertThat(networkAddressBook.getAddresses()).hasSize((FINAL.getNodeAddressCount() / 3) * 2);
        assertArrayEquals(addressBookBytes2Partial, networkAddressBook.getPartialAddressBook().getFileData());

        append(networkAddressBook, addressBookBytes3, 3L);
        assertThat(networkAddressBook.getAddresses()).hasSize(FINAL.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void appendPartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookBytes1, 1L);

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());
    }

    @Test
    void ignoreEmptyByteArray() throws Exception {
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, new byte[] {}, 1L);
        append(networkAddressBook, new byte[] {}, 2L);

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void isAddressBook() {
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);

        // verify 102
        boolean isAddressBook = networkAddressBook.isAddressBook(EntityId.of(FILE_ENTITY_ID));
        assertThat(isAddressBook).isTrue();

        // verify 101
        isAddressBook = networkAddressBook
                .isAddressBook(EntityId.of(FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(101).build()));
        assertThat(isAddressBook).isTrue();

        long entityNum = 123;
        // verify non address book file ID
        isAddressBook = networkAddressBook
                .isAddressBook(EntityId.of(FileID.newBuilder().setShardNum(0).setRealmNum(0)
                        .setFileNum(entityNum).build()));
        assertThat(isAddressBook).isFalse();

        // verify new future address book file id
        mirrorProperties.setAddressBookFileIdEntityNum(entityNum);
        isAddressBook = networkAddressBook
                .isAddressBook(EntityId.of(FileID.newBuilder().setShardNum(0).setRealmNum(0)
                        .setFileNum(mirrorProperties.getAddressBookFileIdEntityNum()).build()));
        assertThat(isAddressBook).isTrue();
    }

    @Test
    void verifyPreviousAddressBookEndTimeUpdate() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        assertThat(networkAddressBook.getCurrentAddressBook().getEndConsensusTimestamp()).isNull();
        update(networkAddressBook, addressBookBytes, 2L);

        // verify previous address book end date was updated
        List<AddressBook> addressBookList = addressBookRepository
                .findCompleteAddressBooks(3L, EntityId.of(FILE_ENTITY_ID));
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

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookBytes1, 1L);

        // create net address book and submit another append to complete file
        networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());

        append(networkAddressBook, addressBookBytes2, 2L);

        // verify valid address book and repository update
        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void appendCompleteFileAcrossFileIds() throws Exception {
        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = INITIAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);

        // init address book and verify initial state
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        assertEquals(INITIAL.getNodeAddressCount(), nodeAddressRepository.count());
        assertEquals(1, addressBookRepository.count());

        // perform file 102 first update and confirm no change to current address book and nodes addresses
        update(networkAddressBook, addressBookBytes1, 1L); // fileID 102
        assertEquals(INITIAL.getNodeAddressCount(), nodeAddressRepository.count());
        assertEquals(2, addressBookRepository.count()); // initial and 102 update

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());

        // perform file 101 update and verify only partial address book is changed
        EntityId file101ID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
        networkAddressBook.updateFrom(
                2L,
                addressBook101Bytes1,
                file101ID,
                false);

        assertArrayEquals(addressBook101Bytes1, networkAddressBook.getPartialAddressBook().getFileData());

        // perform file 101 append
        networkAddressBook.updateFrom(
                3L,
                addressBook101Bytes2,
                file101ID,
                true);

        // verify current address book bytes still match original load and not 101 update and append
        assertEquals(INITIAL.getNodeAddressCount() * 2, nodeAddressRepository.count());
        assertEquals(4, addressBookRepository.count());
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());

        // verify partial bytes match 101 complete address book update
        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBook101Bytes, networkAddressBook.getPartialAddressBook().getFileData());

        // perform file 102 append
        append(networkAddressBook, addressBookBytes2, 4L);

        // verify address book and node addresses are updated
        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());

        assertEquals(INITIAL.getNodeAddressCount() * 2 + UPDATED.getNodeAddressCount(), nodeAddressRepository.count());
        assertEquals(5, addressBookRepository.count());
    }

    @Test
    void isAddressBook() {

    }

    @Test
    void verifyPreviousAddressBookEndTimeUpdate() {

    }

    @Test
    void verifyAddressBookUpdateAcrossSessions() {
        // create network book, perform an update and append

        // create net address book and submit another append to complete file

        // verify valid address book and repository update
    }

    @Test
    void verifyRepositoryUpdates() {
        // add repository verifications for scenarios
        // first importer start
        // importer start after first start
        // address book after single update
        // address book after update and append
        // [stretch] address book across fileID's
    }
}
