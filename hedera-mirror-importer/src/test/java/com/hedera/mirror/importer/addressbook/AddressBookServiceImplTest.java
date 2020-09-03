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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.ResetCacheTestExecutionListener;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;

@Log4j2
@SpringBootTest
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@TestExecutionListeners(value = {ResetCacheTestExecutionListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class AddressBookServiceImplTest {

    private static final NodeAddressBook UPDATED = addressBook(10);
    private static final NodeAddressBook FINAL = addressBook(15);
    private static final List<FieldDescriptor> allNodeAddressFieldDescriptors = NodeAddress.getDescriptor().getFields();

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
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());

        // assert repositories contain updates
        assertAddressBookData(UPDATED.toByteArray(), 1);
        assertEquals(1, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount(), addressBookEntryRepository.count());

        assertEquals(1, addressBookRepository.count());
        assertEquals(10, addressBookEntryRepository.count());
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
        assertThat(addressBookService.getCurrent().getEntries()).hasSize(UPDATED.getNodeAddressCount());
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
        assertThat(addressBook.getEntries()).hasSize(FINAL.getNodeAddressCount());
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
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());

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
        assertThat(newAddressBook.getEntries()).hasSize(FINAL.getNodeAddressCount());
        assertAddressBookData(newAddressBookBytes, 10);

        assertEquals(2, addressBookRepository.count());
        assertEquals(UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(), addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        AddressBook prevAddressBook = addressBookRepository.findById(1L).get();
        assertThat(prevAddressBook.getStartConsensusTimestamp()).isNotNull();
        assertThat(prevAddressBook.getEndConsensusTimestamp()).isNotNull();
    }

    @Test
    void verifyAddressBookWithEmptyAddressBookEntryFields() {
        NodeAddressBook nodeAddressBook = addressBookWithEmptyAddressBookEntryFields();
        byte[] nodeAddressBookBytes = nodeAddressBook.toByteArray();
        update(nodeAddressBookBytes, 1L, true);

        AddressBook current = addressBookService.getCurrent();

        assertThat(current.getEntries()).hasSize(nodeAddressBook.getNodeAddressCount());
        assertAddressBookData(nodeAddressBookBytes, 1L);

        Map<Long, NodeAddress> nodeAddressMap = nodeAddressBook.getNodeAddressList()
                .stream()
                .collect(Collectors.toMap(n -> {
                    if (n.getNodeId() != 0) {
                        return n.getNodeId();
                    } else {
                        return n.getNodeAccountId().getAccountNum();
                    }
                }, n -> n));
        for (AddressBookEntry actual : current.getEntries()) {
            NodeAddress expected = nodeAddressMap.get(actual.getNodeAccountId().getEntityNum());
            Set<Integer> setFieldNumbers = allNodeAddressFieldDescriptors.stream()
                    .filter(expected::hasField)
                    .map(FieldDescriptor::getNumber)
                    .collect(Collectors.toSet());
            // can't check nodeAccountId since AddressBookEntry tries to get nodeAccountId from both memo and
            // nodeAccountId fields
            String ip = setFieldNumbers.contains(NodeAddress.IPADDRESS_FIELD_NUMBER) ? expected.getIpAddress().toStringUtf8() : null;
            Integer portNo = setFieldNumbers.contains(NodeAddress.PORTNO_FIELD_NUMBER) ? expected.getPortno() : null;
            String memo = setFieldNumbers.contains(NodeAddress.MEMO_FIELD_NUMBER) ? expected.getMemo().toStringUtf8() : null;
            String pubKey = setFieldNumbers.contains(NodeAddress.RSA_PUBKEY_FIELD_NUMBER) ? expected.getRSAPubKey() : null;
            Long nodeId = setFieldNumbers.contains(NodeAddress.NODEID_FIELD_NUMBER) ? expected.getNodeId() : null;
            byte[] nodeCertHash = setFieldNumbers.contains(NodeAddress.NODECERTHASH_FIELD_NUMBER) ? expected.getNodeCertHash().toByteArray() : null;

            assertAll(() -> assertThat(actual.getIp()).isEqualTo(ip),
                    () -> assertThat(actual.getPort()).isEqualTo(portNo),
                    () -> assertThat(actual.getMemo()).isEqualTo(memo),
                    () -> assertThat(actual.getPublicKey()).isEqualTo(pubKey),
                    () -> assertThat(actual.getNodeId()).isEqualTo(nodeId),
                    () -> assertThat(actual.getNodeCertHash()).isEqualTo(nodeCertHash));
        }
    }

    private void assertAddressBookData(byte[] expected, long consensusTimestamp) {
        AddressBook actualAddressBook = addressBookRepository.findById(consensusTimestamp + 1).get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }

    private NodeAddressBook addressBookWithEmptyAddressBookEntryFields() {
        NodeAddressBook.Builder addressBookBuilder = NodeAddressBook.newBuilder();
        List<Integer> allFieldNumbers = allNodeAddressFieldDescriptors.stream()
                .map(FieldDescriptor::getNumber)
                .collect(Collectors.toList());

        int nodeId = 3;
        for (int fieldNumber : allFieldNumbers) {
            NodeAddress.Builder builder = NodeAddress.newBuilder()
                    .setIpAddress(ByteString.copyFromUtf8("127.0.0." + nodeId))
                    .setPortno(1000 + nodeId)
                    .setNodeId(nodeId)
                    .setMemo(ByteString.copyFromUtf8("0.0." + nodeId))
                    .setNodeAccountId(AccountID.newBuilder().setAccountNum(nodeId).build())
                    .setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash" + nodeId))
                    .setRSAPubKey("rsa+public/key" + nodeId);
            nodeId++;

            switch (fieldNumber) {
                case NodeAddress.IPADDRESS_FIELD_NUMBER:
                    builder.clearIpAddress();
                    break;
                case NodeAddress.PORTNO_FIELD_NUMBER:
                    builder.clearPortno();
                    break;
                case NodeAddress.MEMO_FIELD_NUMBER:
                    builder.clearMemo();
                    break;
                case NodeAddress.RSA_PUBKEY_FIELD_NUMBER:
                    builder.clearRSAPubKey();
                    break;
                case NodeAddress.NODEID_FIELD_NUMBER:
                    builder.clearNodeId();
                    break;
                case NodeAddress.NODEACCOUNTID_FIELD_NUMBER:
                    builder.clearNodeAccountId();
                    break;
                case NodeAddress.NODECERTHASH_FIELD_NUMBER:
                    builder.clearNodeCertHash();
                    break;
                default:
                    log.warn("Unhandled field of NodeAddress protobuf - {}", fieldNumber);
                    break;
            }

            addressBookBuilder.addNodeAddress(builder);
        }

        return addressBookBuilder.build();
    }
}
