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
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.HederaNetwork;

public class NetworkAddressBookTest {

    private static final NodeAddressBook INITIAL = addressBook(5);
    private static final NodeAddressBook UPDATED = addressBook(10);

    @TempDir
    Path dataPath;

    private MirrorProperties mirrorProperties;
    private Path addressBookPath;
    private Path tempPath;

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
                .build());
    }

    private static void append(NetworkAddressBook networkAddressBook, byte[] contents) {
        networkAddressBook.updateFrom(TransactionBody.newBuilder()
                .setFileAppend(FileAppendTransactionBody.newBuilder()
                        .setContents(ByteString.copyFrom(contents))
                        .build())
                .build());
    }

    @BeforeEach
    void setup() throws Exception {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        addressBookPath = mirrorProperties.getAddressBookPath();
        tempPath = addressBookPath.resolveSibling(addressBookPath + ".tmp");
        Files.write(addressBookPath, INITIAL.toByteArray());
    }

    @Test
    void startupWithClasspath() throws Exception {
        Files.deleteIfExists(addressBookPath);
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Loads default address book from classpath")
                .hasSize(4);
        assertThat(addressBookPath).exists();
        assertThat(tempPath).doesNotExist();
    }

    @Test
    void startupWithInitial() throws Exception {
        Path initialAddressBook = dataPath.resolve("initial.bin");
        Files.write(initialAddressBook, UPDATED.toByteArray());
        Files.deleteIfExists(addressBookPath);
        mirrorProperties.setInitialAddressBook(initialAddressBook);
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Loads default address book from filesystem")
                .hasSize(UPDATED.getNodeAddressCount());
        assertThat(addressBookPath).exists()
                .hasBinaryContent(UPDATED.toByteArray())
                .hasSameContentAs(initialAddressBook);
        assertThat(tempPath).doesNotExist();
    }

    @Test
    void startupWithExisting() throws Exception {
        mirrorProperties.setNetwork(HederaNetwork.MAINNET);
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Continue using previous address book")
                .hasSize(INITIAL.getNodeAddressCount());
        assertThat(addressBookPath).exists().hasBinaryContent(INITIAL.toByteArray());
        assertThat(tempPath).doesNotExist();
    }

    @Test
    void startupWithInvalid() throws Exception {
        Files.write(addressBookPath, new byte[] {'a', 'b', 'c'});
        assertThatThrownBy(() -> new NetworkAddressBook(mirrorProperties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateCompleteFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);
        update(networkAddressBook, addressBookBytes);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertThat(tempPath).doesNotExist();
        assertThat(addressBookPath).exists().hasBinaryContent(addressBookBytes);
    }

    @Test
    void updatePartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);
        update(networkAddressBook, addressBookPartial);

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertThat(addressBookPath).exists().hasBinaryContent(INITIAL.toByteArray());
        assertThat(tempPath).exists().hasBinaryContent(addressBookPartial);
    }

    @Test
    void appendPartialFile() throws Exception {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);
        update(networkAddressBook, addressBookBytes1);
        append(networkAddressBook, addressBookBytes2);

        assertThat(networkAddressBook.getAddresses()).hasSize(UPDATED.getNodeAddressCount());
        assertThat(addressBookPath).exists().hasBinaryContent(UPDATED.toByteArray());
        assertThat(tempPath).doesNotExist();
    }

    @Test
    void ignoreEmptyByteArray() throws Exception {
        NetworkAddressBook networkAddressBook = new NetworkAddressBook(mirrorProperties);
        update(networkAddressBook, new byte[] {});
        append(networkAddressBook, new byte[] {});

        assertThat(networkAddressBook.getAddresses()).hasSize(INITIAL.getNodeAddressCount());
        assertThat(addressBookPath).exists().hasBinaryContent(INITIAL.toByteArray());
        assertThat(tempPath).doesNotExist();
    }
}
