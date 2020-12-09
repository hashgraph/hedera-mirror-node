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

import static com.hedera.mirror.importer.addressbook.AddressBookServiceImpl.ADDRESS_BOOK_102_CACHE_NAME;
import static com.hedera.mirror.importer.config.CacheConfiguration.EXPIRE_AFTER_5M;

import com.google.common.collect.ImmutableList;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.MirrorProperties;
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
@CacheConfig(cacheNames = ADDRESS_BOOK_102_CACHE_NAME, cacheManager = EXPIRE_AFTER_5M)
public class AddressBookServiceImpl implements AddressBookService {

    public static final EntityId ADDRESS_BOOK_101_ENTITY_ID = EntityId.of(0, 0, 101, EntityTypeEnum.FILE);
    public static final EntityId ADDRESS_BOOK_102_ENTITY_ID = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
    public static final String ADDRESS_BOOK_102_CACHE_NAME = "current_102_address_book";

    private final AddressBookRepository addressBookRepository;
    private final FileDataRepository fileDataRepository;
    private final MirrorProperties mirrorProperties;
    private final TransactionTemplate transactionTemplate;

    public AddressBookServiceImpl(AddressBookRepository addressBookRepository, FileDataRepository fileDataRepository,
                                  MirrorProperties mirrorProperties, TransactionTemplate transactionTemplate) {
        this.addressBookRepository = addressBookRepository;
        this.fileDataRepository = fileDataRepository;
        this.mirrorProperties = mirrorProperties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Updates mirror node with new address book details provided in fileData object
     *
     * @param fileData file data entry containing address book bytes
     */
    @Override
    @CacheEvict(allEntries = true)
    public void update(FileData fileData) {
        if (!isAddressBook(fileData.getEntityId())) {
            log.warn("Not an address book File ID. Skipping processing ...");
            return;
        }

        if (fileData.getFileData() == null || fileData.getFileData().length == 0) {
            log.warn("Byte array contents were empty. Skipping processing ...");
            return;
        }

        // ensure address_book table is populated with at least bootstrap addressBook prior to additions
        migrate();

        parse(fileData);
    }

    /**
     * Retrieves the latest address book from db
     *
     * @return returns AddressBook containing network node details
     */
    @Override
    @Cacheable
    public AddressBook getCurrent() {
        Instant now = Instant.now();
        long consensusTimestamp = Utility.convertToNanosMax(now.getEpochSecond(), now.getNano());

        // retrieve latest address book. If address_book is empty parse initial and historic address book files
        return addressBookRepository
                .findLatestAddressBook(consensusTimestamp, ADDRESS_BOOK_102_ENTITY_ID.getId())
                .orElseGet(() -> {
                    log.debug("No address book found in db");
                    return migrate();
                });
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

    @Override
    public synchronized AddressBook migrate() {
        if (addressBookRepository.count() > 0) {
            if (log.isTraceEnabled()) {
                log.trace("Valid address books exist in db, skipping migration");
            }
            return null;
        }

        return transactionTemplate.execute(status -> {
            log.debug("Searching for address book on file system");
            AddressBook initialAddressBook = parse(getInitialAddressBookFileData());
            log.info("Saved initial address book to db: {}", initialAddressBook);

            // Ensure all applicable addressBook file data entries are processed
            AddressBook latestAddressBook = parseHistoricAddressBooks();
            return latestAddressBook == null ? initialAddressBook : latestAddressBook;
        });

//        log.debug("Searching for address book on file system");
//        AddressBook initialAddressBook = parse(getInitialAddressBookFileData());
//        log.info("Saved initial address book to db: {}", initialAddressBook);
//
//        // Ensure all applicable addressBook file data entries are processed
//        AddressBook latestAddressBook = parseHistoricAddressBooks();
//        return latestAddressBook == null ? initialAddressBook : latestAddressBook;
    }

    /**
     * Parses provided fileData object into an AddressBook object if valid and stores into db. Also updates previous
     * address book endConsensusTimestamp based on new address book's startConsensusTimestamp.
     *
     * @param fileData file data with timestamp, contents, entityId and transaction type for parsing
     */
    private AddressBook parse(FileData fileData) {
        byte[] addressBookBytes = null;
        if (fileData.transactionTypeIsAppend()) {
            // concatenate bytes from partial address book file data in db
            addressBookBytes = combinePreviousFileDataContents(fileData);
        } else {
            addressBookBytes = fileData.getFileData();
        }

        AddressBook addressBook = buildAddressBook(new FileData(fileData
                .getConsensusTimestamp(), addressBookBytes, fileData.getEntityId(), fileData.getTransactionType()));
        if (addressBook != null) {
            addressBook = addressBookRepository.save(addressBook);
            log.info("Saved new address book to db: {}", addressBook);

            // update previous addressBook
            updatePreviousAddressBook(fileData);
        }

        return addressBook;
    }

    /**
     * Concatenates byte arrays of first fileCreate/fileUpdate transaction and intermediate fileAppend entries that make
     * up the potential addressBook
     *
     * @param fileData file data entry containing address book bytes
     * @return
     */
    private byte[] combinePreviousFileDataContents(FileData fileData) {
        FileData firstPartialAddressBook = fileDataRepository.findLatestMatchingFile(
                fileData.getConsensusTimestamp(),
                fileData.getEntityId().getId(),
                List.of(TransactionTypeEnum.FILECREATE.getProtoId(), TransactionTypeEnum.FILEUPDATE.getProtoId())
        ).orElseThrow(() -> new IllegalStateException(
                "Missing FileData entry. FileAppend expects a corresponding  FileCreate/FileUpdate entry"));

        List<FileData> appendFileDataEntries = fileDataRepository.findFilesInRange(
                firstPartialAddressBook.getConsensusTimestamp() + 1,
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
     * @param fileData fileData object representing address book data
     * @return
     */
    private AddressBook buildAddressBook(FileData fileData) {
        long startConsensusTimestamp = fileData.getConsensusTimestamp() + 1;
        AddressBook.AddressBookBuilder addressBookBuilder = AddressBook.builder()
                .fileData(fileData.getFileData())
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileId(fileData.getEntityId());

        try {
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(fileData.getFileData());

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
        ImmutableList.Builder<AddressBookEntry> builder = ImmutableList.builder();
        Set<Long> nodeIdSet = new HashSet<>();

        for (NodeAddress nodeAddressProto : nodeAddressBook.getNodeAddressList()) {
            int port = nodeAddressProto.getPortno();
            AddressBookEntry addressBookEntry = AddressBookEntry.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .memo(nodeAddressProto.getMemo().toStringUtf8())
                    .ip(nodeAddressProto.getIpAddress().toStringUtf8())
                    .port(port == 0 ? null : port)
                    .publicKey(nodeAddressProto.getRSAPubKey())
                    .nodeCertHash(nodeAddressProto.getNodeCertHash().toByteArray())
                    .nodeId(nodeAddressProto.getNodeId())
                    .nodeAccountId(EntityId.of(nodeAddressProto.getNodeAccountId()))
                    .build();
            builder.add(addressBookEntry);
            nodeIdSet.add(nodeAddressProto.getNodeId());
        }

        List<AddressBookEntry> addressBookEntryList = builder.build();
        if (nodeIdSet.size() == 1) {
            // clear the node ID when all of them are the same
            addressBookEntryList.forEach(addressBookEntry -> addressBookEntry.setNodeId(null));
        }

        return addressBookEntryList;
    }

    /**
     * Address book updates currently span record files as well as a network shutdown. To account for this verify start
     * and end of addressBook are set after a record file is processed. If not set based on first and last transaction
     * in record file
     *
     * @param fileData FileData of current transaction
     */
    private void updatePreviousAddressBook(FileData fileData) {
        long currentTimestamp = fileData.getConsensusTimestamp() + 1;
        addressBookRepository.findLatestAddressBook(fileData.getConsensusTimestamp(), fileData.getEntityId().getId())
                .ifPresent(previousAddressBook -> {
                    // set EndConsensusTimestamp of addressBook as first transaction - 1ns in record file if not set
                    if (previousAddressBook.getStartConsensusTimestamp() != currentTimestamp &&
                            previousAddressBook.getEndConsensusTimestamp() == null) {
                        previousAddressBook.setEndConsensusTimestamp(fileData.getConsensusTimestamp());
                        addressBookRepository.save(previousAddressBook);
                        log.info("Setting endConsensusTimestamp of previous AddressBook ({}) to {}",
                                previousAddressBook.getStartConsensusTimestamp(), fileData.getConsensusTimestamp());
                    }
                });
    }

    /**
     * Retrieve the initial address book file for the network from either the file system or class path
     *
     * @return Address book fileData object
     */
    private FileData getInitialAddressBookFileData() {
        byte[] addressBookBytes;

        // retrieve bootstrap address book from filesystem or classpath
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
            throw new IllegalStateException("Unable to load bootstrap address book", e);
        }

        return new FileData(0L, addressBookBytes,
                AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID,
                TransactionTypeEnum.FILECREATE.getProtoId());
    }

    /**
     * Batch parse all 101 and 102 addressBook fileData objects and update the address_book table
     */
    private AddressBook parseHistoricAddressBooks() {
        AtomicLong fileDataEntries = new AtomicLong(0);
        AtomicLong currentConsensusTimestamp = new AtomicLong(0);
        AtomicReference<AddressBook> lastAddressBook = new AtomicReference<>();

        // starting from consensusTimeStamp = 0 retrieve pages of fileData entries for historic address books
        int pageSize = 1000; //
        List<FileData> fileDataList = fileDataRepository
                .findAddressBooksAfter(currentConsensusTimestamp.get(), pageSize);
        while (!CollectionUtils.isEmpty(fileDataList)) {
            log.info("Retrieved {} file_data rows for address book processing", fileDataList.size());
            fileDataList.forEach(fileData -> {
                if (fileData.getFileData() != null && fileData.getFileData().length > 0) {
                    // call normal address book file transaction parsing flow to parse and ingest address book contents
                    lastAddressBook.set(parse(fileData));
                    fileDataEntries.incrementAndGet();
                }

                currentConsensusTimestamp.set(fileData.getConsensusTimestamp());
            });

            fileDataList = fileDataRepository.findAddressBooksAfter(currentConsensusTimestamp.get(), pageSize);
        }

        log.info("Processed {} historic address books", fileDataEntries.get());
        return lastAddressBook.get();
    }
}
