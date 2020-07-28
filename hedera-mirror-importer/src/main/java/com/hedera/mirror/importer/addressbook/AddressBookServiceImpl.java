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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;

@Log4j2
@Named
public class AddressBookServiceImpl implements AddressBookService {
    public static final EntityId ADDRESS_BOOK_101_ENTITY_ID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
    public static final EntityId ADDRESS_BOOK_102_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);

    private final MirrorProperties mirrorProperties;
    private final AddressBookRepository addressBookRepository;
    private final FileDataRepository fileDataRepository;
    private Collection<AddressBookEntry> addressBookEntries;

    public AddressBookServiceImpl(MirrorProperties mirrorProperties, AddressBookRepository addressBookRepository,
                                  FileDataRepository fileDataRepository) {
        this.mirrorProperties = mirrorProperties;
        this.addressBookRepository = addressBookRepository;
        this.fileDataRepository = fileDataRepository;
        addressBookEntries = Collections.emptyList();
        init();
    }

    @Override
    public void update(FileData fileData) {
        if (fileData.getFileData() == null || fileData.getFileData().length == 0) {
            log.warn("Byte array contents were empty. Skipping processing ...");
            return;
        }

        try {
            parse(fileData);
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
        }
    }

    @Override
    public Collection<AddressBookEntry> getAddresses() {
        return addressBookEntries;
    }

    @Override
    public boolean isAddressBook(EntityId entityId) {
        return entityId != null && entityId.getType() == EntityTypeEnum.FILE.getId() &&
                (entityId.getEntityNum() == 101 || entityId.getEntityNum() == 102)
                && entityId.getShardNum() == 0 && entityId.getRealmNum() == 0;
    }

    private void init() {
        // load most recent addressBook
        loadAddressBookFromDB();

        if (CollectionUtils.isEmpty(addressBookEntries)) {
            // no addressBook present in db, load from classpath
            byte[] addressBookBytes = null;
            try {
                Path initialAddressBook = mirrorProperties.getInitialAddressBook();
                if (initialAddressBook != null) {
                    addressBookBytes = Files.readAllBytes(initialAddressBook);
                    log.info("Loading bootstrap address book of {} B from {}", addressBookBytes.length,
                            initialAddressBook);
                } else {
                    MirrorProperties.HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
                    String resourcePath = String.format("/addressbook/%s", hederaNetwork.name().toLowerCase());
                    Resource resource = new ClassPathResource(resourcePath, getClass());
                    addressBookBytes = IOUtils.toByteArray(resource.getInputStream());
                    log.info("Loading bootstrap address book of {} B from {}", addressBookBytes.length, resource);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to load valid address book from classpath");
            }

            try {
                FileData fileData = new FileData(0L, addressBookBytes, ADDRESS_BOOK_102_ENTITY_ID,
                        TransactionTypeEnum.FILECREATE.getProtoId());
                AddressBook addressBook = parse(fileData);
                addressBookEntries = addressBook.getAddressBookEntries();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to parse address book: ", e);
            }
        } else {
            log.info("Loaded addressBook w {} nodes from DB. ", addressBookEntries.size());
        }

        if (getAddresses().isEmpty()) {
            throw new IllegalStateException("Unable to load a valid address book with node addresses");
        }
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
    private AddressBook parse(FileData fileData) throws Exception {
        byte[] addressBookBytes = null;
        AddressBook addressBook = null;

        if (fileData.getTransactionType() == TransactionTypeEnum.FILEAPPEND.getProtoId()) {
            // concatenate bytes from partial address book file data in db
            if (fileData.getFileData() != null && fileData.getFileData().length > 0) {
                addressBookBytes = combinePreviousFileDataContents(fileData);
            } else {
                log.error("Previous incomplete address book entry expected but not found");
            }
        } else {
            addressBookBytes = fileData.getFileData();
        }

        // store fileData information
        fileDataRepository.save(fileData);

        addressBook = buildAddressBook(addressBookBytes, fileData.getConsensusTimestamp(), fileData
                .getEntityId());
        if (addressBook != null) {
            saveAddressBook(addressBook);
        }

        return addressBook;
    }

    private byte[] combinePreviousFileDataContents(FileData fileData) {
        Optional<FileData> optionalFileData = fileDataRepository.
                findTopByConsensusTimestampBeforeAndEntityIdAndTransactionTypeInOrderByConsensusTimestampDesc(fileData
                        .getConsensusTimestamp(), fileData.getEntityId(), List
                        .of(TransactionTypeEnum.FILECREATE.getProtoId(), TransactionTypeEnum.FILEUPDATE.getProtoId()));
        byte[] combinedBytes = null;
        if (optionalFileData.isPresent()) {
            FileData firstPartialAddressBook = optionalFileData.get();
            long consensusTimeStamp = firstPartialAddressBook.getConsensusTimestamp();
            List<FileData> appendFileDataEntries = fileDataRepository
                    .findByConsensusTimestampBetweenAndEntityIdAndTransactionTypeOrderByConsensusTimestampAsc(
                            consensusTimeStamp + 1, fileData.getConsensusTimestamp() - 1, firstPartialAddressBook
                                    .getEntityId(),
                            TransactionTypeEnum.FILEAPPEND.getProtoId());

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                bos.write(firstPartialAddressBook.getFileData());
                for (int i = 0; i < appendFileDataEntries.size(); i++) {
                    bos.write(appendFileDataEntries.get(i).getFileData());
                }

                bos.write(fileData.getFileData());
                combinedBytes = bos.toByteArray();
            } catch (Exception ex) {
                log.error("Error concatenating partial address book fileData entries", ex);
            }
        }

        return combinedBytes;
    }

    private AddressBook buildAddressBook(byte[] addressBookBytes, long consensusTimestamp, EntityId fileID) {
        AddressBook.AddressBookBuilder addressBookBuilder = AddressBook.builder()
                .fileData(addressBookBytes)
                .consensusTimestamp(consensusTimestamp)
                .fileId(fileID);

        try {
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
            if (nodeAddressBook != null) {

                if (nodeAddressBook.getNodeAddressCount() > 0) {
                    addressBookBuilder.nodeCount(nodeAddressBook.getNodeAddressCount());
                    Collection<AddressBookEntry> addressBookEntryCollection =
                            retrieveNodeAddressesFromAddressBook(nodeAddressBook, consensusTimestamp);

                    addressBookBuilder.addressBookEntries((List<AddressBookEntry>) addressBookEntryCollection);
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

    private void saveAddressBook(AddressBook addressBook) {
        addressBookRepository.save(addressBook);
        log.info("Saved new address book to db: {}", addressBook);
    }

    private void loadAddressBookFromDB() {
        // get last complete address book
        Optional<AddressBook> optionalAddressBook = addressBookRepository
                .findTopByConsensusTimestampBeforeAndFileIdOrderByConsensusTimestampDesc(Instant.now()
                        .getEpochSecond(), ADDRESS_BOOK_102_ENTITY_ID);

        if (optionalAddressBook.isPresent()) {
            AddressBook addressBook = optionalAddressBook.get();
            addressBookEntries = addressBook.getAddressBookEntries();
        }
    }
}
