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
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import com.hedera.mirror.importer.MirrorProperties;
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
    private Path tempPath;

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
        tempPath = addressBookPath.resolveSibling(addressBookPath + ".tmp");
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
        assertThat(tempPath).doesNotExist();
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
        update(networkAddressBook, addressBookBytes);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertThat(networkAddressBook.getCurrentAddressBook().getFileData()).isNotEmpty()
                .hasSize(addressBookBytes.length);
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
        assertThat(networkAddressBook.getPartialAddressBook().getFileData()).isNotEmpty()
                .hasSize(index);
    }

    @Test
    void appendPartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, addressBookBytes1);
        append(networkAddressBook, addressBookBytes2);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount() / 2);
        assertThat(networkAddressBook.getPartialAddressBook().getFileData()).isNotEmpty()
                .hasSize(index);
    }

    @Test
    void ignoreEmptyByteArray() throws Exception {
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties, addressBookRepository,
                nodeAddressRepository);
        update(networkAddressBook, new byte[] {});
        append(networkAddressBook, new byte[] {});

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertThat(addressBookPath).exists().hasBinaryContent(INITIAL.toByteArray());
        assertThat(tempPath).doesNotExist();
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
