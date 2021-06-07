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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenAccountId;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.PgCopy;
import com.hedera.mirror.importer.parser.UpsertPgCopy;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.EntityIdRepositoryCustomImpl;
import com.hedera.mirror.importer.repository.EntityRepositoryCustomImpl;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.ScheduleRepositoryCustomImpl;
import com.hedera.mirror.importer.repository.TokenAccountRepositoryCustomImpl;
import com.hedera.mirror.importer.repository.TokenRepositoryCustomImpl;

@Log4j2
@Named
@Order(0)
@ConditionOnEntityRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    private final DataSource dataSource;
    private final RecordFileRepository recordFileRepository;
    private final SqlProperties sqlProperties;
    private final ApplicationEventPublisher eventPublisher;

    // init schemas, writers, etc once per process
    private final PgCopy<ContractResult> contractResultPgCopy;
    private final PgCopy<CryptoTransfer> cryptoTransferPgCopy;
    private final PgCopy<FileData> fileDataPgCopy;
    private final PgCopy<LiveHash> liveHashPgCopy;
    private final PgCopy<NonFeeTransfer> nonFeeTransferPgCopy;
    private final PgCopy<TokenTransfer> tokenTransferPgCopy;
    private final PgCopy<TopicMessage> topicMessagePgCopy;
    private final PgCopy<Transaction> transactionPgCopy;
    private final PgCopy<TransactionSignature> transactionSignaturePgCopy;

    private final UpsertPgCopy<Entity> entityIdPgCopy;
    private final UpsertPgCopy<Entity> entityPgCopy;
    private final UpsertPgCopy<Schedule> schedulePgCopy;
    private final UpsertPgCopy<TokenAccount> tokenAccountPgCopy;
    private final UpsertPgCopy<Token> tokenPgCopy;

    // used to optimize inserts into entity table so node and treasury ids are not tried for every transaction
    private final Cache entityCache;

    // lists of insert only domains
    private final Collection<ContractResult> contractResults;
    private final Collection<CryptoTransfer> cryptoTransfers;
    private final Collection<EntityId> entityIds;
    private final Collection<FileData> fileData;
    private final Collection<LiveHash> liveHashes;
    private final Collection<NonFeeTransfer> nonFeeTransfers;
    private final Collection<TopicMessage> topicMessages;
    private final Collection<TokenTransfer> tokenTransfers;
    private final Collection<Transaction> transactions;
    private final Collection<TransactionSignature> transactionSignatures;

    // maps of upgradable domains
    private final Map<Long, Entity> entities;
    private final Map<Long, Entity> entityIdEntities;
    private final Map<Long, Schedule> schedules;
    private final Map<Long, Token> tokens;
    private final Map<TokenAccountId, TokenAccount> tokenAccounts;

    public SqlEntityListener(RecordParserProperties recordParserProperties, SqlProperties sqlProperties,
                             DataSource dataSource,
                             RecordFileRepository recordFileRepository, MeterRegistry meterRegistry,
                             @Qualifier(CacheConfiguration.NEVER_EXPIRE_LARGE) CacheManager cacheManager,
                             ApplicationEventPublisher eventPublisher,
                             EntityRepositoryCustomImpl entityRepositoryCustom,
                             EntityIdRepositoryCustomImpl entityIdRepositoryCustom,
                             ScheduleRepositoryCustomImpl scheduleRepositoryCustom,
                             TokenRepositoryCustomImpl tokenRepositoryCustom,
                             TokenAccountRepositoryCustomImpl tokenAccountRepositoryCustom) {
        this.dataSource = dataSource;
        this.recordFileRepository = recordFileRepository;
        this.sqlProperties = sqlProperties;
        entityCache = cacheManager.getCache(CacheConfiguration.NEVER_EXPIRE_LARGE);
        this.eventPublisher = eventPublisher;

        // insert only tables
        contractResultPgCopy = new PgCopy<>(ContractResult.class, meterRegistry, recordParserProperties);
        cryptoTransferPgCopy = new PgCopy<>(CryptoTransfer.class, meterRegistry, recordParserProperties);
        fileDataPgCopy = new PgCopy<>(FileData.class, meterRegistry, recordParserProperties);
        liveHashPgCopy = new PgCopy<>(LiveHash.class, meterRegistry, recordParserProperties);
        nonFeeTransferPgCopy = new PgCopy<>(NonFeeTransfer.class, meterRegistry, recordParserProperties);
        tokenTransferPgCopy = new PgCopy<>(TokenTransfer.class, meterRegistry, recordParserProperties);
        topicMessagePgCopy = new PgCopy<>(TopicMessage.class, meterRegistry, recordParserProperties);
        transactionPgCopy = new PgCopy<>(Transaction.class, meterRegistry, recordParserProperties);
        transactionSignaturePgCopy = new PgCopy<>(TransactionSignature.class, meterRegistry, recordParserProperties);

        // updatable tables
        entityPgCopy = new UpsertPgCopy<>(Entity.class, meterRegistry, recordParserProperties, entityRepositoryCustom);
        entityIdPgCopy = new UpsertPgCopy<>(Entity.class, meterRegistry, recordParserProperties,
                entityIdRepositoryCustom);
        schedulePgCopy = new UpsertPgCopy<>(Schedule.class, meterRegistry, recordParserProperties,
                scheduleRepositoryCustom);
        tokenAccountPgCopy = new UpsertPgCopy<>(TokenAccount.class, meterRegistry, recordParserProperties,
                tokenAccountRepositoryCustom);
        tokenPgCopy = new UpsertPgCopy<>(Token.class, meterRegistry, recordParserProperties,
                tokenRepositoryCustom);

        contractResults = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        fileData = new ArrayList<>();
        liveHashes = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        tokenTransfers = new ArrayList<>();
        topicMessages = new ArrayList<>();
        transactions = new ArrayList<>();
        transactionSignatures = new ArrayList<>();

        entityIdEntities = new HashMap<>();
        entities = new HashMap<>();
        schedules = new HashMap<>();
        tokens = new HashMap<>();
        tokenAccounts = new HashMap<>();

        entityIds = new HashSet<>();
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
        entities.clear();
        entityIds.clear();
        fileData.clear();
        liveHashes.clear();
        entityIdEntities.clear();
        nonFeeTransfers.clear();
        schedules.clear();
        topicMessages.clear();
        transactions.clear();
        tokenAccounts.clear();
        tokens.clear();
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

            // insert only operations
            contractResultPgCopy.copy(contractResults, connection);
            cryptoTransferPgCopy.copy(cryptoTransfers, connection);
            fileDataPgCopy.copy(fileData, connection);
            liveHashPgCopy.copy(liveHashes, connection);
            nonFeeTransferPgCopy.copy(nonFeeTransfers, connection);
            tokenTransferPgCopy.copy(tokenTransfers, connection);
            topicMessagePgCopy.copy(topicMessages, connection);
            transactionPgCopy.copy(transactions, connection);
            transactionSignaturePgCopy.copy(transactionSignatures, connection);

            // insert operations with conflict management for updates
            entityPgCopy.copy(entities.values(), connection);
            entityIdPgCopy.copy(entityIdEntities.values(), connection);
            schedulePgCopy.copy(schedules.values(), connection);
            tokenPgCopy.copy(tokens.values(), connection);
            tokenAccountPgCopy.copy(tokenAccounts.values(), connection);
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
    public void onEntity(Entity entity) throws ImporterException {
        // entity could experience multiple updates in a single record file, handle updates in memory for this case
        if (entities.containsKey(entity.getId())) {
            updateCachedEntity(entity);
            return;
        }

        entities.put(entity.getId(), entity);
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        // only insert entities not found in cache
        if (EntityId.isEmpty(entityId) || entityCache.get(entityId.getId()) != null ||
                entities.containsKey(entityId.getId())) {
            return;
        }

        entityIds.add(entityId);
        entityIdEntities.put(entityId.getId(), entityId.toEntity());
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
        // entity could experience multiple updates in a single record file, handle updates in memory for this case
        long tokenId = token.getTokenId().getTokenId().getId();
        if (tokens.containsKey(tokenId)) {
            updateCachedToken(token);
            return;
        }

        tokens.put(tokenId, token);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        if (tokenAccounts.containsKey(tokenAccount.getId())) {
            updateCachedTokenAccount(tokenAccount);
            return;
        }

        tokenAccounts.put(tokenAccount.getId(), tokenAccount);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        tokenTransfers.add(tokenTransfer);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        if (schedules.containsKey(schedule.getScheduleId().getId())) {
            updateCachedSchedule(schedule);
            return;
        }

        schedules.put(schedule.getScheduleId().getId(), schedule);
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        transactionSignatures.add(transactionSignature);
    }

    private void updateCachedEntity(Entity newEntity) {
        Entity cachedEntity = entities.get(newEntity.getId());
        cachedEntity.setAutoRenewPeriod(newEntity.getAutoRenewPeriod());

        if (newEntity.getDeleted() != null) {
            cachedEntity.setDeleted(newEntity.getDeleted());
        }

        if (newEntity.getExpirationTimestamp() != null) {
            cachedEntity.setExpirationTimestamp(newEntity.getExpirationTimestamp());
        }

        if (newEntity.getKey() != null) {
            cachedEntity.setKey(newEntity.getKey());
            cachedEntity.setMemo(newEntity.getMemo());
        }

        if (newEntity.getMemo() != null) {
            cachedEntity.setMemo(newEntity.getMemo());
        }
    }

    private void updateCachedToken(Token newToken) {
        Token cachedToken = tokens.get(newToken.getTokenId().getTokenId().getId());

        if (newToken.getFreezeKey() != null) {
            cachedToken.setFreezeKey(newToken.getFreezeKey());
        }

        if (newToken.getKycKey() != null) {
            cachedToken.setKycKey(newToken.getKycKey());
        }

        cachedToken.setModifiedTimestamp(newToken.getModifiedTimestamp());

        if (newToken.getName() != null) {
            cachedToken.setName(newToken.getName());
        }

        if (newToken.getSymbol() != null) {
            cachedToken.setSymbol(newToken.getSymbol());
        }

        if (newToken.getTotalSupply() != null) {
            cachedToken.setTotalSupply(newToken.getTotalSupply());
        }

        if (newToken.getTreasuryAccountId() != null) {
            cachedToken.setTreasuryAccountId(newToken.getTreasuryAccountId());
        }

        if (newToken.getSupplyKey() != null) {
            cachedToken.setSupplyKey(newToken.getSupplyKey());
        }

        if (newToken.getWipeKey() != null) {
            cachedToken.setWipeKey(newToken.getWipeKey());
        }
    }

    private void updateCachedTokenAccount(TokenAccount newTokenAccount) {
        TokenAccount cachedTokenAccount = tokenAccounts.get(newTokenAccount.getId());

        if (newTokenAccount.getAssociated() != null) {
            cachedTokenAccount.setAssociated(newTokenAccount.getAssociated());
        }

        if (newTokenAccount.getFreezeStatus() != null) {
            cachedTokenAccount.setFreezeStatus(newTokenAccount.getFreezeStatus());
        }

        if (newTokenAccount.getKycStatus() != null) {
            cachedTokenAccount.setKycStatus(newTokenAccount.getKycStatus());
        }

        cachedTokenAccount.setModifiedTimestamp(newTokenAccount.getModifiedTimestamp());
    }

    private void updateCachedSchedule(Schedule schedule) {
        Schedule cachedSchedule = schedules.get(schedule.getScheduleId().getId());
        cachedSchedule.setExecutedTimestamp(schedule.getExecutedTimestamp());
    }
}
