package com.hedera.mirror.importer.addressbook;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.addressbook.AddressBookServiceImpl.CACHE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ResourceUtils;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.AddressBookServiceEndpointRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AddressBookServiceImplTest extends IntegrationTest {

    private static final NodeAddressBook UPDATED = addressBook(10, 0);
    private static final NodeAddressBook FINAL = addressBook(15, 0);
    private static final int TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT = 4;
    private static final String baseAccountId = "0.0.";
    private static final int basePort = 50211;
    private static byte[] initialAddressBookBytes;

    private final AddressBookEntryRepository addressBookEntryRepository;
    private final AddressBookRepository addressBookRepository;
    private final AddressBookService addressBookService;
    private final AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;
    @Qualifier(CacheConfiguration.EXPIRE_AFTER_5M)
    private final CacheManager cacheManager;
    private final FileDataRepository fileDataRepository;
    private final MirrorProperties mirrorProperties;
    private final NodeStakeRepository nodeStakeRepository;
    private final TransactionTemplate transactionTemplate;

    @TempDir
    private Path dataPath;

    @Value("classpath:addressbook")
    private Path testPath;

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

    @BeforeAll
    static void setupAll() throws IOException {
        Path addressBookPath = ResourceUtils.getFile("classpath:addressbook/testnet").toPath();
        initialAddressBookBytes = Files.readAllBytes(addressBookPath);
    }

    private FileData createFileData(byte[] contents, long consensusTimeStamp, boolean is102,
                                    TransactionType transactionType) {
        EntityId entityId = is102 ? AddressBookServiceImpl.FILE_102 :
                AddressBookServiceImpl.FILE_101;
        return new FileData(consensusTimeStamp, contents, entityId, transactionType.getProtoId());
    }

    private FileData store(byte[] contents, long consensusTimeStamp, boolean is102) {
        FileData fileData = createFileData(contents, consensusTimeStamp, is102, TransactionType.FILEUPDATE);
        return fileDataRepository.save(fileData);
    }

    private void update(byte[] contents, long consensusTimeStamp, boolean is102) {
        FileData fileData = createFileData(contents, consensusTimeStamp, is102, TransactionType.FILEUPDATE);
        addressBookService.update(fileData);
        fileDataRepository.save(fileData);
    }

    private void append(byte[] contents, long consensusTimeStamp, boolean is102) {
        FileData fileData = createFileData(contents, consensusTimeStamp, is102, TransactionType.FILEAPPEND);
        fileDataRepository.save(fileData);
        addressBookService.update(fileData);
    }

    @Test
    void startupWithOtherNetworkIncorrectInitialAddressBookPath() {
        MirrorProperties otherNetworkMirrorProperties = new MirrorProperties();
        otherNetworkMirrorProperties.setDataPath(dataPath);
        otherNetworkMirrorProperties.setInitialAddressBook(dataPath.resolve("test-v1"));
        otherNetworkMirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        AddressBookService customAddressBookService = new AddressBookServiceImpl(addressBookRepository,
                fileDataRepository, otherNetworkMirrorProperties, nodeStakeRepository, transactionTemplate);
        assertThrows(IllegalStateException.class, () -> {
            customAddressBookService.getCurrent();
        });
        assertEquals(0, addressBookRepository.count());
    }

    @Test
    void startupWithDefaultNetwork() {
        // init address book and verify initial state
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count());

        AddressBook addressBook = addressBookService.getCurrent();

        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(1L);
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void startupWithOtherNetwork() {
        // copy other addressbook to file system
        FileCopier fileCopier = FileCopier.create(testPath, dataPath)
                .from("")
                .filterFiles("test-v1")
                .to("");
        fileCopier.copy();

        MirrorProperties otherNetworkMirrorProperties = new MirrorProperties();
        otherNetworkMirrorProperties.setDataPath(dataPath);
        otherNetworkMirrorProperties.setInitialAddressBook(dataPath.resolve("test-v1"));
        otherNetworkMirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        AddressBookService customAddressBookService = new AddressBookServiceImpl(addressBookRepository,
                fileDataRepository, otherNetworkMirrorProperties, nodeStakeRepository, transactionTemplate);
        AddressBook addressBook = customAddressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(1L);
        assertEquals(1, addressBookRepository.count());
    }

    @Test
    void updateCompleteFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        long addressBookConsensusTimeStamp = 5L;
        update(addressBookBytes, addressBookConsensusTimeStamp, true);

        // assert current addressBook is updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(addressBookConsensusTimeStamp + 1);
        assertAddressBook(addressBook, UPDATED);

        // assert repositories contain updates
        assertAddressBookData(UPDATED.toByteArray(), addressBookConsensusTimeStamp + 1);
        assertEquals(2, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount(), addressBookEntryRepository
                .count());
    }

    @Test
    void cacheAndEvictAddressBook() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        long addressBookConsensusTimeStamp = 5L;
        update(addressBookBytes, addressBookConsensusTimeStamp, true);

        //verify cache is empty to start
        assertNull(cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY));

        //verify getCurrent() adds an entry to the cache
        var nodes = addressBookService.getNodes();
        var nodesCache = cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY).get();
        assertNotNull(nodesCache);
        assertThat(nodesCache).isEqualTo(nodes);

        //verify updating the address book evicts the cache.
        update(addressBookBytes, addressBookConsensusTimeStamp + 1, true);
        assertNull(cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY));
    }

    @Test
    void updatePartialFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        update(addressBookPartial, 5L, true);

        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void appendCompleteFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 3;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, index * 2);
        byte[] addressBookBytes3 = Arrays.copyOfRange(addressBookBytes, index * 2, addressBookBytes.length);

        update(addressBookBytes1, 2L, true);
        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        append(addressBookBytes2, 3L, true);
        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        long addressBookConsensusTimeStamp = 5L;
        append(addressBookBytes3, addressBookConsensusTimeStamp, true);
        assertAddressBook(addressBookService.getCurrent(), UPDATED);

        assertAddressBookData(addressBookBytes, addressBookConsensusTimeStamp + 1);

        assertEquals(2, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount() + TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository
                .count());

        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(6L);
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());
    }

    @Test
    void appendPartialFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        update(addressBookBytes1, 2L, true);

        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void ignoreEmptyByteArray() {
        update(new byte[] {}, 2L, true);
        append(new byte[] {}, 3L, true);

        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void isAddressBook() {
        EntityId fileID = EntityId.of(0, 0, 234, EntityType.FILE);
        boolean isAddressBook = addressBookService.isAddressBook(fileID);
        assertThat(isAddressBook).isFalse();

        fileID = EntityId.of(0, 0, 101, EntityType.FILE);
        isAddressBook = addressBookService.isAddressBook(fileID);
        assertThat(isAddressBook).isTrue();

        fileID = EntityId.of(0, 0, 102, EntityType.FILE);
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

        update(addressBookBytes1, 2L, true);

        // create new address book and submit another append to complete file
        addressBookService.getCurrent();

        long addressBookConsensusTimeStamp = 3L;
        append(addressBookBytes2, addressBookConsensusTimeStamp, true);

        // verify valid address book and repository update
        AddressBook addressBook = addressBookService.getCurrent();
        assertAddressBook(addressBook, FINAL);
        assertAddressBookData(FINAL.toByteArray(), addressBookConsensusTimeStamp + 1);
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(addressBookConsensusTimeStamp + 1);
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
        update(addressBookBytes1, 2L, true); // fileID 102
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
        assertEquals(1, addressBookRepository.count()); // initial

        addressBookService.getCurrent();

        update(addressBook101Bytes1, 3L, false);
        long addressBook101ConsensusTimeStamp = 5L;
        append(addressBook101Bytes2, addressBook101ConsensusTimeStamp, false);

        // verify partial bytes match 101 complete address book update
        assertAddressBookData(FINAL.toByteArray(), addressBook101ConsensusTimeStamp + 1);
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + FINAL.getNodeAddressCount(), addressBookEntryRepository
                .count());
        assertEquals(2, addressBookRepository.count());

        // verify current address book bytes still match original load and not 101 update and append
        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        // perform file 102 append
        long addressBook102ConsensusTimeStamp = 7L;
        append(addressBookBytes2, addressBook102ConsensusTimeStamp, true);

        // verify address book and node addresses are updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(addressBook102ConsensusTimeStamp + 1);
        assertAddressBook(addressBookService.getCurrent(), UPDATED);

        // 15 (101 update) + 12 (102 update)
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(),
                addressBookEntryRepository.count());
        assertAddressBookData(UPDATED.toByteArray(), addressBook102ConsensusTimeStamp + 1);
        assertEquals(3, addressBookRepository.count());
    }

    @Test
    void verify102AddressBookEndPointsAreSet() {
        long initialTimestamp = 5L;
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, initialTimestamp, true);

        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(addressBookBytes, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_102, AddressBook::getFileId)
                .returns(UPDATED.getNodeAddressCount(), AddressBook::getNodeCount)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, UPDATED));

        long newTimestamp = 10L;
        byte[] newAddressBookBytes = FINAL.toByteArray();
        update(newAddressBookBytes, newTimestamp, true);

        assertThat(addressBookRepository.findById(newTimestamp + 1))
                .get()
                .returns(newAddressBookBytes, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_102, AddressBook::getFileId)
                .returns(newTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, FINAL));

        assertEquals(3, addressBookRepository.count()); // bootstrap, UPDATED and FINAL
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL
                .getNodeAddressCount(), addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(AddressBookServiceImpl.FILE_102, AddressBook::getFileId)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(newTimestamp, AddressBook::getEndConsensusTimestamp);
    }

    @Test
    void verify101AddressBookEndPointsAreSet() {
        long initialTimestamp = 5L;
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, initialTimestamp, false);

        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(addressBookBytes, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_101, AddressBook::getFileId)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, UPDATED));

        long newTimestamp = 10L;
        byte[] newAddressBookBytes = FINAL.toByteArray();
        update(newAddressBookBytes, newTimestamp, false);

        assertThat(addressBookRepository.findById(newTimestamp + 1))
                .get()
                .returns(newAddressBookBytes, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_101, AddressBook::getFileId)
                .returns(newTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, FINAL));

        assertEquals(3, addressBookRepository.count()); // bootstrap, UPDATED and FINAL
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL
                .getNodeAddressCount(), addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(AddressBookServiceImpl.FILE_101, AddressBook::getFileId)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(newTimestamp, AddressBook::getEndConsensusTimestamp);
    }

    @Test
    void verify101DoesntUpdate102() {
        long timestamp102 = 2L;
        byte[] addressBookBytes102 = UPDATED.toByteArray();
        update(addressBookBytes102, timestamp102, true);

        assertThat(addressBookRepository.findById(timestamp102 + 1))
                .get()
                .returns(addressBookBytes102, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_102, AddressBook::getFileId)
                .returns(timestamp102 + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, UPDATED));

        long timestamp101 = 10L;
        byte[] addressBookBytes101 = FINAL.toByteArray();
        update(addressBookBytes101, timestamp101, false);

        assertThat(addressBookRepository.findById(timestamp101 + 1))
                .get()
                .returns(addressBookBytes101, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_101, AddressBook::getFileId)
                .returns(timestamp101 + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, FINAL));
    }

    @Test
    void verifyAddressBookEntriesWithNodeIdAndPortNotSet() {
        Map<String, Integer> memoToNodeIdMap = Map.of(
                "0.0.3", 0,
                "0.0.4", 1
        );

        String[] accountIds = memoToNodeIdMap.keySet().stream().sorted().toArray(String[]::new);

        // nodeId 0, port 0
        NodeAddress nodeAddress1 = NodeAddress.newBuilder()
                .setIpAddress(ByteString.copyFromUtf8("127.0.0.1"))
                .setMemo(ByteString.copyFromUtf8(accountIds[0]))
                .setNodeAccountId(AccountID.newBuilder().setAccountNum(3))
                .setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                .setRSAPubKey("rsa+public/key")
                .build();
        // nodeId 0, port 50211
        NodeAddress nodeAddress2 = NodeAddress.newBuilder(nodeAddress1)
                .setIpAddress(ByteString.copyFromUtf8("127.0.0.2"))
                .setMemo(ByteString.copyFromUtf8(accountIds[1]))
                .setNodeAccountId(AccountID.newBuilder().setAccountNum(4))
                .setPortno(50211)
                .build();
        NodeAddressBook nodeAddressBook = NodeAddressBook.newBuilder()
                .addAllNodeAddress(List.of(nodeAddress1, nodeAddress2))
                .build();

        byte[] addressBookBytes = nodeAddressBook.toByteArray();
        long addressBookConsensusTimeStamp = 5L;
        update(addressBookBytes, addressBookConsensusTimeStamp, true);

        assertAddressBookData(addressBookBytes, addressBookConsensusTimeStamp + 1);
        AddressBook addressBook = addressBookService.getCurrent();
        ListAssert<AddressBookEntry> listAssert = assertThat(addressBook.getEntries())
                .hasSize(nodeAddressBook.getNodeAddressCount());
        for (NodeAddress nodeAddress : nodeAddressBook.getNodeAddressList()) {
            int expectedNodeId = memoToNodeIdMap.get(nodeAddress.getMemo().toStringUtf8());
            listAssert.anySatisfy(abe -> {
                assertThat(abe.getMemo()).isEqualTo(nodeAddress.getMemo().toStringUtf8());
                assertThat(abe.getNodeAccountId()).isEqualTo(EntityId.of(nodeAddress.getNodeAccountId()));
                assertThat(abe.getNodeCertHash()).isEqualTo(nodeAddress.getNodeCertHash().toByteArray());
                assertThat(abe.getPublicKey()).isEqualTo(nodeAddress.getRSAPubKey());
                assertThat(abe.getNodeId()).isEqualTo(expectedNodeId); // both entries have null node id

                assertAddressBookEndPoints(abe.getServiceEndpoints(), nodeAddress.getServiceEndpointList());
            });
        }
    }

    @Test
    void verifyGetCurrentCallPopulatesInitialAddressBook() {
        assertEquals(0, addressBookRepository.count());

        assertThat(addressBookService.getCurrent())
                .returns(initialAddressBookBytes, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_102, AddressBook::getFileId)
                .returns(1L, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp);
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void verifyUpdateCallPopulatesInitialAddressBook() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        assertEquals(0, addressBookRepository.count());
        update(addressBookPartial, 2L, false);

        assertThat(addressBookService.getCurrent())
                .returns(initialAddressBookBytes, AddressBook::getFileData)
                .returns(AddressBookServiceImpl.FILE_102, AddressBook::getFileId)
                .returns(1L, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp);
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void verifyAddressBookMigrationInitiatedByDownloader() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        byte[] addressBookBytes3 = FINAL.toByteArray();
        store(addressBookBytes3, 4L, false);

        byte[] addressBookBytes4 = FINAL.toByteArray();
        store(addressBookBytes4, 5L, true);

        // migration
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(6L);
        assertAddressBook(addressBook, FINAL);

        assertEquals(4, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count()); // initial plus 4 files
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + (UPDATED.getNodeAddressCount() * 2L) +
                (FINAL.getNodeAddressCount() * 2L), addressBookEntryRepository.count());
    }

    @Test
    void verifyAddressBookMigrationInitiatedByParser() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        byte[] addressBookBytes3 = FINAL.toByteArray();
        store(addressBookBytes3, 4L, false);

        byte[] addressBookBytes4 = FINAL.toByteArray();
        store(addressBookBytes4, 5L, true);

        // migration
        int addressBook5NodeCount = 20;
        byte[] addressBookBytes5 = addressBook(addressBook5NodeCount, 0).toByteArray();
        addressBookService.update(createFileData(addressBookBytes5, 6L, true, TransactionType.FILEUPDATE));

        assertEquals(4, fileDataRepository.count());
        assertEquals(6, addressBookRepository.count()); // initial plus 5 files
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + (UPDATED.getNodeAddressCount() * 2L) +
                (FINAL.getNodeAddressCount() * 2L) + addressBook5NodeCount, addressBookEntryRepository.count());
    }

    @Test
    void verifyAddressBookWithServiceEndpointsOnly() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 4;

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    null,
                    List.of(
                            String.format("127.0.%d.1", i),
                            String.format("127.0.%d.2", i),
                            String.format("127.0.%d.3", i),
                            String.format("127.0.%d.4", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, false);

        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries,
                addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode,
                addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyAddressBookWithDeprecatedIpOnly() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 1;

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    String.format("127.0.%d.0", i),
                    List.of()));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, false);

        assertArrayEquals(initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries,
                addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode,
                addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyAddressBookWithDeprecatedIpAndServiceEndpoints() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 5; // deprecated ip + service endpoints

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    String.format("127.0.%d.0", i),
                    List.of(
                            String.format("127.0.%d.1", i),
                            String.format("127.0.%d.2", i),
                            String.format("127.0.%d.3", i),
                            String.format("127.0.%d.4", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries,
                addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode,
                addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyDuplicateNodeAddressPerNodeIdAreCollapsed() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 6; // deprecated ip, service endpoints, deprecated ip + service endpoints

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            // deprecated ip
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    String.format("127.0.%d.0", i),
                    List.of()));

            // subset of only service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    null,
                    List.of(
                            String.format("127.0.%d.1", i),
                            String.format("127.0.%d.2", i))));

            // another deprecated ip and more service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    String.format("128.0.%d.0", i),
                    List.of(
                            String.format("127.0.%d.3", i),
                            String.format("127.0.%d.4", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries,
                addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode,
                addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyEmptyDeprecatedMemo() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 1; // service endpoint

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            // empty deprecated ip
            nodeAddressList.add(getNodeAddress(
                    i,
                    "",
                    null,
                    List.of(String.format("127.0.%d.0", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries,
                addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode,
                addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyDuplicateEndpointsPerNodeAddressAreCollapsed() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 3; // 127.0.<nodeId>.0, 127.0.<nodeId>.1, 127.0.<nodeId>.2

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            // deprecated ip
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    String.format("127.0.%d.0", i),
                    List.of()));

            // subset of only service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    null,
                    List.of(
                            String.format("127.0.%d.0", i))));

            // another deprecated ip and more service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    baseAccountId + i,
                    String.format("127.0.%d.0", i),
                    List.of(
                            String.format("127.0.%d.1", i),
                            String.format("127.0.%d.2", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries,
                addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode,
                addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyAddressBookMigrationWithNewFileDataAfterCurrentAddressBook() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        // initial migration
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(4L);
        assertAddressBook(addressBook, UPDATED);

        // valid file data added but no address book produced
        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);
        fileDataRepository.save(createFileData(addressBook101Bytes1, 4L, false, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook101Bytes2, 5L, false, TransactionType.FILEAPPEND));

        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBook102Bytes = FINAL.toByteArray();
        int index = addressBook102Bytes.length / 2;
        byte[] addressBook102Bytes1 = Arrays.copyOfRange(addressBook102Bytes, 0, index);
        byte[] addressBook102Bytes2 = Arrays.copyOfRange(addressBook102Bytes, index, addressBook102Bytes.length);
        fileDataRepository.save(createFileData(addressBook102Bytes1, 6L, true, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook102Bytes2, 7L, true, TransactionType.FILEAPPEND));

        // migration on startup
        AddressBook newAddressBook = addressBookService.migrate();
        assertThat(newAddressBook.getStartConsensusTimestamp()).isEqualTo(8L);
        assertAddressBook(newAddressBook, FINAL);

        assertEquals(6, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count()); // initial plus 4 files
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + (UPDATED.getNodeAddressCount() * 2L) +
                (FINAL.getNodeAddressCount() * 2L), addressBookEntryRepository.count());
    }

    @Test
    void verifyUpdateWithNewFileDataAfterCurrentAddressBook() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        // initial migration
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(4L);
        assertAddressBook(addressBook, UPDATED);

        // valid file data added but no address book produced
        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);
        fileDataRepository.save(createFileData(addressBook101Bytes1, 4L, false, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook101Bytes2, 5L, false, TransactionType.FILEAPPEND));

        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBook102Bytes = FINAL.toByteArray();
        int index = addressBook102Bytes.length / 2;
        byte[] addressBook102Bytes1 = Arrays.copyOfRange(addressBook102Bytes, 0, index);
        byte[] addressBook102Bytes2 = Arrays.copyOfRange(addressBook102Bytes, index, addressBook102Bytes.length);
        fileDataRepository.save(createFileData(addressBook102Bytes1, 6L, true, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook102Bytes2, 7L, true, TransactionType.FILEAPPEND));

        // migration on update, missing address books are created
        addressBookService.update(createFileData(addressBook101Bytes1, 10L, false, TransactionType.FILEUPDATE));
        AddressBook newAddressBook = addressBookService.getCurrent(); // latest missing address book is current
        assertThat(newAddressBook.getStartConsensusTimestamp()).isEqualTo(8L);
        assertAddressBook(newAddressBook, FINAL);

        assertEquals(6, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count()); // initial plus 4 files
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + (UPDATED.getNodeAddressCount() * 2L) +
                (FINAL.getNodeAddressCount() * 2L), addressBookEntryRepository.count());
    }

    @Test
    void getNodesEmptyNodeStake() {
        assertThat(addressBookService.getNodes())
                .hasSize(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT)
                .allMatch(c -> c.getStake() == 1L)
                .allMatch(c -> c.getTotalStake() == 4L)
                .allMatch(c -> c.getNodeAccountId().getEntityNum() - 3 == c.getNodeId())
                .allSatisfy(c -> assertThat(c.getPublicKey()).isNotNull())
                .extracting(ConsensusNode::getNodeId)
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @Test
    void getNodesWithNodeStake() {
        long timestamp = domainBuilder.timestamp();
        var nodeStake0 = domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(0)).persist();
        var nodeStake1 = domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(1)).persist();
        var nodeStake2 = domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(2)).persist();
        var nodeStake3 = domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(3)).persist();
        long totalStake = nodeStake0.getStake() + nodeStake1.getStake() + nodeStake2.getStake() + nodeStake3.getStake();

        assertThat(addressBookService.getNodes())
                .hasSize(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT)
                .allSatisfy(c -> assertThat(c).returns(totalStake, ConsensusNode::getTotalStake))
                .allSatisfy(c -> assertThat(c.getPublicKey()).isNotNull())
                .allMatch(c -> c.getStake() > 1L)
                .extracting(ConsensusNode::getNodeId)
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @Test
    void getNodesWithNodeStakeCountMoreThanAddressBook() {
        long timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(0).stake(0L)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(1).stake(0L)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(2).stake(0L)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(3).stake(0L)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(4).stake(0L)).persist();

        assertThat(addressBookService.getNodes())
                .hasSize(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT)
                .allMatch(c -> c.getStake() == 1L)
                .allMatch(c -> c.getTotalStake() == 5L)
                .allMatch(c -> c.getNodeAccountId().getEntityNum() - 3 == c.getNodeId())
                .allSatisfy(c -> assertThat(c.getPublicKey()).isNotNull());
    }

    @Test
    void refresh() {
        long timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(0)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(1)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(2)).persist();
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).nodeId(3)).persist();

        // Verify cache is empty to start
        assertNull(cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY));

        // Verify getCurrent() adds an entry to the cache
        var nodes = addressBookService.getNodes();
        var nodesCache = cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY).get();
        assertThat(nodes)
                .isNotNull()
                .isEqualTo(nodesCache)
                .allMatch(node -> node.getStake() > 1)
                .allMatch(node -> node.getTotalStake() > 1)
                .allMatch(node -> node.getNodeAccountId() != null);

        addressBookService.refresh();
        assertNull(cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY));
    }

    private ServiceEndpoint getServiceEndpoint(String ip, int port) throws UnknownHostException {
        return ServiceEndpoint.newBuilder()
                .setIpAddressV4(ByteString.copyFrom(InetAddress.getByName(ip).getAddress()))
                .setPort(port)
                .build();
    }

    private NodeAddress getNodeAddress(int accountNum, String deprecatedMemo, String deprecatedIp,
                                       List<String> serviceEndpoints) throws UnknownHostException {
        NodeAddress.Builder nodeAddressBuilder = NodeAddress.newBuilder()
                .setDescription("NodeAddressWithServiceEndpoint")
                .setNodeAccountId(AccountID.newBuilder().setAccountNum(accountNum).build())
                .setNodeCertHash(ByteString.copyFromUtf8(accountNum + "NodeCertHash"))
                .setNodeId(accountNum - AddressBookServiceImpl.INITIAL_NODE_ID_ACCOUNT_ID_OFFSET)
                .setRSAPubKey(accountNum + "RSAPubKey")
                .setStake(500);

        if (StringUtils.isNotBlank(deprecatedIp)) {
            nodeAddressBuilder
                    .setIpAddress(ByteString.copyFromUtf8(deprecatedIp))
                    .setPortno(basePort);
        }

        if (StringUtils.isNotBlank(deprecatedMemo)) {
            nodeAddressBuilder.setMemo(ByteString.copyFromUtf8(deprecatedMemo));
        }

        for (String endpoint : serviceEndpoints) {
            nodeAddressBuilder.addServiceEndpoint(getServiceEndpoint(endpoint, basePort));
        }

        return nodeAddressBuilder.build();
    }

    private void assertAddressBookData(byte[] expected, long consensusTimestamp) {
        AddressBook actualAddressBook = addressBookRepository.findById(consensusTimestamp).get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
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
                assertThat(abe.getNodeId()).isEqualTo(nodeAddress.getNodeId());

                assertAddressBookEndPoints(abe.getServiceEndpoints(), nodeAddress.getServiceEndpointList());
            });
        }
    }

    private void assertAddressBookEndPoints(Set<AddressBookServiceEndpoint> actual, List<ServiceEndpoint> expected) {
        if (expected.isEmpty()) {
            return;
        }

        var listAssert = assertThat(actual).hasSize(expected.size());

        for (ServiceEndpoint serviceEndpoint : expected) {
            listAssert.anySatisfy(abe -> {
                AtomicReference<String> ip = new AtomicReference<>("");
                assertDoesNotThrow(() -> {
                    ip.set(InetAddress.getByAddress(serviceEndpoint.getIpAddressV4().toByteArray()).getHostAddress());
                });

                assertThat(abe.getId().getPort()).isEqualTo(serviceEndpoint.getPort());
                assertThat(abe.getId().getIpAddressV4()).isEqualTo(ip.get());
            });
        }
    }
}
