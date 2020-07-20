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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.TransactionBody;
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
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.NodeAddressRepository;

@SpringBootTest
public class NetworkAddressBookTest {

    private static final NodeAddressBook INITIAL = addressBook(5);
    private static final NodeAddressBook UPDATED = addressBook(10);
    private static final FileID FILE_ID = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build();
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

    private static void update(NetworkAddressBook networkAddressBook, byte[] contents) {
        networkAddressBook.updateFrom(TransactionBody.newBuilder()
                        .setFileUpdate(FileUpdateTransactionBody.newBuilder()
                                .setContents(ByteString.copyFrom(contents))
                                .build())
                        .build(),
                Instant.now().getEpochSecond(),
                FILE_ID);
    }

    private static void append(NetworkAddressBook networkAddressBook, byte[] contents) {
        networkAddressBook.updateFrom(TransactionBody.newBuilder()
                        .setFileAppend(FileAppendTransactionBody.newBuilder()
                                .setContents(ByteString.copyFrom(contents))
                                .build())
                        .build(),
                Instant.now().getEpochSecond(),
                FILE_ID);
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
        long timeStamp = Instant.now().getEpochSecond();
        update(networkAddressBook, addressBookBytes);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());

        // verify previous address book end date was updated
        List<AddressBook> addressBookList = addressBookRepository
                .findCompleteAddressBooks(Instant.now().getEpochSecond(), EntityId.of(FILE_ID));
        assertThat(addressBookList).isNotEmpty().hasSize(2);
        assertThat(addressBookList.get(0).getEndConsensusTimestamp()).isGreaterThan(0).isLessThan(timeStamp);
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
        update(networkAddressBook, addressBookPartial);

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookPartial, networkAddressBook.getPartialAddressBook().getFileData());
    }

    @Test
    void appendCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookBytes1);
        append(networkAddressBook, addressBookBytes2);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void appendPartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookBytes1);

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());
    }

    @Test
    void ignoreEmptyByteArray() throws Exception {
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, new byte[] {});
        append(networkAddressBook, new byte[] {});

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void isAddressBook() {
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);

        // verify 102
        boolean isAddressBook = networkAddressBook.isAddressBook(EntityId.of(FILE_ID));
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
        long timeStamp = Instant.now().getEpochSecond();
        update(networkAddressBook, addressBookBytes);

        // verify previous address book end date was updated
        List<AddressBook> addressBookList = addressBookRepository
                .findCompleteAddressBooks(Instant.now().getEpochSecond(), EntityId.of(FILE_ID));
        assertThat(addressBookList).isNotEmpty().hasSize(2);
        assertThat(addressBookList.get(0).getEndConsensusTimestamp()).isGreaterThan(0).isLessThan(timeStamp);
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
        update(networkAddressBook, addressBookBytes1);

        // create net address book and submit another append to complete file
        networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());

        append(networkAddressBook, addressBookBytes2);

        // verify valid address book and repository update
        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());
    }

    @Test
    void appendCompleteFileAcrossFileIds() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        byte[] addressBook101Bytes = INITIAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        assertEquals(INITIAL.getNodeAddressCount(), nodeAddressRepository.count());
        assertEquals(1, addressBookRepository.count());

        update(networkAddressBook, addressBookBytes1); // fileID 102
        assertEquals(INITIAL.getNodeAddressCount(), nodeAddressRepository.count());
        assertEquals(2, addressBookRepository.count());

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBookBytes1, networkAddressBook.getPartialAddressBook().getFileData());

        FileID file101ID = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(101).build();
        networkAddressBook.updateFrom(TransactionBody.newBuilder()
                        .setFileUpdate(FileUpdateTransactionBody.newBuilder()
                                .setContents(ByteString.copyFrom(addressBook101Bytes1))
                                .build())
                        .build(),
                Instant.now().getEpochSecond(),
                file101ID);

        networkAddressBook.updateFrom(TransactionBody.newBuilder()
                        .setFileAppend(FileAppendTransactionBody.newBuilder()
                                .setContents(ByteString.copyFrom(addressBook101Bytes2))
                                .build())
                        .build(),
                Instant.now().getEpochSecond(),
                file101ID);

        assertEquals(INITIAL.getNodeAddressCount() * 2, nodeAddressRepository.count());
        assertEquals(4, addressBookRepository.count());

        // verify partial bytes still match those for 102 UPDATE and not 101
        assertArrayEquals(INITIAL.toByteArray(), networkAddressBook.getCurrentAddressBook().getFileData());

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertArrayEquals(addressBook101Bytes, networkAddressBook.getPartialAddressBook().getFileData());

        append(networkAddressBook, addressBookBytes2); // fileID 102

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertArrayEquals(addressBookBytes, networkAddressBook.getCurrentAddressBook().getFileData());

        assertEquals(INITIAL.getNodeAddressCount() * 2 + UPDATED.getNodeAddressCount(), nodeAddressRepository.count());
        assertEquals(5, addressBookRepository.count());
    }
}
