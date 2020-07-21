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
import io.micrometer.core.instrument.Timer;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.apache.commons.io.FilenameUtils;

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
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Log4j2
@Named
@ConditionOnEntityRecordParser

public class SqlEntityListener implements EntityListener, RecordStreamFileListener, Closeable {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

    private final DataSource dataSource;
    private final ExecutorService executorService;
    private final RecordFileRepository recordFileRepository;
    private final EntityRepository entityRepository;
    private final SqlProperties sqlProperties;

    @Qualifier(value = "sessionCache")
    private final CacheManager cacheManager;
    private long batchCount;

    // Keeps track of entityIds seen so far. This is for optimizing inserts into t_entities table so that insertion of
    // node and treasury ids are not tried for every transaction.
    private final Collection<EntityId> seenEntityIds = new HashSet<>();
    // init connections, schemas, writers, etc once per process
    private final PgCopy<Transaction> transactionPgCopy;
    private final PgCopy<CryptoTransfer> cryptoTransferPgCopy;
    private final PgCopy<NonFeeTransfer> nonFeeTransferPgCopy;
    private final PgCopy<FileData> fileDataPgCopy;
    private final PgCopy<ContractResult> contractResultPgCopy;
    private final PgCopy<LiveHash> liveHashPgCopy;
    private final PgCopy<TopicMessage> topicMessagePgCopy;

    private final Timer insertDuration;

    private HashSet<Transaction> transactions;
    private HashSet<CryptoTransfer> cryptoTransfers;
    private HashSet<NonFeeTransfer> nonFeeTransfers;
    private HashSet<FileData> fileData;
    private HashSet<ContractResult> contractResults;
    private HashSet<LiveHash> liveHashes;
    private HashSet<TopicMessage> topicMessages;
    private HashSet<EntityId> entityIds;
    private final Cache entityCache;

    private PreparedStatement sqlNotifyTopicMessage;
    private Connection connection;

    public SqlEntityListener(SqlProperties properties, DataSource dataSource,
                             RecordFileRepository recordFileRepository, EntityRepository entityRepository,
                             MeterRegistry meterRegistry, CacheManager cacheManager) {
        this.dataSource = dataSource;
        this.recordFileRepository = recordFileRepository;
        this.entityRepository = entityRepository;
        sqlProperties = properties;
        executorService = Executors.newFixedThreadPool(properties.getThreads());
        this.cacheManager = cacheManager;

        transactionPgCopy = new PgCopy<>(dataSource, Transaction.class, meterRegistry, sqlProperties.getBatchSize());
        cryptoTransferPgCopy = new PgCopy<>(dataSource, CryptoTransfer.class, meterRegistry, sqlProperties
                .getBatchSize());
        nonFeeTransferPgCopy = new PgCopy<>(dataSource, NonFeeTransfer.class, meterRegistry, sqlProperties
                .getBatchSize());
        fileDataPgCopy = new PgCopy<>(dataSource, FileData.class, meterRegistry, sqlProperties.getBatchSize());
        contractResultPgCopy = new PgCopy<>(dataSource, ContractResult.class, meterRegistry, sqlProperties
                .getBatchSize());
        liveHashPgCopy = new PgCopy<>(dataSource, LiveHash.class, meterRegistry, sqlProperties.getBatchSize());
        topicMessagePgCopy = new PgCopy<>(dataSource, TopicMessage.class, meterRegistry, sqlProperties.getBatchSize());

        insertDuration = Timer.builder("hedera.mirror.importer.parser.record.entity.sql.insert")
                .description("Time to insert all entities into database")
                .register(meterRegistry);

        entityCache = cacheManager.getCache("seen_entities");
    }

    @Override
    public void onStart(StreamFileData streamFileData) {
        String fileName = FilenameUtils.getName(streamFileData.getFilename());
        entityIds = new HashSet<>();
        if (recordFileRepository.findByName(fileName).size() > 0) {
            throw new DuplicateFileException("File already exists in the database: " + fileName);
        }
        transactions = new HashSet<>();
        cryptoTransfers = new HashSet<>();
        nonFeeTransfers = new HashSet<>();
        fileData = new HashSet<>();
        contractResults = new HashSet<>();
        liveHashes = new HashSet<>();
        entityIds = new HashSet<>();
        topicMessages = new HashSet<>();
        batchCount = 0;

        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false); // do not auto-commit
            connection.setClientInfo("ApplicationName", getClass().getSimpleName());

            sqlNotifyTopicMessage = connection.prepareStatement("select pg_notify('topic_message', ?)");
        } catch (SQLException e) {
            throw new ParserSQLException("Error setting up connection to database", e);
        }
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        executeBatches();

        try {
            connection.commit();
            recordFileRepository.save(recordFile);
            closeConnectionAndStatements();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onError() {
        // no error handling
    }

    @Override
    public void close() {
        executorService.shutdown();
    }

    private ExecuteBatchResult executeBatch(PreparedStatement ps) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        var executeResult = ps.executeBatch();
        return new ExecuteBatchResult(executeResult == null ? 0 : executeResult.length, stopwatch
                .elapsed(TimeUnit.MILLISECONDS));
    }

    private void closeConnectionAndStatements() {
        try {
            sqlNotifyTopicMessage.close();
            connection.close();
        } catch (SQLException e) {
            throw new ParserSQLException("Error closing connection", e);
        }
    }

    private void executeBatches() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> transactionPgCopy.copy(transactions), executorService),
                    CompletableFuture.runAsync(() -> cryptoTransferPgCopy.copy(cryptoTransfers), executorService),
                    CompletableFuture.runAsync(() -> nonFeeTransferPgCopy.copy(nonFeeTransfers), executorService),
                    CompletableFuture.runAsync(() -> fileDataPgCopy.copy(fileData), executorService),
                    CompletableFuture.runAsync(() -> contractResultPgCopy.copy(contractResults), executorService),
                    CompletableFuture.runAsync(() -> liveHashPgCopy.copy(liveHashes), executorService),
                    CompletableFuture.runAsync(() -> topicMessagePgCopy.copy(topicMessages), executorService),
                    CompletableFuture
                            .runAsync(() -> entityIds.forEach(entityId -> {
                                if (entityCache.get(entityId.getId()) == null) {
                                    entityRepository.insertEntityId(entityId);
                                    entityCache.put(entityId.getId(), null);
                                }
                            }), executorService)
                    ,
                    CompletableFuture.runAsync(() -> {
                        try {
                            var topicMessageNotifications = executeBatch(sqlNotifyTopicMessage);
                            log.info("Inserted {} topic notifications", topicMessageNotifications);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }, executorService)
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ParserException(e);
        }
        insertDuration.record(stopwatch.elapsed());
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
        entityIds.add(entityId);
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

    @Data
    @AllArgsConstructor
    private static class ExecuteBatchResult {
        int numRows;
        long timeTakenInMs;

        @Override
        public String toString() {
            return numRows + " (" + timeTakenInMs + "ms)";
        }
    }
}
