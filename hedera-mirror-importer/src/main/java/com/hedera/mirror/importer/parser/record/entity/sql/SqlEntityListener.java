package com.hedera.mirror.importer.parser.record.entity.sql;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.DuplicateFileException;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@ConditionOnEntityRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

    private final DataSource dataSource;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;
    private final SqlProperties sqlProperties;
    private final RecordParserProperties parserProperties;

    // init connections, schemas, writers, etc once per process
    private final PgCopy<Transaction> transactionPgCopy;
    private final PgCopy<CryptoTransfer> cryptoTransferPgCopy;
    private final PgCopy<NonFeeTransfer> nonFeeTransferPgCopy;
    private final PgCopy<FileData> fileDataPgCopy;
    private final PgCopy<ContractResult> contractResultPgCopy;
    private final PgCopy<LiveHash> liveHashPgCopy;
    private final PgCopy<TopicMessage> topicMessagePgCopy;

    private final Collection<Transaction> transactions;
    private final Collection<CryptoTransfer> cryptoTransfers;
    private final Collection<NonFeeTransfer> nonFeeTransfers;
    private final Collection<FileData> fileData;
    private final Collection<ContractResult> contractResults;
    private final Collection<LiveHash> liveHashes;
    private final Collection<TopicMessage> topicMessages;
    private final Collection<EntityId> entityIds;

    // used to optimize inserts into t_entities table so node and treasury ids are not tried for every transaction
    private final Cache entityCache;

    private PreparedStatement sqlInsertEntityId;
    private PreparedStatement sqlNotifyTopicMessage;
    private Connection connection;
    private long batchCount;
    private RecordFile partialRecordFile;

    public SqlEntityListener(SqlProperties properties, DataSource dataSource,
                             RecordFileRepository recordFileRepository, MeterRegistry meterRegistry,
                             @Qualifier(CacheConfiguration.NEVER_EXPIRE_LARGE) CacheManager cacheManager,
                             ApplicationStatusRepository applicationStatusRepository,
                             TransactionRepository transactionRepository,
                             RecordParserProperties parserProps) {
        this.dataSource = dataSource;
        this.applicationStatusRepository = applicationStatusRepository;
        this.recordFileRepository = recordFileRepository;
        this.transactionRepository = transactionRepository;
        sqlProperties = properties;
        parserProperties = parserProps;
        entityCache = cacheManager.getCache(CacheConfiguration.NEVER_EXPIRE_LARGE);

        transactionPgCopy = new PgCopy<>(Transaction.class, meterRegistry, properties);
        cryptoTransferPgCopy = new PgCopy<>(CryptoTransfer.class, meterRegistry, properties);
        nonFeeTransferPgCopy = new PgCopy<>(NonFeeTransfer.class, meterRegistry, properties);
        fileDataPgCopy = new PgCopy<>(FileData.class, meterRegistry, properties);
        contractResultPgCopy = new PgCopy<>(ContractResult.class, meterRegistry, properties);
        liveHashPgCopy = new PgCopy<>(LiveHash.class, meterRegistry, properties);
        topicMessagePgCopy = new PgCopy<>(TopicMessage.class, meterRegistry, properties);

        transactions = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        fileData = new ArrayList<>();
        contractResults = new ArrayList<>();
        liveHashes = new ArrayList<>();
        entityIds = new HashSet<>();
        topicMessages = new ArrayList<>();
        partialRecordFile = null;
    }

    @Override
    public void onStart(StreamFileData streamFileData) {
        String fileName = FilenameUtils.getName(streamFileData.getFilename());

        List<RecordFile> matchingRecords = recordFileRepository.findByName(fileName);
        partialRecordFile = null;
        if (matchingRecords.size() > 0) {
            partialRecordFile = matchingRecords.get(0); // cache to avoid jpa exception
            handleDuplicateOrPartiallyProcessedRecordFile(partialRecordFile);
        }

        try {
            cleanup();
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            connection.setClientInfo("ApplicationName", getClass().getSimpleName());

            sqlInsertEntityId = connection.prepareStatement("INSERT INTO t_entities " +
                    "(id, entity_shard, entity_realm, entity_num, fk_entity_type_id) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT DO NOTHING");

            sqlNotifyTopicMessage = connection.prepareStatement("select pg_notify('topic_message', ?)");
        } catch (SQLException e) {
            throw new ParserSQLException("Error setting up connection to database", e);
        }
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        executeBatches();
        try {
            recordFileRepository.save(partialRecordFile == null ? recordFile : partialRecordFile);
            applicationStatusRepository.updateStatusValue(
                    ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, recordFile.getFileHash());

            connection.commit();
            closeConnectionAndStatements();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onError() {
        try {
            if (connection != null) {
                connection.rollback();
                closeConnectionAndStatements();
            }
        } catch (SQLException e) {
            log.error("Exception while rolling transaction back", e);
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        batchCount = 0;
        contractResults.clear();
        cryptoTransfers.clear();
        entityIds.clear();
        fileData.clear();
        liveHashes.clear();
        nonFeeTransfers.clear();
        topicMessages.clear();
        transactions.clear();
    }

    private void executeBatch(PreparedStatement ps, String entity) {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            var executeResult = ps.executeBatch();
            log.info("Inserted {} {} in {}", executeResult.length, entity, stopwatch);
        } catch (SQLException e) {
            throw new ParserException(e);
        }
    }

    private void closeConnectionAndStatements() {
        try {
            if (connection != null) {
                sqlInsertEntityId.close();
                sqlNotifyTopicMessage.close();
                connection.close();
            }
        } catch (SQLException e) {
            log.error("Error closing connection", e);
        }
    }

    private void executeBatches() {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            transactionPgCopy.copy(transactions, connection);
            cryptoTransferPgCopy.copy(cryptoTransfers, connection);
            nonFeeTransferPgCopy.copy(nonFeeTransfers, connection);
            fileDataPgCopy.copy(fileData, connection);
            contractResultPgCopy.copy(contractResults, connection);
            liveHashPgCopy.copy(liveHashes, connection);
            topicMessagePgCopy.copy(topicMessages, connection);
            persistEntities();
            notifyTopicMessages();
            log.info("Completed batch inserts in {}", stopwatch);
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        } finally {
            cleanup();
        }
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        transactions.add(transaction);
        if (batchCount == sqlProperties.getBatchSize() - 1) {
            // execute any remaining batches
            executeBatches();
        } else {
            batchCount += 1;
        }
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        // add entities not found in cache to list of entities to be persisted
        if (entityCache.get(entityId.getId()) != null) {
            return;
        }

        try {
            sqlInsertEntityId.setLong(F_ENTITY_ID.ID.ordinal(), entityId.getId());
            sqlInsertEntityId.setLong(F_ENTITY_ID.ENTITY_SHARD.ordinal(), entityId.getShardNum());
            sqlInsertEntityId.setLong(F_ENTITY_ID.ENTITY_REALM.ordinal(), entityId.getRealmNum());
            sqlInsertEntityId.setLong(F_ENTITY_ID.ENTITY_NUM.ordinal(), entityId.getEntityNum());
            sqlInsertEntityId.setLong(F_ENTITY_ID.TYPE.ordinal(), entityId.getType());
            sqlInsertEntityId.addBatch();
            entityIds.add(entityId);
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        cryptoTransfers.add(cryptoTransfer);
    }

    @Override
    public void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
        nonFeeTransfers.add(nonFeeTransfer);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        topicMessages.add(topicMessage);

        try {
            if (sqlProperties.isNotifyTopicMessage()) {
                String json = OBJECT_MAPPER.writeValueAsString(topicMessage);
                if (json.length() >= sqlProperties.getMaxJsonPayloadSize()) {
                    log.warn("Unable to notify large payload of size {}B: {}", json.length(), topicMessage);
                    return;
                }
                sqlNotifyTopicMessage.setString(1, json);
                sqlNotifyTopicMessage.addBatch();
            }
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        contractResults.add(contractResult);
    }

    @Override
    public void onFileData(FileData fd) {
        fileData.add(fd);
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        liveHashes.add(liveHash);
    }

    private void persistEntities() {
        executeBatch(sqlInsertEntityId, "entities");
        entityIds.forEach(entityId -> entityCache.put(entityId.getId(), null));
    }

    private void notifyTopicMessages() {
        executeBatch(sqlNotifyTopicMessage, "topic notifications");
    }

    /**
     * Handle purely duplicate file in addition to faulty transaction management between spring repositories and manual
     * java sql operations. Check whether the given file was partially processed and matches the last record file
     * processed where the record_file table was updated but application_status table LAST_PROCESSED_RECORD_HASH was not
     * updated. If so update application_status table LAST_PROCESSED_RECORD_HASH appropriately with correct hash. Also
     * handle case where transaction data was rolled bach but not record_file entry
     *
     * @param recordFile record file object that was partially processed file
     */
    private void handleDuplicateOrPartiallyProcessedRecordFile(RecordFile recordFile) {
        // handle faulty transaction management between spring repositories and manual java sql operations
        DuplicateFileException duplicateFileException = new DuplicateFileException("File already exists in the " +
                "database: " + recordFile.getName());
        RecordFile latestRecordFile = recordFileRepository.findTopByOrderByConsensusEndDesc().get();
        String lastRecordFileHash =
                applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH);

        // check if latest record file is a match processed file by comparing name and hash
        if (latestRecordFile.getName().equalsIgnoreCase(recordFile.getName()) &&
                !Utility.verifyHashChain(
                        latestRecordFile.getFileHash(),
                        lastRecordFileHash,
                        parserProperties.getMirrorProperties().getVerifyHashAfter(),
                        recordFile.getName())) {

            // check if this file is next in line of hash chain
            // Assumes application status updates after record_file update which is current process
            if (Utility.verifyHashChain(
                    latestRecordFile.getPreviousHash(),
                    lastRecordFileHash,
                    parserProperties.getMirrorProperties().getVerifyHashAfter(),
                    recordFile.getName())) {
                // application_status outdated
                log.debug("Duplicate file failed to update applicationStatus on previous processing, updating now!");
                applicationStatusRepository.updateStatusValue(
                        ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, latestRecordFile.getFileHash());
                throw duplicateFileException;
            } else {
                // very unlikely edge case
                log.warn("Latest file already exists but has mismatched hash for expectedPrevFileHash: ", recordFile
                        .getName());
                throw new HashMismatchException(recordFile.getName(), lastRecordFileHash, latestRecordFile
                        .getFileHash());
            }
        } else {
            // check if transactions are already present
            if (transactionRepository.existsById(recordFile.getConsensusStart()) &&
                    transactionRepository.existsById(recordFile.getConsensusEnd())) {
                throw duplicateFileException;
            }
        }

        // allow file processing but update to previous hash
        applicationStatusRepository.updateStatusValue(
                ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, latestRecordFile.getPreviousHash());
        log.debug("Transactions from partially processed record file {} were not persisted! Reprocessing...",
                recordFile.getName());
    }

    enum F_ENTITY_ID {
        ZERO, // column indices start at 1, this creates the necessary offset
        ID, ENTITY_SHARD, ENTITY_REALM, ENTITY_NUM, TYPE
    }
}
