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
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.NodeAddress;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.NodeAddressRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class NetworkAddressBook {

    private final MirrorProperties mirrorProperties;
    private final AddressBookRepository addressBookRepository;
    private final NodeAddressRepository nodeAddressRepository;

    private volatile Collection<NodeAddress> currentNodeAddresses;
    private volatile Collection<NodeAddress> incomingNodeAddresses;
    private volatile AddressBook currentAddressBook;
    private volatile AddressBook incomingAddressBook;
    private final Path addressBookPath;
    private Path tempAddressBookPath;

    public NetworkAddressBook(MirrorProperties mirrorProperties, AddressBookRepository addressBookRepository,
                              NodeAddressRepository nodeAddressRepository) {
        this.mirrorProperties = mirrorProperties;
        this.addressBookRepository = addressBookRepository;
        this.nodeAddressRepository = nodeAddressRepository;
        currentNodeAddresses = Collections.emptyList();
        incomingNodeAddresses = Collections.emptyList();
        currentAddressBook = null;
        incomingAddressBook = null;
        addressBookPath = mirrorProperties.getAddressBookPath();
        tempAddressBookPath = null;
        init();
    }

    public static boolean isAddressBook(EntityId entityId) {
        return entityId != null && entityId.getType() == EntityTypeEnum.FILE.getId()
                && (entityId.getEntityNum() == 101 || entityId.getEntityNum() == 102)
                && entityId.getShardNum() == 0 && entityId.getRealmNum() == 0;
    }

    public void updateFrom(TransactionBody transactionBody, long consensusTimeStamp, FileID fileID) {
        OpenOption openOption = null;
        AddressBook.FileOperation fileOperation = null;
        byte[] contents = null;

        try {
            if (transactionBody.hasFileAppend()) {
                openOption = StandardOpenOption.APPEND;
                fileOperation = AddressBook.FileOperation.APPEND;
                contents = transactionBody.getFileAppend().getContents().toByteArray();
            } else if (transactionBody.hasFileUpdate()) {
                openOption = StandardOpenOption.TRUNCATE_EXISTING;
                fileOperation = AddressBook.FileOperation.UPDATE;
                contents = transactionBody.getFileUpdate().getContents().toByteArray();
            } else if (transactionBody.hasFileCreate()) {
                openOption = StandardOpenOption.CREATE;
                fileOperation = AddressBook.FileOperation.CREATE;
                contents = transactionBody.getFileCreate().getContents().toByteArray();
            }

            if (contents == null || contents.length == 0) {
                log.warn("Byte array contents were empty. Skipping processing ...");
                return;
            }

            // file system storage can eventually be removed when in memory concatenation of bytes is supported
            saveToDisk(contents, openOption);

            try {
                parse(consensusTimeStamp, fileID, fileOperation);
            } catch (Exception e) {
                log.warn("Unable to parse address book: {}", e.getMessage());
            }

            persistAddressBookToDB(consensusTimeStamp);
        } catch (IOException e) {
            throw new ParserException("Error appending to network address book", e);
        }
    }

    public Collection<NodeAddress> getAddresses() {
        return currentNodeAddresses;
    }

    private void init() {
        loadAddressBookFromDB(Instant.now().getEpochSecond(), EntityId.of("0.0.102", EntityTypeEnum.FILE));
        if (currentAddressBook == null) {
            // no addressBook present in db, load from fileSystem

            try {
                File addressBookFile = addressBookPath.toFile();

                if (!addressBookFile.canRead()) {
                    if (addressBookFile.exists()) {
                        log.warn("Backing up unreadable address book: {}", addressBookPath);
                        Files.move(addressBookPath, addressBookPath.resolveSibling(addressBookPath + ".unreadable"));
                    }

                    byte[] addressBookBytes = null;
                    Path initialAddressBook = mirrorProperties.getInitialAddressBook();

                    if (initialAddressBook != null) {
                        addressBookBytes = Files.readAllBytes(initialAddressBook);
                        log.info("Loading bootstrap address book from {}", initialAddressBook);
                    } else {
                        MirrorProperties.HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
                        String resourcePath = String.format("/addressbook/%s", hederaNetwork.name().toLowerCase());
                        Resource resource = new ClassPathResource(resourcePath, getClass());
                        addressBookBytes = IOUtils.toByteArray(resource.getInputStream());
                        log.info("Loading bootstrap address book from {}", resource);
                    }

                    Utility.ensureDirectory(addressBookPath.getParent());
                    saveToDisk(addressBookBytes, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    log.info("Restoring existing address book {}", addressBookPath);
                    saveToDisk(Files.readAllBytes(addressBookPath), StandardOpenOption.TRUNCATE_EXISTING);
                }
            } catch (Exception e) {
                log.error("Unable to copy address book from {} to {}", mirrorProperties
                        .getNetwork(), addressBookPath, e);
            }

            try {
                parse(0L, FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build(),
                        AddressBook.FileOperation.CREATE);
                persistAddressBookToDB(0);
            } catch (Exception e) {
                log.error("Unable to parse address book: {}", e.getMessage());
            }
        } else {
            // addressBook loaded from db
            log.info("Loaded addressBook details from DB. Valid from {}, {} nodes, fileSize {} B",
                    currentAddressBook.getStartConsensusTimestamp(), currentAddressBook.getNodeCount(),
                    currentAddressBook.getFileData().length);
        }

        if (getAddresses().isEmpty()) {
            throw new IllegalStateException("Unable to load a valid address book");
        }
    }

    private void saveToDisk(byte[] contents, OpenOption openOption) throws IOException {
        if (contents == null || contents.length == 0) {
            log.warn("Ignored empty byte array");
            return;
        }

        tempAddressBookPath = addressBookPath.resolveSibling(addressBookPath.getFileName() + ".tmp");
        Files.write(tempAddressBookPath, contents, StandardOpenOption.CREATE, StandardOpenOption.WRITE, openOption);
        log.info("Saved {}B partial address book update to {}", contents.length, tempAddressBookPath);
    }

    private void parse(Long consensusTimestamp, FileID fileID, AddressBook.FileOperation fileOperation) throws Exception {
        // read bytes from temp file to ensure bytes represent concatenated bytes.
        // In memory solution should retrieve concatenation of bytes from latest incomplete address book bytes
        byte[] addressBookBytes = Files.readAllBytes(tempAddressBookPath);
        retrieveAddressBook(addressBookBytes, consensusTimestamp, fileID, fileOperation);

        if (!incomingNodeAddresses.isEmpty()) {
            // when fileSystem logic is removed this file move can be removed
            Files.move(tempAddressBookPath, addressBookPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("New address book with {} addresses successfully parsed and saved to {}",
                    incomingNodeAddresses.size(), addressBookPath);
        }
    }

    private void parse(byte[] contents, Long consensusTimestamp, FileID fileID,
                       AddressBook.FileOperation fileOperation) throws Exception {
        byte[] addressBookBytes = null;
        if (fileOperation == AddressBook.FileOperation.APPEND) {
            // retrieve last incomplete addressBook entry for this fileID
            Optional<AddressBook> optionAddressBook = addressBookRepository
                    .findTopByFileIdAndIsCompleteIsTrueOrderByConsensusTimestampDesc(EntityId.of(fileID));

            if (optionAddressBook.isPresent()) {
                AddressBook addressBook = optionAddressBook.get();
                byte[] incompleteBytes = addressBook.getFileData();
                byte[] combinedBytes = new byte[addressBookBytes.length + incompleteBytes.length];
                System.arraycopy(addressBookBytes, 0, combinedBytes, 0, addressBookBytes.length);
                System.arraycopy(incompleteBytes, 0, combinedBytes, addressBookBytes.length, incompleteBytes.length);
                addressBookBytes = combinedBytes;
                log.info("Combined incomplete addressBook from {} of size {} B with bytes from {} of {} B. Combined " +
                                "length is {}", addressBook.getConsensusTimestamp(), incompleteBytes.length,
                        consensusTimestamp, contents.length, addressBookBytes.length);
            } else {
                log.error("Previous incomplete address book entry expected but not found");
            }
        } else {
            addressBookBytes = contents;
        }

        retrieveAddressBook(addressBookBytes, consensusTimestamp, fileID, fileOperation);

        if (!incomingNodeAddresses.isEmpty()) {
            // when fileSystem logic is removed this file move can be removed
            Files.move(tempAddressBookPath, addressBookPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("New address book with {} addresses successfully parsed and saved to {}",
                    incomingNodeAddresses.size(), addressBookPath);
        }
    }

    private void retrieveAddressBook(byte[] addressBookBytes, long consensusTimestamp, FileID fileID,
                                     AddressBook.FileOperation fileOperation) {
        incomingNodeAddresses = Collections.emptyList();
        incomingAddressBook = null;
        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .fileData(addressBookBytes)
                .consensusTimestamp(consensusTimestamp)
                .fileId(EntityId.of(fileID))
                .operationType(fileOperation);

        try {
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
            if (nodeAddressBook != null) {
                builder = builder
                        .isComplete(true)
                        .nodeCount(nodeAddressBook.getNodeAddressCount())
                        .startConsensusTimestamp(consensusTimestamp);

                Collection<NodeAddress> nodeAddresses = retrieveNodeAddressesFromAddressBook(nodeAddressBook);
                if (nodeAddressBook.getNodeAddressCount() > 0) {
                    incomingNodeAddresses = nodeAddresses;
                }
            }
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
        }

        incomingAddressBook = builder.build();
    }

    private Collection<NodeAddress> retrieveNodeAddressesFromAddressBook(NodeAddressBook nodeAddressBook) {
        ImmutableList.Builder<NodeAddress> builder = ImmutableList.builder();

        if (nodeAddressBook != null) {
            for (com.hederahashgraph.api.proto.java.NodeAddress nodeAddressProto : nodeAddressBook
                    .getNodeAddressList()) {
                NodeAddress nodeAddress = NodeAddress.builder()
                        .memo(nodeAddressProto.getMemo().toStringUtf8())
                        .ip(nodeAddressProto.getIpAddress().toStringUtf8())
                        .port(nodeAddressProto.getPortno())
                        .publicKey(nodeAddressProto.getRSAPubKey())
                        .build();
                builder.add(nodeAddress);
            }
        }

        return builder.build();
    }

    private void persistAddressBookToDB(long consensusTimestamp) {
        // store complete address book
        saveAddressBook(consensusTimestamp);

        // store node addresses
        saveNodeAddresses(consensusTimestamp);

        incomingAddressBook = null;
    }

    private void saveAddressBook(long consensusTimestamp) {
        // if address book is complete update end time of previous addressbook and start time of this
        if (currentAddressBook != null && incomingAddressBook != null && incomingAddressBook.isComplete()) {
            // store current address book with complete rows, start and end
            // to:do check why enum type seems to not be of file type
            addressBookRepository
                    .updateEndConsensusTimestamp(currentAddressBook.getConsensusTimestamp(), consensusTimestamp);
        }

        // store address book.
        // Potential to also remove incomplete address books entries in the db table
        addressBookRepository.save(incomingAddressBook);
    }

    private void saveNodeAddresses(long consensusTimestamp) {
        if (!incomingNodeAddresses.isEmpty()) {
            for (NodeAddress nodeAddress : incomingNodeAddresses) {
                // set consensusTimestamp
                nodeAddress.setConsensusTimestamp(consensusTimestamp);
            }

            // store node addresses
            nodeAddressRepository.saveAll(incomingNodeAddresses);

            // update current and reset incoming
            currentNodeAddresses = new ArrayList(incomingNodeAddresses);
            incomingNodeAddresses = Collections.emptyList();

            // update currentAddressBook when there's a new valid set of nodeAddresses
            currentAddressBook = incomingAddressBook.toBuilder().build();
        }
    }

    private void loadAddressBookFromDB(long timeStamp, EntityId entityId) {
        // get last complete address book
        Optional<AddressBook> addressBook = addressBookRepository
                .findTopByFileIdAndIsCompleteIsTrueOrderByConsensusTimestampDesc(entityId);

        if (addressBook.isPresent()) {
            currentAddressBook = addressBook.get();
            currentNodeAddresses = nodeAddressRepository
                    .findNodeAddressesByConsensusTimestamp(timeStamp);
        }
    }
}
