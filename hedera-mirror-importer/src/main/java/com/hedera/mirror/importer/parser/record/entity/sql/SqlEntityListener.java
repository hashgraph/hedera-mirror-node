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

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceUtils;

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
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Log4j2
@Named
@Order(0)
@ConditionOnEntityRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    private final DataSource dataSource;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final EntityRepository entityRepository;
    private final RecordFileRepository recordFileRepository;
    private final SqlProperties sqlProperties;

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

    private Connection connection;
    private long batchCount;

    public SqlEntityListener(SqlProperties sqlProperties, DataSource dataSource,
                             RecordFileRepository recordFileRepository, MeterRegistry meterRegistry,
                             ApplicationStatusRepository applicationStatusRepository,
                             EntityRepository entityRepository) {
        this.dataSource = dataSource;
        this.applicationStatusRepository = applicationStatusRepository;
        this.entityRepository = entityRepository;
        this.recordFileRepository = recordFileRepository;
        this.sqlProperties = sqlProperties;

        transactionPgCopy = new PgCopy<>(Transaction.class, meterRegistry, sqlProperties);
        cryptoTransferPgCopy = new PgCopy<>(CryptoTransfer.class, meterRegistry, sqlProperties);
        nonFeeTransferPgCopy = new PgCopy<>(NonFeeTransfer.class, meterRegistry, sqlProperties);
        fileDataPgCopy = new PgCopy<>(FileData.class, meterRegistry, sqlProperties);
        contractResultPgCopy = new PgCopy<>(ContractResult.class, meterRegistry, sqlProperties);
        liveHashPgCopy = new PgCopy<>(LiveHash.class, meterRegistry, sqlProperties);
        topicMessagePgCopy = new PgCopy<>(TopicMessage.class, meterRegistry, sqlProperties);

        transactions = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        fileData = new ArrayList<>();
        contractResults = new ArrayList<>();
        liveHashes = new ArrayList<>();
        entityIds = new HashSet<>();
        topicMessages = new ArrayList<>();
    }

    @Override
    public boolean isEnabled() {
        return sqlProperties.isEnabled();
    }

    @Override
    public void onStart(StreamFileData streamFileData) {
        try {
            cleanup();
            connection = DataSourceUtils.getConnection(dataSource);
        } catch (Exception e) {
            throw new ParserSQLException("Error setting up connection to database", e);
        }
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        executeBatches();

        try {
            recordFileRepository.save(recordFile);
            applicationStatusRepository.updateStatusValue(
                    ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, recordFile.getFileHash());
        } finally {
            cleanup();
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public void onError() {
        cleanup();
        DataSourceUtils.releaseConnection(connection, dataSource);
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
        entityRepository.insertEntityId(entityId);
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
}
