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
import java.util.Map;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceUtils;

import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenAccountKey;
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
import com.hedera.mirror.importer.parser.record.entity.AbstractEntityListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.upsert.EntityUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.NftUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.ScheduleUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.TokenAccountUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.TokenUpsertQueryGenerator;

@Log4j2
@Named
@Order(0)
@ConditionOnEntityRecordParser
public class SqlEntityListener extends AbstractEntityListener implements RecordStreamFileListener {

    private final DataSource dataSource;
    private final RecordFileRepository recordFileRepository;
    private final SqlProperties sqlProperties;
    private final ApplicationEventPublisher eventPublisher;

    // init schemas, writers, etc once per process
    private final PgCopy<AssessedCustomFee> assessedCustomFeePgCopy;
    private final PgCopy<ContractResult> contractResultPgCopy;
    private final PgCopy<CryptoTransfer> cryptoTransferPgCopy;
    private final PgCopy<CustomFee> customFeePgCopy;
    private final PgCopy<FileData> fileDataPgCopy;
    private final PgCopy<LiveHash> liveHashPgCopy;
    private final PgCopy<NftTransfer> nftTransferPgCopy;
    private final PgCopy<NonFeeTransfer> nonFeeTransferPgCopy;
    private final PgCopy<TokenTransfer> tokenTransferPgCopy;
    private final PgCopy<TopicMessage> topicMessagePgCopy;
    private final PgCopy<Transaction> transactionPgCopy;
    private final PgCopy<TransactionSignature> transactionSignaturePgCopy;

    private final UpsertPgCopy<Entity> entityPgCopy;
    private final UpsertPgCopy<Nft> nftPgCopy;
    private final UpsertPgCopy<Schedule> schedulePgCopy;
    private final UpsertPgCopy<TokenAccount> tokenAccountPgCopy;
    private final UpsertPgCopy<Token> tokenPgCopy;

    // lists of insert only domains
    private final Collection<AssessedCustomFee> assessedCustomFees;
    private final Collection<ContractResult> contractResults;
    private final Collection<CryptoTransfer> cryptoTransfers;
    private final Collection<CustomFee> customFees;
    private final Collection<FileData> fileData;
    private final Collection<LiveHash> liveHashes;
    private final Collection<NftTransfer> nftTransfers;
    private final Collection<NonFeeTransfer> nonFeeTransfers;
    private final Collection<TokenAccount> tokenAccounts;
    private final Collection<TokenTransfer> tokenTransfers;
    private final Collection<TopicMessage> topicMessages;
    private final Collection<Transaction> transactions;
    private final Collection<TransactionSignature> transactionSignatures;

    // maps of upgradable domains
    private final Map<Long, Entity> entities;
    private final Map<Long, Schedule> schedules;
    private final Map<Long, Token> tokens;
    private final Map<NftId, Nft> nfts;

    // tracks the state of <token, account> relationships in a batch, the initial state before the batch is in db.
    // for each <token, account> update, merge the state and the update, save the merged state to the batch.
    // during upsert pgcopy, the merged state at time T is again merged with the initial state before the batch to
    // get the full state at time T
    private final Map<TokenAccountKey, TokenAccount> tokenAccountState;

    public SqlEntityListener(RecordParserProperties recordParserProperties, SqlProperties sqlProperties,
                             DataSource dataSource,
                             RecordFileRepository recordFileRepository, MeterRegistry meterRegistry,
                             ApplicationEventPublisher eventPublisher,
                             EntityUpsertQueryGenerator entityUpsertQueryGenerator,
                             ScheduleUpsertQueryGenerator scheduleUpsertQueryGenerator,
                             TokenUpsertQueryGenerator tokenUpsertQueryGenerator,
                             TokenAccountUpsertQueryGenerator tokenAccountUpsertQueryGenerator,
                             NftUpsertQueryGenerator nftUpsertQueryGenerator) {
        this.dataSource = dataSource;
        this.recordFileRepository = recordFileRepository;
        this.sqlProperties = sqlProperties;
        this.eventPublisher = eventPublisher;

        // insert only tables
        assessedCustomFeePgCopy = new PgCopy<>(AssessedCustomFee.class, meterRegistry, recordParserProperties);
        contractResultPgCopy = new PgCopy<>(ContractResult.class, meterRegistry, recordParserProperties);
        cryptoTransferPgCopy = new PgCopy<>(CryptoTransfer.class, meterRegistry, recordParserProperties);
        customFeePgCopy = new PgCopy<>(CustomFee.class, meterRegistry, recordParserProperties);
        fileDataPgCopy = new PgCopy<>(FileData.class, meterRegistry, recordParserProperties);
        liveHashPgCopy = new PgCopy<>(LiveHash.class, meterRegistry, recordParserProperties);
        nftTransferPgCopy = new PgCopy<>(NftTransfer.class, meterRegistry, recordParserProperties);
        nonFeeTransferPgCopy = new PgCopy<>(NonFeeTransfer.class, meterRegistry, recordParserProperties);
        tokenTransferPgCopy = new PgCopy<>(TokenTransfer.class, meterRegistry, recordParserProperties);
        topicMessagePgCopy = new PgCopy<>(TopicMessage.class, meterRegistry, recordParserProperties);
        transactionPgCopy = new PgCopy<>(Transaction.class, meterRegistry, recordParserProperties);
        transactionSignaturePgCopy = new PgCopy<>(TransactionSignature.class, meterRegistry, recordParserProperties);

        // updatable tables
        entityPgCopy = new UpsertPgCopy<>(Entity.class, meterRegistry, recordParserProperties,
                entityUpsertQueryGenerator);
        nftPgCopy = new UpsertPgCopy<>(Nft.class, meterRegistry, recordParserProperties,
                nftUpsertQueryGenerator);
        schedulePgCopy = new UpsertPgCopy<>(Schedule.class, meterRegistry, recordParserProperties,
                scheduleUpsertQueryGenerator);
        tokenAccountPgCopy = new UpsertPgCopy<>(TokenAccount.class, meterRegistry, recordParserProperties,
                tokenAccountUpsertQueryGenerator);
        tokenPgCopy = new UpsertPgCopy<>(Token.class, meterRegistry, recordParserProperties,
                tokenUpsertQueryGenerator);

        assessedCustomFees = new ArrayList<>();
        contractResults = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        customFees = new ArrayList<>();
        fileData = new ArrayList<>();
        liveHashes = new ArrayList<>();
        nftTransfers = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        tokenTransfers = new ArrayList<>();
        tokenAccounts = new ArrayList<>();
        topicMessages = new ArrayList<>();
        transactions = new ArrayList<>();
        transactionSignatures = new ArrayList<>();

        entities = new HashMap<>();
        nfts = new HashMap<>();
        schedules = new HashMap<>();
        tokens = new HashMap<>();

        tokenAccountState = new HashMap<>();
    }

    @Override
    public boolean isEnabled() {
        return sqlProperties.isEnabled();
    }

    @Override
    public void onStart() {
        cleanup();
        Connection connection = null;
        try {
            connection = DataSourceUtils.getConnection(dataSource);
            entityPgCopy.init(connection);
            nftPgCopy.init(connection);
            schedulePgCopy.init(connection);
            tokenAccountPgCopy.init(connection);
            tokenPgCopy.init(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
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
        try {
            assessedCustomFees.clear();
            contractResults.clear();
            cryptoTransfers.clear();
            customFees.clear();
            entities.clear();
            fileData.clear();
            liveHashes.clear();
            nonFeeTransfers.clear();
            nfts.clear();
            nftTransfers.clear();
            schedules.clear();
            topicMessages.clear();
            tokenAccounts.clear();
            tokenAccountState.clear();
            tokens.clear();
            tokenTransfers.clear();
            transactions.clear();
            transactionSignatures.clear();
            eventPublisher.publishEvent(new EntityBatchCleanupEvent(this));
        } catch (BeanCreationNotAllowedException e) {
            // This error can occur during shutdown
        }
    }

    private void executeBatches() {
        Connection connection = null;

        try {
            // batch save action may run asynchronously, triggering it before other operations can reduce latency
            eventPublisher.publishEvent(new EntityBatchSaveEvent(this));

            connection = DataSourceUtils.getConnection(dataSource);
            Stopwatch stopwatch = Stopwatch.createStarted();

            // insert only operations
            assessedCustomFeePgCopy.copy(assessedCustomFees, connection);
            contractResultPgCopy.copy(contractResults, connection);
            cryptoTransferPgCopy.copy(cryptoTransfers, connection);
            customFeePgCopy.copy(customFees, connection);
            fileDataPgCopy.copy(fileData, connection);
            liveHashPgCopy.copy(liveHashes, connection);
            topicMessagePgCopy.copy(topicMessages, connection);
            transactionPgCopy.copy(transactions, connection);
            transactionSignaturePgCopy.copy(transactionSignatures, connection);

            // insert operations with conflict management
            entityPgCopy.copy(entities.values(), connection);
            tokenPgCopy.copy(tokens.values(), connection);
            // ingest tokenAccounts after tokens since some fields of token accounts depends on the associated token
            tokenAccountPgCopy.copy(tokenAccounts, connection);
            nftPgCopy.copy(nfts.values(), connection); // persist nft after token entity
            schedulePgCopy.copy(schedules.values(), connection);

            // transfers operations should be last to ensure insert logic completeness, entities should already exist
            nonFeeTransferPgCopy.copy(nonFeeTransfers, connection);
            nftTransferPgCopy.copy(nftTransfers, connection);
            tokenTransferPgCopy.copy(tokenTransfers, connection);

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
    public void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {
        assessedCustomFees.add(assessedCustomFee);
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        contractResults.add(contractResult);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        cryptoTransfers.add(cryptoTransfer);
    }

    @Override
    public void onCustomFee(CustomFee customFee) throws ImporterException {
        customFees.add(customFee);
    }

    @Override
    public void onEntity(Entity entity) throws ImporterException {
        EntityId entityId = entity.toEntityId();
        // handle empty id case
        if (EntityId.isEmpty(entityId)) {
            return;
        }

        entities.merge(entity.getId(), entity, this::mergeEntity);
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
    public void onNft(Nft nft) throws ImporterException {
        nfts.merge(nft.getId(), nft, this::mergeNft);
    }

    @Override
    public void onNftTransfer(NftTransfer nftTransfer) throws ImporterException {
        nftTransfers.add(nftTransfer);
    }

    @Override
    public void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
        nonFeeTransfers.add(nonFeeTransfer);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        // schedules could experience multiple updates in a single record file, handle updates in memory for this case
        schedules.merge(schedule.getScheduleId(), schedule, this::mergeSchedule);
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        // tokens could experience multiple updates in a single record file, handle updates in memory for this case
        tokens.merge(token.getTokenId().getTokenId().getId(), token, this::mergeToken);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        var key = new TokenAccountKey(tokenAccount.getId().getTokenId(), tokenAccount.getId().getAccountId());
        TokenAccount merged = tokenAccountState.merge(key, tokenAccount, this::mergeTokenAccount);
        tokenAccounts.add(merged);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        tokenTransfers.add(tokenTransfer);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        topicMessages.add(topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        transactions.add(transaction);
        if (transactions.size() == sqlProperties.getBatchSize()) {
            executeBatches();
        }
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        transactionSignatures.add(transactionSignature);
    }
}
