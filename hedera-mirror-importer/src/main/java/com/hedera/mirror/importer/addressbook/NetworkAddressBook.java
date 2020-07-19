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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.NodeAddressRepository;

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
        init();
    }

    public boolean isAddressBook(EntityId entityId) {
        return entityId != null && entityId.getType() == EntityTypeEnum.FILE.getId() &&
                (entityId.getEntityNum() == 101 || entityId.getEntityNum() == mirrorProperties
                        .getAddressBookFileIdEntityNum())
                && entityId.getShardNum() == 0 && entityId.getRealmNum() == 0;
    }

    public void updateFrom(long consensusTimeStamp, byte[] contents, EntityId fileID, boolean isAppendOperation) {
        if (contents == null || contents.length == 0) {
            log.warn("Byte array contents were empty. Skipping processing ...");
            return;
        }

        try {
            parse(contents, consensusTimeStamp, fileID, isAppendOperation);
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
        }

        persistAddressBookToDB(consensusTimeStamp);
    }

    public Collection<NodeAddress> getAddresses() {
        return currentNodeAddresses;
    }

    public AddressBook getCurrentAddressBook() {
        return currentAddressBook;
    }

    public AddressBook getPartialAddressBook() {
        return incomingAddressBook;
    }

    private boolean isSupportedAddressBookEntityNum(long entityNum) {
        return mirrorProperties.getAddressBookFileIdEntityNum() == entityNum;
    }

    public AddressBook getCurrentAddressBook() {
        return currentAddressBook;
    }

    public AddressBook getPartialAddressBook() {
        return incomingAddressBook;
    }

    private void init() {
        // load most recent addressBook
        loadAddressBookFromDB();

        if (currentAddressBook == null) {
            // no addressBook present in db, load from fileSystem
            byte[] addressBookBytes = null;
            try {
                File addressBookFile = addressBookPath.toFile();

                if (!addressBookFile.canRead()) {
                    if (addressBookFile.exists()) {
                        log.warn("Backing up unreadable address book: {}", addressBookPath);
                        Files.move(addressBookPath, addressBookPath.resolveSibling(addressBookPath + ".unreadable"));
                    }

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
                } else {
                    log.info("Restoring existing address book {}", addressBookPath);
                    addressBookBytes = Files.readAllBytes(addressBookPath);
                }
            } catch (Exception e) {
                throw new IllegalStateException(String
                        .format("Unable to load valid address book from %s", addressBookPath));
            }

            try {
                parse(addressBookBytes, 0L, EntityId.of(mirrorProperties.getShard(), 0, mirrorProperties
                        .getAddressBookFileIdEntityNum(), EntityTypeEnum.FILE), false);
                persistAddressBookToDB(0);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to parse address book: ", e);
            }
        } else {
            // addressBook loaded from db
            log.info("Loaded addressBook details from DB. Valid from {}, {} nodes, fileSize {} B",
                    currentAddressBook.getStartConsensusTimestamp(), currentAddressBook.getNodeCount(),
                    currentAddressBook.getFileData().length);
        }

        if (getAddresses().isEmpty()) {
            throw new IllegalStateException("Unable to load a valid address book with node addresses");
        }
    }

    private void parse(byte[] contents, Long consensusTimestamp, EntityId fileID, boolean append) throws Exception {
        byte[] addressBookBytes = null;
        if (append) {
            // concatenate bytes for impartial address books
            AddressBook addressBook = getPreviousAddressBookToAppendTo(fileID);

            if (addressBook != null) {
                byte[] incompleteBytes = addressBook.getFileData();
                byte[] combinedBytes = new byte[incompleteBytes.length + contents.length];
                System.arraycopy(incompleteBytes, 0, combinedBytes, 0, incompleteBytes.length);
                System.arraycopy(contents, 0, combinedBytes, incompleteBytes.length, contents.length);
                addressBookBytes = combinedBytes;
                log.info("Combined incomplete addressBook from {} of size {} B with bytes from {} of {} B. Combined " +
                                "length is {}", addressBook.getConsensusTimestamp(), incompleteBytes.length,
                        consensusTimestamp, contents.length, combinedBytes.length);
            } else {
                log.error("Previous incomplete address book entry expected but not found");
            }
        } else {
            addressBookBytes = contents;
        }

        retrieveAddressBook(addressBookBytes, consensusTimestamp, fileID);
    }

    private AddressBook getPreviousAddressBookToAppendTo(EntityId fileID) {
        // if incomingAddressBook is a match use it if not retrieve last address book for given file from Db
        if (incomingAddressBook != null && incomingAddressBook.getFileId().getEntityNum() == fileID.getEntityNum()) {
            return incomingAddressBook;
        }

        Optional<AddressBook> optionalAddressBook = addressBookRepository
                .findTopByFileIdOrderByConsensusTimestampDesc(fileID);

        return optionalAddressBook.isPresent() ? optionalAddressBook.get() : null;
    }

    private void retrieveAddressBook(byte[] addressBookBytes, long consensusTimestamp, EntityId fileID) {
        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .fileData(addressBookBytes)
                .consensusTimestamp(consensusTimestamp)
                .fileId(fileID);

        try {
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
            if (nodeAddressBook != null) {
                builder
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
            incomingNodeAddresses = Collections.emptyList();
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
    }

    private void saveAddressBook(long consensusTimestamp) {
        if (incomingAddressBook != null) {

            // if address book is complete update end time of previous address book and start time of this
            if (incomingAddressBook.isComplete()) {
                // retrieve last complete address book for fileID and update endConsensusTimestamp
                Optional<AddressBook> addressBook = addressBookRepository
                        .findTopByFileIdAndIsCompleteIsTrueOrderByConsensusTimestampDesc(incomingAddressBook
                                .getFileId());
                if (addressBook.isPresent()) {
                    addressBookRepository
                            .updateEndConsensusTimestamp(addressBook.get()
                                    .getConsensusTimestamp(), consensusTimestamp - 1);
                }
            }

            // store address book.
            // Potential to also remove incomplete address books entries in the db table
            addressBookRepository.save(incomingAddressBook);
            log.info("Saved new address book to db: {}", incomingAddressBook);
        }
    }

    private void saveNodeAddresses(long consensusTimestamp) {
        // update current node address for matching address file
        if (!incomingNodeAddresses.isEmpty()) {
            for (NodeAddress nodeAddress : incomingNodeAddresses) {
                // set consensusTimestamp
                nodeAddress.setConsensusTimestamp(consensusTimestamp);
            }

            // store node addresses
            nodeAddressRepository.saveAll(incomingNodeAddresses);
            log.info("Saved {} new node address to db: {}", incomingNodeAddresses.size());

            // update currentAddressBook and nodeAddresses for supported addressBook and matching fileID's only
            if (isSupportedAddressBookEntityNum(incomingAddressBook.getFileId().getEntityNum())) {
                if (currentAddressBook == null || currentAddressBook.getFileId().getId() == incomingAddressBook
                        .getFileId().getId()) {

                    // update current and reset incoming
                    log.info("Updating address book in use from {} to {}", currentAddressBook, incomingAddressBook);
                    currentNodeAddresses = new ArrayList(incomingNodeAddresses);
                    currentAddressBook = incomingAddressBook.toBuilder().build();
                }
            }
        }
    }

    public void loadAddressBookFromDB() {
        EntityId entityId = EntityId.of(mirrorProperties.getShard(), 0,
                mirrorProperties.getAddressBookFileIdEntityNum(), EntityTypeEnum.FILE);
        // get last complete address book
        Optional<AddressBook> addressBook = addressBookRepository
                .findTopByFileIdAndIsCompleteIsTrueOrderByConsensusTimestampDesc(entityId);

        if (addressBook.isPresent()) {
            currentAddressBook = addressBook.get();
            currentNodeAddresses = nodeAddressRepository
                    .findNodeAddressesByConsensusTimestamp(currentAddressBook.getConsensusTimestamp());
        }

        // load partial address book if present
        addressBook = addressBookRepository
                .findTopByFileIdAndIsCompleteIsFalseOrderByConsensusTimestampDesc(entityId);

        if (addressBook.isPresent()) {
            incomingAddressBook = addressBook.get();
        }
    }
}
