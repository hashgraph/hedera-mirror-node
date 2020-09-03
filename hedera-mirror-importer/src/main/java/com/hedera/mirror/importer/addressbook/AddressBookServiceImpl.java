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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@AllArgsConstructor
public class AddressBookServiceImpl implements AddressBookService {

    public static final EntityId ADDRESS_BOOK_101_ENTITY_ID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
    public static final EntityId ADDRESS_BOOK_102_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);

    private static final List<FieldDescriptor> nodeAddressFieldDescriptors = NodeAddress.getDescriptor().getFields();

    private final AddressBookRepository addressBookRepository;
    private final FileDataRepository fileDataRepository;

    /**
     * Updates mirror node with new address book details provided in fileData object
     *
     * @param fileData file data entry containing address book bytes
     */
    @Override
    public void update(FileData fileData) {
        if (!isAddressBook(fileData.getEntityId())) {
            log.warn("Not an address book File ID. Skipping processing ...");
            return;
        }

        if (fileData.getFileData() == null || fileData.getFileData().length == 0) {
            log.warn("Byte array contents were empty. Skipping processing ...");
            return;
        }

        try {
            parse(fileData);
        } catch (Exception e) {
            log.error("Unable to parse address book", e);
        }
    }

    /**
     * Retrieves the latest address book from db
     *
     * @return returns AddressBook containing network node details
     */
    @Override
    public AddressBook getCurrent() {
        Instant now = Instant.now();
        long consensusTimestamp = Utility.convertToNanos(Instant.now().getEpochSecond(), now.getNano());

        return addressBookRepository
                .findLatestAddressBook(consensusTimestamp, ADDRESS_BOOK_102_ENTITY_ID.getId())
                .orElseThrow(() -> new IllegalStateException("No valid address book found in DB"));
    }

    /**
     * Checks if provided EntityId is a valid AddressBook file EntityId
     *
     * @param entityId file  entity id
     * @return returns true if valid address book EntityId
     */
    @Override
    public boolean isAddressBook(EntityId entityId) {
        return ADDRESS_BOOK_101_ENTITY_ID.equals(entityId) || ADDRESS_BOOK_102_ENTITY_ID.equals(entityId);
    }

    /**
     * Parses provided fileData object into an AddressBook object if valid and stores into db. Also updates previous
     * address book endConsensusTimestamp based on new address book's startConsensusTimestamp.
     *
     * @param fileData file data with timestamp, contents, entityId and transaction type for parsing
     */
    private void parse(FileData fileData) {
        byte[] addressBookBytes = null;
        if (fileData.transactionTypeIsAppend()) {
            // concatenate bytes from partial address book file data in db
            addressBookBytes = combinePreviousFileDataContents(fileData);
        } else {
            addressBookBytes = fileData.getFileData();
        }

        AddressBook addressBook = buildAddressBook(addressBookBytes, fileData.getConsensusTimestamp(), fileData
                .getEntityId());
        if (addressBook != null) {
            addressBook = addressBookRepository.save(addressBook);
            log.info("Saved new address book to db: {}", addressBook);

            // update previous addressBook
            updatePreviousAddressBook(addressBook.getStartConsensusTimestamp(), fileData.getConsensusTimestamp());
        }
    }

    /**
     * Concatenates byte arrays of first fileCreate/fileUpdate transaction and intermediate fileAppend entries that make
     * up the potential addressBook
     *
     * @param fileData file data entry containing address book bytes
     * @return
     */
    private byte[] combinePreviousFileDataContents(FileData fileData) {
        Optional<FileData> optionalFileData = fileDataRepository.findLatestMatchingFile(
                fileData.getConsensusTimestamp(),
                fileData.getEntityId().getId(),
                List.of(TransactionTypeEnum.FILECREATE.getProtoId(), TransactionTypeEnum.FILEUPDATE.getProtoId())
        );

        if (!optionalFileData.isPresent()) {
            throw new IllegalStateException("Missing FileData entry. FileAppend expects a corresponding " +
                    "FileCreate/FileUpdate entry");
        }

        FileData firstPartialAddressBook = optionalFileData.get();
        long consensusTimeStamp = firstPartialAddressBook.getConsensusTimestamp();

        List<FileData> appendFileDataEntries = fileDataRepository.findFilesInRange(
                consensusTimeStamp + 1,
                fileData.getConsensusTimestamp() - 1,
                firstPartialAddressBook.getEntityId().getId(),
                TransactionTypeEnum.FILEAPPEND.getProtoId()
        );

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(firstPartialAddressBook.getFileData().length)) {
            bos.write(firstPartialAddressBook.getFileData());
            for (int i = 0; i < appendFileDataEntries.size(); i++) {
                bos.write(appendFileDataEntries.get(i).getFileData());
            }

            bos.write(fileData.getFileData());
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error concatenating partial address book fileData entries", ex);
        }
    }

    /**
     * Creates a AddressBook object given address book byte array contents. Attempts to convert contents into a valid
     * NodeAddressBook proto. If successful the NodeAddressBook details including AddressBookEntry entries are extracted
     * and added to AddressBook object.
     *
     * @param addressBookBytes   byte array representation of AddressBookEntry proto
     * @param consensusTimestamp transaction consensusTimestamp
     * @param fileID             address book file id
     * @return
     */
    private AddressBook buildAddressBook(byte[] addressBookBytes, long consensusTimestamp, EntityId fileID) {
        long startConsensusTimestamp = consensusTimestamp + 1;
        AddressBook.AddressBookBuilder addressBookBuilder = AddressBook.builder()
                .fileData(addressBookBytes)
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileId(fileID);

        try {
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
            if (nodeAddressBook != null) {

                if (nodeAddressBook.getNodeAddressCount() > 0) {
                    addressBookBuilder.nodeCount(nodeAddressBook.getNodeAddressCount());
                    Collection<AddressBookEntry> addressBookEntryCollection =
                            retrieveNodeAddressesFromAddressBook(nodeAddressBook, startConsensusTimestamp);

                    addressBookBuilder.entries((List<AddressBookEntry>) addressBookEntryCollection);
                }
            }
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
            return null;
        }

        return addressBookBuilder.build();
    }

    /**
     * Extracts a collection of AddressBookEntry domain objects from NodeAddressBook proto. Sets provided
     * consensusTimestamp as the consensusTimestamp of each address book entry to ensure mapping to a single
     * AddressBook
     *
     * @param nodeAddressBook    node address book proto
     * @param consensusTimestamp transaction consensusTimestamp
     * @return
     */
    private Collection<AddressBookEntry> retrieveNodeAddressesFromAddressBook(NodeAddressBook nodeAddressBook,
                                                                              long consensusTimestamp) {
        ImmutableList.Builder<AddressBookEntry> listBuilder = ImmutableList.builder();

        for (NodeAddress nodeAddressProto : nodeAddressBook.getNodeAddressList()) {
            AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                    .consensusTimestamp(consensusTimestamp);

            nodeAddressFieldDescriptors.stream()
                    .filter(nodeAddressProto::hasField)
                    .map(FieldDescriptor::getNumber)
                    .forEach(fieldNumber -> {
                        switch (fieldNumber) {
                            case NodeAddress.IPADDRESS_FIELD_NUMBER:
                                builder.ip(nodeAddressProto.getIpAddress().toStringUtf8());
                                break;
                            case NodeAddress.PORTNO_FIELD_NUMBER:
                                builder.port(nodeAddressProto.getPortno());
                                break;
                            case NodeAddress.MEMO_FIELD_NUMBER:
                                builder.memo(nodeAddressProto.getMemo().toStringUtf8());
                                break;
                            case NodeAddress.RSA_PUBKEY_FIELD_NUMBER:
                                builder.publicKey(nodeAddressProto.getRSAPubKey());
                                break;
                            case NodeAddress.NODEID_FIELD_NUMBER:
                                builder.nodeId(nodeAddressProto.getNodeId());
                                break;
                            case NodeAddress.NODEACCOUNTID_FIELD_NUMBER:
                                builder.nodeAccountId(EntityId.of(nodeAddressProto.getNodeAccountId()));
                                break;
                            case NodeAddress.NODECERTHASH_FIELD_NUMBER:
                                builder.nodeCertHash(nodeAddressProto.getNodeCertHash().toByteArray());
                                break;
                            default:
                                log.warn("Unhandled field of NodeAddress protobuf - {}", fieldNumber);
                                break;
                        }
                    });

            listBuilder.add(builder.build());
        }

        return listBuilder.build();
    }

    /**
     * Address book updates currently span record files as well as a network shutdown. To account for this verify start
     * and end of addressBook are set after a record file is processed. If not set based on first and last transaction
     * in record file
     *
     * @param currentAddressBookStartConsensusTimestamp start of current address book
     * @param transactionConsensusTimestamp             consensusTimestamp of current transaction
     */
    private void updatePreviousAddressBook(long currentAddressBookStartConsensusTimestamp,
                                           long transactionConsensusTimestamp) {
        // close off previous addressBook
        Optional<AddressBook> previousOptionalAddressBook = addressBookRepository
                .findLatestAddressBook(currentAddressBookStartConsensusTimestamp - 1,
                        AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID
                                .getId());
        if (previousOptionalAddressBook.isPresent()) {
            AddressBook previousAddressBook = previousOptionalAddressBook.get();

            // set EndConsensusTimestamp of addressBook as first transaction - 1ns in record file if not set already
            if (previousAddressBook.getStartConsensusTimestamp() != currentAddressBookStartConsensusTimestamp &&
                    previousAddressBook.getEndConsensusTimestamp() == null) {
                previousAddressBook.setEndConsensusTimestamp(transactionConsensusTimestamp);
                addressBookRepository.save(previousAddressBook);
                log.info("Setting endConsensusTimestamp of previous AddressBook ({}) to {}",
                        previousAddressBook.getStartConsensusTimestamp(), transactionConsensusTimestamp);
            }
        } else {
            log.warn("No previous address book found before {}", currentAddressBookStartConsensusTimestamp);
        }
    }
}
