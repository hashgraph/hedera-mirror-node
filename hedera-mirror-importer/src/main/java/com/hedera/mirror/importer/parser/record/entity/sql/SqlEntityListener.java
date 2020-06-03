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

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;

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
import com.hedera.mirror.importer.parser.record.ConditionalOnRecordParser;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Log4j2
@Named
@ConditionalOnRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener, Closeable {
    private final DataSource dataSource;
    private final ExecutorService executorService;
    private final RecordFileRepository recordFileRepository;
    private final EntityRepository entityRepository;
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

    private List<Transaction> transactions;
    private List<CryptoTransfer> cryptoTransfers;
    private List<NonFeeTransfer> nonFeeTransfers;
    private List<FileData> fileData;
    private List<ContractResult> contractResults;
    private List<LiveHash> liveHashes;
    private List<TopicMessage> topicMessages;
    private List<EntityId> entityIds;

    public SqlEntityListener(SqlProperties properties, DataSource dataSource,
                             RecordFileRepository recordFileRepository, EntityRepository entityRepository) {
        this.dataSource = dataSource;
        this.recordFileRepository = recordFileRepository;
        this.entityRepository = entityRepository;
        this.executorService = Executors.newFixedThreadPool(properties.getThreads());
        try {
            transactionPgCopy = new PgCopy<>(getConnection(), Transaction.class);
            cryptoTransferPgCopy = new PgCopy<>(getConnection(), CryptoTransfer.class);
            nonFeeTransferPgCopy = new PgCopy<>(getConnection(), NonFeeTransfer.class);
            fileDataPgCopy = new PgCopy<>(getConnection(), FileData.class);
            contractResultPgCopy = new PgCopy<>(getConnection(), ContractResult.class);
            liveHashPgCopy = new PgCopy<>(getConnection(), LiveHash.class);
            topicMessagePgCopy = new PgCopy<>(getConnection(), TopicMessage.class);
        } catch (SQLException e) {
            throw new ParserException("Error setting up postgres copier", e);
        }
    }

    @Override
    public void onStart(StreamFileData streamFileData) {
        String fileName = streamFileData.getFilename();
        if (recordFileRepository.findByName(fileName).size() > 0) {
            throw new DuplicateFileException("File already exists in the database: " + fileName);
        }
        transactions = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        fileData = new ArrayList<>();
        contractResults = new ArrayList<>();
        liveHashes = new ArrayList<>();
        entityIds = new ArrayList<>();
        topicMessages = new ArrayList<>();
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> transactionPgCopy.copy(transactions), executorService),
                    CompletableFuture.runAsync(() -> cryptoTransferPgCopy.copy(cryptoTransfers), executorService),
                    CompletableFuture.runAsync(() -> nonFeeTransferPgCopy.copy(nonFeeTransfers), executorService),
                    CompletableFuture.runAsync(() -> fileDataPgCopy.copy(fileData), executorService),
                    CompletableFuture.runAsync(() -> contractResultPgCopy.copy(contractResults), executorService),
                    CompletableFuture.runAsync(() -> liveHashPgCopy.copy(liveHashes), executorService),
                    CompletableFuture.runAsync(() -> topicMessagePgCopy.copy(topicMessages), executorService),
                    CompletableFuture.runAsync(() ->
                        entityIds.forEach(entityRepository::insertEntityIdDoNothingOnConflict), executorService)
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
            throw new ParserException(e);
        }
        recordFileRepository.save(recordFile);
    }

    @Override
    public void onError() {
        // no error handling
    }

    @Override
    public void close() {
        transactionPgCopy.close();
        cryptoTransferPgCopy.close();
        nonFeeTransferPgCopy.close();
        fileDataPgCopy.close();
        contractResultPgCopy.close();
        liveHashPgCopy.close();
        topicMessagePgCopy.close();
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        transactions.add(transaction);
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        if (!seenEntityIds.contains(entityId)) {
            entityIds.add(entityId);
            seenEntityIds.add(entityId);
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

    private Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            log.error("Error getting connection ", e);
            throw new ParserSQLException(e);
        }
    }
}
