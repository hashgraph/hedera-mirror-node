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
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
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

    private final AddressBookRepository addressBookRepository;
    private final FileDataRepository fileDataRepository;

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

    @Override
    public AddressBook getCurrent() {
        Instant now = Instant.now();
        long consensus_timestamp = Utility.convertToNanos(Instant.now().getEpochSecond(), now.getNano());
        Optional<AddressBook> optionalAddressBook = addressBookRepository
                .findLatestAddressBook(consensus_timestamp, ADDRESS_BOOK_102_ENTITY_ID.getId());

        if (optionalAddressBook.isPresent()) {
            AddressBook addressBook = optionalAddressBook.get();
            log.info("Loaded addressBook from {} with nodes ({}).", addressBook.getStartConsensusTimestamp(),
                    addressBook.getNodeSet());
            return addressBook;
        }

        log.warn("No addressBooks before {} were found.", consensus_timestamp);
        return null;
    }

    @Override
    public boolean isAddressBook(EntityId entityId) {
        return ADDRESS_BOOK_101_ENTITY_ID.equals(entityId) || ADDRESS_BOOK_102_ENTITY_ID.equals(entityId);
    }

    /**
     * find last fileData for given entityId where operation was create/update using consensusTimestamp find all
     * fileData since  that time for given entityId concatenate all binary data in order and attempt to parse if
     * successful save
     *
     * @param fileData file data with timestamp, contents, entity type and transactions type for parsing
     * @return Parsed addressbook object if valid. Null otherwise.
     * @throws Exception
     */
    private void parse(FileData fileData) {
        byte[] addressBookBytes = null;
        if (fileData.transactionTypeIsAppend()) {
            // concatenate bytes from partial address book file data in db
            addressBookBytes = combinePreviousFileDataContents(fileData);
        } else {
            addressBookBytes = fileData.getFileData();
        }

        // store fileData information
        fileData = fileDataRepository.save(fileData);

        AddressBook addressBook = buildAddressBook(addressBookBytes, fileData.getConsensusTimestamp(), fileData
                .getEntityId());
        if (addressBook != null) {
            addressBook = addressBookRepository.save(addressBook);
            log.info("Saved new address book to db: {}", addressBook);

            // update previous addressBook
            updateAddressBook(addressBook.getStartConsensusTimestamp(), fileData.getConsensusTimestamp());
        }
    }

    private byte[] combinePreviousFileDataContents(FileData fileData) {
        Optional<FileData> optionalFileData = fileDataRepository.findLatestMatchingFile(
                fileData.getConsensusTimestamp(), fileData.getEntityId().getId(),
                List.of(TransactionTypeEnum.FILECREATE.getProtoId(), TransactionTypeEnum.FILEUPDATE.getProtoId()));
        byte[] combinedBytes = null;
        if (optionalFileData.isPresent()) {
            FileData firstPartialAddressBook = optionalFileData.get();
            long consensusTimeStamp = firstPartialAddressBook.getConsensusTimestamp();
            List<FileData> appendFileDataEntries = fileDataRepository
                    .findFilesInRange(
                            consensusTimeStamp + 1, fileData.getConsensusTimestamp() - 1, firstPartialAddressBook
                                    .getEntityId().getId(),
                            TransactionTypeEnum.FILEAPPEND.getProtoId());

            ByteArrayOutputStream bos = new ByteArrayOutputStream(firstPartialAddressBook.getFileData().length);
            try {
                bos.write(firstPartialAddressBook.getFileData());
                for (int i = 0; i < appendFileDataEntries.size(); i++) {
                    bos.write(appendFileDataEntries.get(i).getFileData());
                }

                bos.write(fileData.getFileData());
                combinedBytes = bos.toByteArray();
            } catch (IOException ex) {
                throw new InvalidDatasetException("Error concatenating partial address book fileData entries", ex);
            }
        } else {
            throw new IllegalStateException("Missing FileData entry. FileAppend expects a corresponding " +
                    "FileCreate/FileUpdate entry");
        }

        return combinedBytes;
    }

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

    private Collection<AddressBookEntry> retrieveNodeAddressesFromAddressBook(NodeAddressBook nodeAddressBook,
                                                                              long consensusTimestamp) {
        ImmutableList.Builder<AddressBookEntry> builder = ImmutableList.builder();

        if (nodeAddressBook != null) {
            for (com.hederahashgraph.api.proto.java.NodeAddress nodeAddressProto : nodeAddressBook
                    .getNodeAddressList()) {
                AddressBookEntry addressBookEntry = AddressBookEntry.builder()
                        .consensusTimestamp(consensusTimestamp)
                        .memo(nodeAddressProto.getMemo().toStringUtf8())
                        .ip(nodeAddressProto.getIpAddress().toStringUtf8())
                        .port(nodeAddressProto.getPortno())
                        .publicKey(nodeAddressProto.getRSAPubKey())
                        .nodeCertHash(nodeAddressProto.getNodeCertHash().toByteArray())
                        .nodeId(nodeAddressProto.getNodeId())
                        .nodeAccountId(EntityId.of(nodeAddressProto.getNodeAccountId()))
                        .build();
                builder.add(addressBookEntry);
            }
        }

        return builder.build();
    }

    /**
     * Address book updates currently span record files as well as a network shutdown. To account for this verify start
     * and end of addressbook are set after a record file is processed. If not set based on first and last transaction
     * in record file
     *
     * @param currentAddressBookStartConsensusTimestamp
     */
    private void updateAddressBook(long currentAddressBookStartConsensusTimestamp, long transactionConsensusTimestamp) {
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
