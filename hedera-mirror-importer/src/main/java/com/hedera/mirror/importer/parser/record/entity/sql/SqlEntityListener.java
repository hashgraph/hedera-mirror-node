package com.hedera.mirror.importer.parser.record.entity.sql;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceUtils;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.PgCopy;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;

@Log4j2
@Named
@Order(0)
@ConditionOnEntityRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    private final DataSource dataSource;
    private final EntityRepository entityRepository;
    private final RecordFileRepository recordFileRepository;
    private final SqlProperties sqlProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenRepository tokenRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final ScheduleRepository scheduleRepository;

    // init schemas, writers, etc once per process
    private final PgCopy<Transaction> transactionPgCopy;
    private final PgCopy<CryptoTransfer> cryptoTransferPgCopy;
    private final PgCopy<NonFeeTransfer> nonFeeTransferPgCopy;
    private final PgCopy<FileData> fileDataPgCopy;
    private final PgCopy<ContractResult> contractResultPgCopy;
    private final PgCopy<LiveHash> liveHashPgCopy;
    private final PgCopy<TopicMessage> topicMessagePgCopy;
    private final PgCopy<TokenTransfer> tokenTransferPgCopy;
    private final PgCopy<TransactionSignature> transactionSignaturePgCopy;

    // used to optimize inserts into t_entities table so node and treasury ids are not tried for every transaction
    private final Cache entityCache;

    private final Collection<Transaction> transactions;
    private final Collection<CryptoTransfer> cryptoTransfers;
    private final Collection<NonFeeTransfer> nonFeeTransfers;
    private final Collection<FileData> fileData;
    private final Collection<ContractResult> contractResults;
    private final Collection<LiveHash> liveHashes;
    private final Collection<TopicMessage> topicMessages;
    private final Collection<EntityId> entityIds;
    private final Collection<TokenTransfer> tokenTransfers;
    private final Collection<TransactionSignature> transactionSignatures;

    public SqlEntityListener(RecordParserProperties recordParserProperties, SqlProperties sqlProperties,
                             DataSource dataSource,
                             RecordFileRepository recordFileRepository, MeterRegistry meterRegistry,
                             @Qualifier(CacheConfiguration.NEVER_EXPIRE_LARGE) CacheManager cacheManager,
                             EntityRepository entityRepository, ApplicationEventPublisher eventPublisher,
                             TokenRepository tokenRepository, TokenAccountRepository tokenAccountRepository,
                             ScheduleRepository scheduleRepository) {
        this.dataSource = dataSource;
        this.entityRepository = entityRepository;
        this.recordFileRepository = recordFileRepository;
        this.sqlProperties = sqlProperties;
        entityCache = cacheManager.getCache(CacheConfiguration.NEVER_EXPIRE_LARGE);
        this.eventPublisher = eventPublisher;
        this.tokenRepository = tokenRepository;
        this.tokenAccountRepository = tokenAccountRepository;
        this.scheduleRepository = scheduleRepository;

        transactionPgCopy = new PgCopy<>(Transaction.class, meterRegistry, recordParserProperties);
        cryptoTransferPgCopy = new PgCopy<>(CryptoTransfer.class, meterRegistry, recordParserProperties);
        nonFeeTransferPgCopy = new PgCopy<>(NonFeeTransfer.class, meterRegistry, recordParserProperties);
        fileDataPgCopy = new PgCopy<>(FileData.class, meterRegistry, recordParserProperties);
        contractResultPgCopy = new PgCopy<>(ContractResult.class, meterRegistry, recordParserProperties);
        liveHashPgCopy = new PgCopy<>(LiveHash.class, meterRegistry, recordParserProperties);
        topicMessagePgCopy = new PgCopy<>(TopicMessage.class, meterRegistry, recordParserProperties);
        tokenTransferPgCopy = new PgCopy<>(TokenTransfer.class, meterRegistry, recordParserProperties);
        transactionSignaturePgCopy = new PgCopy<>(TransactionSignature.class, meterRegistry, recordParserProperties);

        transactions = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        fileData = new ArrayList<>();
        contractResults = new ArrayList<>();
        liveHashes = new ArrayList<>();
        entityIds = new HashSet<>();
        topicMessages = new ArrayList<>();
        tokenTransfers = new ArrayList<>();
        transactionSignatures = new ArrayList<>();
    }

    @Override
    public boolean isEnabled() {
        return sqlProperties.isEnabled();
    }

    @Override
    public void onStart() {
        cleanup();
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        executeBatches();
        recordFileRepository.save(recordFile);
    }

    @Override
    public void onError() {
        cleanup();
    }

    private void cleanup() {
        contractResults.clear();
        cryptoTransfers.clear();
        entityIds.clear();
        fileData.clear();
        liveHashes.clear();
        nonFeeTransfers.clear();
        topicMessages.clear();
        transactions.clear();
        tokenTransfers.clear();
        transactionSignatures.clear();
        eventPublisher.publishEvent(new EntityBatchCleanupEvent(this));
    }

    private void executeBatches() {
        Connection connection = null;

        try {
            // batch save action may run asynchronously, triggering it before other operations can reduce latency
            eventPublisher.publishEvent(new EntityBatchSaveEvent(this));

            connection = DataSourceUtils.getConnection(dataSource);
            Stopwatch stopwatch = Stopwatch.createStarted();
            transactionPgCopy.copy(transactions, connection);
            cryptoTransferPgCopy.copy(cryptoTransfers, connection);
            nonFeeTransferPgCopy.copy(nonFeeTransfers, connection);
            fileDataPgCopy.copy(fileData, connection);
            contractResultPgCopy.copy(contractResults, connection);
            liveHashPgCopy.copy(liveHashes, connection);
            topicMessagePgCopy.copy(topicMessages, connection);
            tokenTransferPgCopy.copy(tokenTransfers, connection);
            transactionSignaturePgCopy.copy(transactionSignatures, connection);
            persistEntities();
            log.info("Completed batch inserts in {}", stopwatch);
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        } finally {
            cleanup();
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        transactions.add(transaction);
        if (transactions.size() == sqlProperties.getBatchSize()) {
            executeBatches();
        }
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        // only insert entities not found in cache
        if (EntityId.isEmpty(entityId) || entityCache.get(entityId.getId()) != null) {
            return;
        }

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

    @Override
    public void onToken(Token token) throws ImporterException {
        tokenRepository.save(token);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        tokenAccountRepository.save(tokenAccount);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        tokenTransfers.add(tokenTransfer);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        scheduleRepository.save(schedule);
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        transactionSignatures.add(transactionSignature);
    }

    private void persistEntities() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        entityIds.forEach(entityId -> {
            entityRepository.insertEntityId(entityId);
            entityCache.put(entityId, null);
        });
        log.info("Inserted {} entities in {}", entityIds.size(), stopwatch);
    }
}
