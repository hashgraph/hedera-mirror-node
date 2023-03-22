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
import static com.hedera.mirror.importer.config.CacheConfiguration.EXPIRE_AFTER_5M;

import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

@Log4j2
@Named
@CacheConfig(cacheNames = CACHE_NAME, cacheManager = EXPIRE_AFTER_5M)
@RequiredArgsConstructor
public class AddressBookServiceImpl implements AddressBookService {

    public static final String CACHE_NAME = "nodes";
    public static final EntityId FILE_101 = EntityId.of(0, 0, 101, EntityType.FILE);
    public static final EntityId FILE_102 = EntityId.of(0, 0, 102, EntityType.FILE);
    public static final int INITIAL_NODE_ID_ACCOUNT_ID_OFFSET = 3;

    private final AddressBookRepository addressBookRepository;
    private final FileDataRepository fileDataRepository;
    private final MirrorProperties mirrorProperties;
    private final NodeStakeRepository nodeStakeRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    @CacheEvict(allEntries = true)
    public void refresh() {
        log.info("Clearing node cache");
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

        log.info("Received an address book update: {}", fileData);

        // ensure address_book table is populated with latest addressBook prior to additions
        validateAndCompleteAddressBookList(fileData);

        parse(fileData);
    }

    @Override
    public AddressBook getCurrent() {
        long consensusTimestamp = DomainUtils.convertToNanosMax(Instant.now());

        // retrieve latest address book. If address_book is empty parse initial and historic address book files
        return addressBookRepository.findLatest(consensusTimestamp, FILE_102.getId())
                .orElseGet(this::migrate);
    }

    @Cacheable
    @Override
    public Collection<ConsensusNode> getNodes() {
        var addressBook = getCurrent();
        var totalStake = new AtomicLong(0L);
        var nodes = new TreeSet<ConsensusNode>();
        var nodeStakes = new HashMap<Long, NodeStake>();

        nodeStakeRepository.findLatest().forEach(nodeStake -> {
            totalStake.addAndGet(nodeStake.getStake());
            nodeStakes.put(nodeStake.getNodeId(), nodeStake);
        });

        // For partial mirror nodes, node stake will provide a more accurate count
        long nodeCount = !nodeStakes.isEmpty() ? nodeStakes.size() : addressBook.getNodeCount();

        addressBook.getEntries().forEach(e -> {
            var nodeStake = nodeStakes.get(e.getNodeId());
            nodes.add(new ConsensusNodeWrapper(e, nodeStake, nodeCount, totalStake.get()));
        });

        if (nodes.isEmpty()) {
            throw new InvalidDatasetException("Unable to find a valid address book");
        }

        return Collections.unmodifiableCollection(nodes);
    }

    /**
     * Checks if provided EntityId is a valid AddressBook file EntityId
     *
     * @param entityId file  entity id
     * @return returns true if valid address book EntityId
     */
    @Override
    public boolean isAddressBook(EntityId entityId) {
        return FILE_101.equals(entityId) || FILE_102.equals(entityId);
    }

    /**
     * Creates a AddressBook object given address book byte array contents. Attempts to convert contents into a valid
     * NodeAddressBook proto. If successful the NodeAddressBook details including AddressBookEntry entries are extracted
     * and added to AddressBook object.
     *
     * @param fileData fileData object representing address book data
     * @return
     */
    protected AddressBook buildAddressBook(FileData fileData) {
        long startConsensusTimestamp = getAddressBookStartConsensusTimestamp(fileData);
        var addressBookBuilder = AddressBook.builder()
                .fileData(fileData.getFileData())
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileId(fileData.getEntityId());

        try {
            var nodeAddressBook = NodeAddressBook.parseFrom(fileData.getFileData());

            if (nodeAddressBook != null && nodeAddressBook.getNodeAddressCount() > 0) {
                addressBookBuilder.nodeCount(nodeAddressBook.getNodeAddressCount());
                Collection<AddressBookEntry> addressBookEntryCollection =
                        retrieveNodeAddressesFromAddressBook(nodeAddressBook, startConsensusTimestamp);

                addressBookBuilder.entries(new ArrayList<>(addressBookEntryCollection));
            }
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
            return null;
        }

        return addressBookBuilder.build();
    }

    private long getAddressBookStartConsensusTimestamp(FileData fileData) {
        return fileData.getConsensusTimestamp() + 1;
    }

    /**
     * Migrates address book data by searching file_data table for applicable 101 and 102 files. These files are
     * converted to AddressBook objects and used to populate address_book and address_book_entry tables. Migrate flow
     * will populate initial addressBook where applicable and consider all file_data present
     *
     * @return Latest AddressBook from historical files
     */
    @Override
    public synchronized AddressBook migrate() {
        long consensusTimestamp = DomainUtils.convertToNanosMax(Instant.now());
        var currentAddressBook = addressBookRepository
                .findLatest(consensusTimestamp, FILE_102.getId())
                .orElse(null);

        if (currentAddressBook != null) {
            // verify no file_data 102 entries exists after current addressBook
            List<FileData> fileDataList = fileDataRepository
                    .findAddressBooksBetween(currentAddressBook.getStartConsensusTimestamp(), Long.MAX_VALUE, 1);
            if (CollectionUtils.isEmpty(fileDataList)) {
                log.trace("All valid address books exist in db, skipping migration");
                return currentAddressBook;
            }

            log.warn("Valid address book file data entries exist in db after current address book");
        }

        log.info("Empty or incomplete list of address books found in db, proceeding with migration");
        return transactionTemplate.execute(status -> {
            log.info("Searching for address book on file system");
            var initialAddressBook = currentAddressBook == null ? parse(getInitialAddressBookFileData()) :
                    currentAddressBook;

            // Parse all applicable addressBook file_data entries are processed
            AddressBook latestAddressBook = parseHistoricAddressBooks(initialAddressBook
                    .getStartConsensusTimestamp() - 1, consensusTimestamp);

            // set latestAddressBook as newest addressBook from file_data entries or initial addressBook from filesystem
            latestAddressBook = latestAddressBook == null ? initialAddressBook : latestAddressBook;

            log.info("Migration complete. Current address book to db: {}", latestAddressBook);
            return latestAddressBook;
        });
    }

    /**
     * Ensure all addressBook file_data entries prior to this fileData and after the current address book have been
     * parsed. If not parse them and populate the address_book tables to complete the list. This does not handle initial
     * startup and only ensure any unprocessed address book files are processed.
     *
     * @param fileData
     */
    private void validateAndCompleteAddressBookList(FileData fileData) {
        AddressBook currentAddressBook = getCurrent();
        long startConsensusTimestamp = currentAddressBook == null ? 0 : currentAddressBook.getStartConsensusTimestamp();

        transactionTemplate.executeWithoutResult(status ->
                // Parse all applicable missed addressBook file_data entries in range
                parseHistoricAddressBooks(
                        startConsensusTimestamp,
                        fileData.getConsensusTimestamp())
        );
    }

    /**
     * Parses provided fileData object into an AddressBook object if valid and stores into db. Also updates previous
     * address book endConsensusTimestamp based on new address book's startConsensusTimestamp.
     *
     * @param fileData file data with timestamp, contents, entityId and transaction type for parsing
     * @return Parsed AddressBook from fileData object
     */
    private AddressBook parse(FileData fileData) {
        if (addressBookRepository.existsById(getAddressBookStartConsensusTimestamp(fileData))) {
            // skip as address book already exists
            log.info("Address book from fileData {} already exists, skip parsing", fileData);
            return null;
        }

        byte[] addressBookBytes = null;
        if (fileData.transactionTypeIsAppend()) {
            // concatenate bytes from partial address book file data in db
            addressBookBytes = combinePreviousFileDataContents(fileData);
        } else {
            addressBookBytes = fileData.getFileData();
        }

        var addressBook = buildAddressBook(new FileData(fileData
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
                List.of(TransactionType.FILECREATE.getProtoId(), TransactionType.FILEUPDATE.getProtoId())
        ).orElseThrow(() -> new IllegalStateException(
                "Missing FileData entry. FileAppend expects a corresponding  FileCreate/FileUpdate entry"));

        List<FileData> appendFileDataEntries = fileDataRepository.findFilesInRange(
                getAddressBookStartConsensusTimestamp(firstPartialAddressBook),
                fileData.getConsensusTimestamp() - 1,
                firstPartialAddressBook.getEntityId().getId(),
                TransactionType.FILEAPPEND.getProtoId()
        );

        try (var bos = new ByteArrayOutputStream(firstPartialAddressBook.getFileData().length)) {
            bos.write(firstPartialAddressBook.getFileData());
            for (var i = 0; i < appendFileDataEntries.size(); i++) {
                bos.write(appendFileDataEntries.get(i).getFileData());
            }

            bos.write(fileData.getFileData());
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error concatenating partial address book fileData entries", ex);
        }
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
                                                                              long consensusTimestamp) throws UnknownHostException {
        Map<Long, AddressBookEntry> addressBookEntries = new LinkedHashMap<>(); // node id to entry

        for (NodeAddress nodeAddressProto : nodeAddressBook.getNodeAddressList()) {
            Pair<Long, EntityId> nodeIds = getNodeIds(nodeAddressProto);
            AddressBookEntry addressBookEntry = addressBookEntries.computeIfAbsent(
                    nodeIds.getLeft(),
                    k -> getAddressBookEntry(nodeAddressProto, consensusTimestamp, nodeIds));

            Set<AddressBookServiceEndpoint> updatedList = new HashSet<>(addressBookEntry.getServiceEndpoints());
            updatedList.addAll(getAddressBookServiceEndpoints(nodeAddressProto, consensusTimestamp));
            addressBookEntry.setServiceEndpoints(updatedList);
        }

        return addressBookEntries.values();
    }

    /**
     * Get Node id and account id
     *
     * @param nodeAddressProto
     * @return Pair of nodeId and nodeAccountId
     */
    @SuppressWarnings({"deprecation", "java:S1874"})
    private Pair<Long, EntityId> getNodeIds(NodeAddress nodeAddressProto) {
        var memo = nodeAddressProto.getMemo().toStringUtf8();
        EntityId memoNodeAccountId = StringUtils.isEmpty(memo) ? EntityId.EMPTY : EntityId
                .of(memo, EntityType.ACCOUNT);
        var nodeAccountId = nodeAddressProto.hasNodeAccountId() ? EntityId
                .of(nodeAddressProto.getNodeAccountId()) : memoNodeAccountId;

        var nodeId = nodeAddressProto.getNodeId();
        // ensure valid nodeId. In early versions of initial addressBook (entityNum < 20) all nodeIds are set to 0
        if (nodeId == 0 && nodeAccountId.getEntityNum() < 20 &&
                nodeAccountId.getEntityNum() != INITIAL_NODE_ID_ACCOUNT_ID_OFFSET) {
            nodeId = nodeAccountId.getEntityNum() - INITIAL_NODE_ID_ACCOUNT_ID_OFFSET;
        }

        return Pair.of(nodeId, nodeAccountId);
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    private AddressBookEntry getAddressBookEntry(NodeAddress nodeAddressProto, long consensusTimestamp,
                                                 Pair<Long, EntityId> nodeIds) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .description(nodeAddressProto.getDescription())
                .nodeAccountId(nodeIds.getRight())
                .nodeId(nodeIds.getLeft())
                .publicKey(nodeAddressProto.getRSAPubKey())
                .serviceEndpoints(Set.of())
                .stake(nodeAddressProto.getStake());

        if (nodeAddressProto.getNodeCertHash() != null) {
            builder.nodeCertHash(nodeAddressProto.getNodeCertHash().toByteArray());
        }

        if (nodeAddressProto.getMemo() != null) {
            builder.memo(nodeAddressProto.getMemo().toStringUtf8());
        }

        return builder.build();
    }

    private Set<AddressBookServiceEndpoint> getAddressBookServiceEndpoints(NodeAddress nodeAddressProto,
                                                                           long consensusTimestamp) throws UnknownHostException {
        var nodeId = nodeAddressProto.getNodeId();
        Set<AddressBookServiceEndpoint> serviceEndpoints = new HashSet<>();

        // create an AddressBookServiceEndpoint for deprecated port and IP if populated
        AddressBookServiceEndpoint deprecatedServiceEndpoint = getAddressBookServiceEndpoint(
                nodeAddressProto,
                consensusTimestamp,
                nodeId);
        if (deprecatedServiceEndpoint != null) {
            serviceEndpoints.add(deprecatedServiceEndpoint);
        }

        // create an AddressBookServiceEndpoint for every ServiceEndpoint found
        for (ServiceEndpoint serviceEndpoint : nodeAddressProto.getServiceEndpointList()) {
            serviceEndpoints.add(getAddressBookServiceEndpoint(serviceEndpoint, consensusTimestamp, nodeId));
        }

        return serviceEndpoints;
    }

    @SuppressWarnings("deprecation")
    private AddressBookServiceEndpoint getAddressBookServiceEndpoint(NodeAddress nodeAddressProto,
                                                                     long consensusTimestamp,
                                                                     long nodeId) {
        String ip = nodeAddressProto.getIpAddress().toStringUtf8();
        if (StringUtils.isBlank(ip)) {
            return null;
        }

        AddressBookServiceEndpoint addressBookServiceEndpoint = new AddressBookServiceEndpoint();
        addressBookServiceEndpoint.setConsensusTimestamp(consensusTimestamp);
        addressBookServiceEndpoint.setIpAddressV4(ip);
        addressBookServiceEndpoint.setPort(nodeAddressProto.getPortno());
        addressBookServiceEndpoint.setNodeId(nodeId);
        return addressBookServiceEndpoint;
    }

    private AddressBookServiceEndpoint getAddressBookServiceEndpoint(ServiceEndpoint serviceEndpoint,
                                                                     long consensusTimestamp,
                                                                     long nodeId) throws UnknownHostException {
        var ipAddressByteString = serviceEndpoint.getIpAddressV4();
        if (ipAddressByteString == null || ipAddressByteString.size() != 4) {
            throw new IllegalStateException(String
                    .format("Invalid IpAddressV4: %s", ipAddressByteString));
        }

        var ip = InetAddress.getByAddress(ipAddressByteString.toByteArray()).getHostAddress();
        AddressBookServiceEndpoint addressBookServiceEndpoint = new AddressBookServiceEndpoint();
        addressBookServiceEndpoint.setConsensusTimestamp(consensusTimestamp);
        addressBookServiceEndpoint.setIpAddressV4(ip);
        addressBookServiceEndpoint.setPort(serviceEndpoint.getPort());
        addressBookServiceEndpoint.setNodeId(nodeId);
        return addressBookServiceEndpoint;
    }

    /**
     * Address book updates currently span record files as well as a network shutdown. To account for this verify start
     * and end of addressBook are set after a record file is processed. If not set based on first and last transaction
     * in record file
     *
     * @param fileData FileData of current transaction
     */
    private void updatePreviousAddressBook(FileData fileData) {
        long currentTimestamp = getAddressBookStartConsensusTimestamp(fileData);
        addressBookRepository.findLatest(fileData.getConsensusTimestamp(), fileData.getEntityId().getId())
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
                log.info("Loading bootstrap address book from {}", initialAddressBook.toString());
                addressBookBytes = Files.readAllBytes(initialAddressBook);
            } else {
                var hederaNetwork = mirrorProperties.getNetwork();
                var resourcePath = String.format("/addressbook/%s", hederaNetwork.name().toLowerCase());

                log.info("Loading bootstrap address book from {}", resourcePath);
                Resource resource = new ClassPathResource(resourcePath, getClass());
                addressBookBytes = resource.getInputStream().readAllBytes();
            }

            log.info("Loaded bootstrap address book of {} B", addressBookBytes.length);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load bootstrap address book", e);
        }

        return new FileData(0L, addressBookBytes,
                AddressBookServiceImpl.FILE_102,
                TransactionType.FILECREATE.getProtoId());
    }

    /**
     * Batch parse all 101 and 102 addressBook fileData objects and update the address_book table. Uses page size and
     * timestamp counters to batch query file data entries within timestamp range
     */
    private AddressBook parseHistoricAddressBooks(long startTimestamp, long endTimestamp) {
        var fileDataEntries = 0;
        long currentConsensusTimestamp = startTimestamp;
        AddressBook lastAddressBook = null;

        // retrieve pages of fileData entries for historic address books within range
        var pageSize = 1000;
        List<FileData> fileDataList = fileDataRepository
                .findAddressBooksBetween(currentConsensusTimestamp, endTimestamp, pageSize);
        while (!CollectionUtils.isEmpty(fileDataList)) {
            log.info("Retrieved {} file_data rows for address book processing", fileDataList.size());

            for (FileData fileData : fileDataList) {
                if (fileData.getFileData() != null && fileData.getFileData().length > 0) {
                    // convert and ingest address book fileData contents
                    lastAddressBook = parse(fileData);
                    fileDataEntries++;
                }

                // update timestamp counter to ensure next query doesn't reconsider files in this time range
                currentConsensusTimestamp = fileData.getConsensusTimestamp();
            }

            fileDataList = fileDataRepository
                    .findAddressBooksBetween(currentConsensusTimestamp, endTimestamp, pageSize);
        }

        log.info("Processed {} historic address books", fileDataEntries);
        return lastAddressBook;
    }
}
