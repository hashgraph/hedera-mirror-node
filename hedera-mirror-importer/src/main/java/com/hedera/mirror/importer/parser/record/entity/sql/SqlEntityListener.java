/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record.entity.sql;

import static com.hedera.mirror.importer.config.MirrorImporterConfiguration.DELETED_TOKEN_DISSOCIATE_BATCH_PERSISTER;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.AbstractCryptoAllowance;
import com.hedera.mirror.common.domain.entity.AbstractNftAllowance;
import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.SidecarFileRepository;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;

@Log4j2
@Named
@Order(0)
@ConditionOnEntityRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    private final BatchPersister batchPersister;
    private final EntityIdService entityIdService;
    private final EntityProperties entityProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final NftRepository nftRepository;
    private final RecordFileRepository recordFileRepository;
    private final SidecarFileRepository sidecarFileRepository;
    private final SqlProperties sqlProperties;
    private final BatchPersister tokenDissociateTransferBatchPersister;

    // lists of insert only domains
    private final Collection<AssessedCustomFee> assessedCustomFees;
    private final Collection<Contract> contracts;
    private final Collection<ContractAction> contractActions;
    private final Collection<ContractLog> contractLogs;
    private final Collection<ContractResult> contractResults;
    private final Collection<ContractStateChange> contractStateChanges;
    private final Collection<CryptoAllowance> cryptoAllowances;
    private final Collection<CryptoTransfer> cryptoTransfers;
    private final Collection<CustomFee> customFees;
    private final Collection<TokenTransfer> deletedTokenDissociateTransfers;
    private final Collection<Entity> entities;
    private final Collection<EthereumTransaction> ethereumTransactions;
    private final Collection<FileData> fileData;
    private final Collection<LiveHash> liveHashes;
    private final Collection<NetworkStake> networkStakes;
    private final Collection<NftAllowance> nftAllowances;
    private final Collection<NodeStake> nodeStakes;
    private final Collection<NonFeeTransfer> nonFeeTransfers;
    private final Collection<Prng> prngs;
    private final Collection<StakingRewardTransfer> stakingRewardTransfers;
    private final Collection<TokenAccount> tokenAccounts;
    private final Collection<TokenAllowance> tokenAllowances;
    private final Collection<TokenTransfer> tokenTransfers;
    private final Collection<TopicMessage> topicMessages;
    private final Collection<Transaction> transactions;
    private final Collection<TransactionHash> transactionHashes;
    private final Collection<TransactionSignature> transactionSignatures;

    // maps of upgradable domains
    private final Map<ContractState.Id, ContractState> contractStates;
    private final Map<AbstractCryptoAllowance.Id, CryptoAllowance> cryptoAllowanceState;
    private final Map<Long, Entity> entityState;
    private final Map<NftId, Nft> nfts;
    private final Map<AbstractNftAllowance.Id, NftAllowance> nftAllowanceState;
    private final Map<NftTransferId, NftTransfer> nftTransferState;
    private final Map<Long, Schedule> schedules;
    private final Map<Long, Token> tokens;
    private final Map<AbstractTokenAllowance.Id, TokenAllowance> tokenAllowanceState;

    // tracks the state of <token, account> relationships in a batch, the initial state before the batch is in db.
    // for each <token, account> update, merge the state and the update, save the merged state to the batch.
    // during batch upsert, the merged state at time T is again merged with the initial state before the batch to
    // get the full state at time T
    private final Map<AbstractTokenAccount.Id, TokenAccount> tokenAccountState;

    @SuppressWarnings("java:S107")
    public SqlEntityListener(
            BatchPersister batchPersister,
            EntityIdService entityIdService,
            EntityProperties entityProperties,
            ApplicationEventPublisher eventPublisher,
            NftRepository nftRepository,
            RecordFileRepository recordFileRepository,
            SidecarFileRepository sidecarFileRepository,
            SqlProperties sqlProperties,
            @Qualifier(DELETED_TOKEN_DISSOCIATE_BATCH_PERSISTER) BatchPersister tokenDissociateTransferBatchPersister) {
        this.batchPersister = batchPersister;
        this.entityIdService = entityIdService;
        this.entityProperties = entityProperties;
        this.eventPublisher = eventPublisher;
        this.nftRepository = nftRepository;
        this.recordFileRepository = recordFileRepository;
        this.sidecarFileRepository = sidecarFileRepository;
        this.sqlProperties = sqlProperties;
        this.tokenDissociateTransferBatchPersister = tokenDissociateTransferBatchPersister;

        assessedCustomFees = new ArrayList<>();
        contracts = new ArrayList<>();
        contractActions = new ArrayList<>();
        contractLogs = new ArrayList<>();
        contractResults = new ArrayList<>();
        contractStateChanges = new ArrayList<>();
        cryptoAllowances = new ArrayList<>();
        cryptoTransfers = new ArrayList<>();
        customFees = new ArrayList<>();
        deletedTokenDissociateTransfers = new ArrayList<>();
        entities = new ArrayList<>();
        ethereumTransactions = new ArrayList<>();
        fileData = new ArrayList<>();
        liveHashes = new ArrayList<>();
        nftAllowances = new ArrayList<>();
        networkStakes = new ArrayList<>();
        nodeStakes = new ArrayList<>();
        nonFeeTransfers = new ArrayList<>();
        prngs = new ArrayList<>();
        stakingRewardTransfers = new ArrayList<>();
        tokenAccounts = new ArrayList<>();
        tokenAllowances = new ArrayList<>();
        tokenTransfers = new ArrayList<>();
        topicMessages = new ArrayList<>();
        transactions = new ArrayList<>();
        transactionHashes = new ArrayList<>();
        transactionSignatures = new ArrayList<>();

        contractStates = new HashMap<>();
        cryptoAllowanceState = new HashMap<>();
        entityState = new HashMap<>();
        nfts = new HashMap<>();
        nftAllowanceState = new HashMap<>();
        nftTransferState = new HashMap<>();
        schedules = new HashMap<>();
        tokens = new HashMap<>();
        tokenAccountState = new HashMap<>();
        tokenAllowanceState = new HashMap<>();
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
        flush();
        if (recordFile != null) {
            recordFileRepository.save(recordFile);
            sidecarFileRepository.saveAll(recordFile.getSidecars());
        }
    }

    @Override
    public void onError() {
        cleanup();
    }

    @Override
    public void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {
        assessedCustomFees.add(assessedCustomFee);
    }

    @Override
    public void onContract(Contract contract) {
        contracts.add(contract);
    }

    @Override
    public void onContractAction(ContractAction contractAction) {
        contractActions.add(contractAction);
    }

    @Override
    public void onContractLog(ContractLog contractLog) {
        contractLogs.add(contractLog);
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        contractResults.add(contractResult);
    }

    @Override
    public void onContractStateChange(ContractStateChange contractStateChange) {
        contractStateChanges.add(contractStateChange);

        var valueRead = contractStateChange.getValueRead();
        var valueWritten = contractStateChange.getValueWritten();
        if (valueWritten != null || contractStateChange.isMigration()) {
            var value = valueWritten == null ? valueRead : valueWritten;
            var state = new ContractState();
            state.setContractId(contractStateChange.getContractId());
            state.setCreatedTimestamp(contractStateChange.getConsensusTimestamp());
            state.setModifiedTimestamp(contractStateChange.getConsensusTimestamp());
            state.setSlot(contractStateChange.getSlot());
            state.setValue(value);
            contractStates.merge(state.getId(), state, this::mergeContractState);
        }
    }

    @Override
    public void onCryptoAllowance(CryptoAllowance cryptoAllowance) {
        var merged = cryptoAllowanceState.merge(cryptoAllowance.getId(), cryptoAllowance, this::mergeCryptoAllowance);
        cryptoAllowances.add(merged);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        if (entityProperties.getPersist().isTrackBalance()) {
            var entity = new Entity();
            entity.setId(cryptoTransfer.getEntityId());
            entity.setBalance(cryptoTransfer.getAmount());
            onEntity(entity);
        }

        cryptoTransfers.add(cryptoTransfer);
    }

    @Override
    public void onCustomFee(CustomFee customFee) throws ImporterException {
        customFees.add(customFee);
    }

    @Override
    public void onEntity(Entity entity) throws ImporterException {
        long id = entity.getId();
        if (id == EntityId.EMPTY.getId()) {
            return;
        }

        Entity merged = entityState.merge(entity.getId(), entity, this::mergeEntity);
        if (merged == entity) {
            // only add the merged object to the collection if the state is replaced with the new entity object, i.e.,
            // attributes only in the previous state are merged into the new entity object
            entities.add(entity);
        }
        entityIdService.notify(entity);
    }

    @Override
    public void onEthereumTransaction(EthereumTransaction ethereumTransaction) throws ImporterException {
        ethereumTransactions.add(ethereumTransaction);
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
    public void onNetworkStake(NetworkStake networkStake) throws ImporterException {
        networkStakes.add(networkStake);
    }

    @Override
    public void onNft(Nft nft) throws ImporterException {
        nfts.merge(nft.getId(), nft, this::mergeNft);
    }

    @Override
    public void onNftAllowance(NftAllowance nftAllowance) {
        var merged = nftAllowanceState.merge(nftAllowance.getId(), nftAllowance, this::mergeNftAllowance);
        nftAllowances.add(merged);
    }

    @Override
    @SuppressWarnings({"java:S2259"})
    // If nftTransferId is null, this will throw an NPE.  That behavior is correct, for that case.
    public void onNftTransfer(NftTransfer nftTransfer) throws ImporterException {
        var nftTransferId = nftTransfer.getId();
        long tokenId = nftTransferId.getTokenId().getId();
        if (nftTransferId.getSerialNumber() == NftTransferId.WILDCARD_SERIAL_NUMBER) {
            flushNftState();

            long payerAccountId = nftTransfer.getPayerAccountId().getId();
            var newTreasury = nftTransfer.getReceiverAccountId();
            var previousTreasury = nftTransfer.getSenderAccountId();

            nftRepository.updateTreasury(
                    tokenId,
                    previousTreasury.getId(),
                    newTreasury.getId(),
                    nftTransferId.getConsensusTimestamp(),
                    payerAccountId,
                    nftTransfer.getIsApproval());
            return;
        }

        if (entityProperties.getPersist().isTrackBalance()) {
            if (nftTransfer.getSenderAccountId() != EntityId.EMPTY) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getSenderAccountId().getId());
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(-1);
                onTokenAccount(tokenAccount);
            }

            if (nftTransfer.getReceiverAccountId() != EntityId.EMPTY) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getReceiverAccountId().getId());
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(1);
                onTokenAccount(tokenAccount);
            }
        }

        nftTransferState.merge(nftTransferId, nftTransfer, this::mergeNftTransfer);
    }

    @Override
    public void onNodeStake(NodeStake nodeStake) {
        nodeStakes.add(nodeStake);
    }

    @Override
    public void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
        nonFeeTransfers.add(nonFeeTransfer);
    }

    @Override
    public void onPrng(Prng prng) {
        prngs.add(prng);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        // schedules could experience multiple updates in a single record file, handle updates in memory for this case
        schedules.merge(schedule.getScheduleId(), schedule, this::mergeSchedule);
    }

    @Override
    public void onStakingRewardTransfer(StakingRewardTransfer stakingRewardTransfer) {
        stakingRewardTransfers.add(stakingRewardTransfer);

        long consensusTimestamp = stakingRewardTransfer.getConsensusTimestamp();
        var current = entityState.get(stakingRewardTransfer.getAccountId());
        if (current == null
                || (current.getTimestampLower() != null && current.getTimestampLower() != consensusTimestamp)
                || current.getStakePeriodStart() == null) {
            // Set the stake period start when the entity is not in the state, or the current lower timestamp is
            // different from the staking reward transfer's consensus timestamp, or the current stake period start is
            // not set. Note the stake period start in all the cases is set to today - 1, so that when today ends, the
            // account / contract will earn staking reward for today
            long stakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
            if (current != null) {
                current.setStakePeriodStart(stakePeriodStart);
                return;
            }

            // Create a non-history entity update when there's no such entity in state
            var entity = Entity.builder()
                    .id(stakingRewardTransfer.getAccountId())
                    .stakePeriodStart(stakePeriodStart)
                    .build();
            onEntity(entity);
        }
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        // tokens could experience multiple updates in a single record file, handle updates in memory for this case
        tokens.merge(token.getTokenId().getTokenId().getId(), token, this::mergeToken);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        var merged = tokenAccountState.merge(tokenAccount.getId(), tokenAccount, this::mergeTokenAccount);
        if (merged == tokenAccount) {
            tokenAccounts.add(merged);
        }
    }

    @Override
    public void onTokenAllowance(TokenAllowance tokenAllowance) {
        TokenAllowance merged =
                tokenAllowanceState.merge(tokenAllowance.getId(), tokenAllowance, this::mergeTokenAllowance);
        tokenAllowances.add(merged);
    }

    @Override
    @SuppressWarnings("java:S2259")
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        if (entityProperties.getPersist().isTrackBalance()) {
            var tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(tokenTransfer.getId().getAccountId().getId());
            tokenAccount.setTokenId(tokenTransfer.getId().getTokenId().getId());
            tokenAccount.setBalance(tokenTransfer.getAmount());
            onTokenAccount(tokenAccount);
        }

        if (tokenTransfer.isDeletedTokenDissociate()) {
            deletedTokenDissociateTransfers.add(tokenTransfer);
            return;
        }

        tokenTransfers.add(tokenTransfer);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        topicMessages.add(topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        transactions.add(transaction);

        if (entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.of(transaction.getType()))) {
            transactionHashes.add(transaction.toTransactionHash());
        }

        if (transactions.size() == sqlProperties.getBatchSize()) {
            flush();
        }
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        transactionSignatures.add(transactionSignature);
    }

    private void cleanup() {
        try {
            assessedCustomFees.clear();
            contracts.clear();
            contractActions.clear();
            contractLogs.clear();
            contractResults.clear();
            contractStateChanges.clear();
            contractStates.clear();
            cryptoAllowances.clear();
            cryptoAllowanceState.clear();
            cryptoTransfers.clear();
            customFees.clear();
            entities.clear();
            entityState.clear();
            ethereumTransactions.clear();
            fileData.clear();
            liveHashes.clear();
            networkStakes.clear();
            nfts.clear();
            nftAllowances.clear();
            nftAllowanceState.clear();
            nftTransferState.clear();
            nodeStakes.clear();
            nonFeeTransfers.clear();
            prngs.clear();
            schedules.clear();
            stakingRewardTransfers.clear();
            topicMessages.clear();
            tokenAccounts.clear();
            tokenAccountState.clear();
            tokenAllowances.clear();
            tokenAllowanceState.clear();
            tokens.clear();
            deletedTokenDissociateTransfers.clear();
            tokenTransfers.clear();
            transactions.clear();
            transactionHashes.clear();
            transactionSignatures.clear();
            eventPublisher.publishEvent(new EntityBatchCleanupEvent(this));
        } catch (BeanCreationNotAllowedException e) {
            // This error can occur during shutdown
        }
    }

    private void flush() {
        try {
            // batch save action may run asynchronously, triggering it before other operations can reduce latency
            eventPublisher.publishEvent(new EntityBatchSaveEvent(this));

            Stopwatch stopwatch = Stopwatch.createStarted();

            // insert only operations
            batchPersister.persist(assessedCustomFees);
            batchPersister.persist(contractActions);
            batchPersister.persist(contractLogs);
            batchPersister.persist(contractResults);
            batchPersister.persist(contractStateChanges);
            batchPersister.persist(cryptoTransfers);
            batchPersister.persist(customFees);
            batchPersister.persist(ethereumTransactions);
            batchPersister.persist(fileData);
            batchPersister.persist(liveHashes);
            batchPersister.persist(networkStakes);
            batchPersister.persist(nodeStakes);
            batchPersister.persist(prngs);
            batchPersister.persist(topicMessages);
            batchPersister.persist(transactions);
            batchPersister.persist(transactionHashes);
            batchPersister.persist(transactionSignatures);

            // insert operations with conflict management
            batchPersister.persist(contracts);
            batchPersister.persist(contractStates.values());
            batchPersister.persist(cryptoAllowances);
            batchPersister.persist(entities);
            batchPersister.persist(nftAllowances);
            batchPersister.persist(tokens.values());
            // ingest tokenAccounts after tokens since some fields of token accounts depends on the associated token
            batchPersister.persist(tokenAccounts);
            batchPersister.persist(tokenAllowances);
            batchPersister.persist(nfts.values()); // persist nft after token entity
            batchPersister.persist(schedules.values());

            // transfers operations should be last to ensure insert logic completeness, entities should already exist
            batchPersister.persist(nonFeeTransfers);
            batchPersister.persist(nftTransferState.values());
            batchPersister.persist(stakingRewardTransfers);
            batchPersister.persist(tokenTransfers);

            // handle the transfers from token dissociate transactions after nft is processed
            tokenDissociateTransferBatchPersister.persist(deletedTokenDissociateTransfers);

            log.info("Completed batch inserts in {}", stopwatch);
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        } finally {
            cleanup();
        }
    }

    private void flushNftState() {
        try {
            // flush tables required for an accurate nft state in database to ensure correct state-dependent changes
            batchPersister.persist(tokens.values());
            batchPersister.persist(tokenAccounts);
            batchPersister.persist(nfts.values());
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        } finally {
            tokens.clear();
            tokenAccounts.clear();
            nfts.clear();
        }
    }

    private ContractState mergeContractState(ContractState previous, ContractState current) {
        previous.setValue(current.getValue());
        previous.setModifiedTimestamp(current.getModifiedTimestamp());
        return previous;
    }

    private CryptoAllowance mergeCryptoAllowance(CryptoAllowance previous, CryptoAllowance current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    @SuppressWarnings("java:S3776")
    private Entity mergeEntity(Entity previous, Entity current) {
        // This entity should not trigger a history record, so just copy common non-history fields, if set, to previous
        if (!current.isHistory()) {
            previous.addBalance(current.getBalance());

            if (current.getEthereumNonce() != null) {
                previous.setEthereumNonce(current.getEthereumNonce());
            }

            if (current.getStakePeriodStart() != null) {
                previous.setStakePeriodStart(current.getStakePeriodStart());
            }

            return previous;
        }

        // If previous doesn't have history, merge reversely from current to previous
        var src = previous.isHistory() ? previous : current;
        var dest = previous.isHistory() ? current : previous;

        // Copy non-updatable fields from src
        dest.setAlias(src.getAlias());
        dest.setCreatedTimestamp(src.getCreatedTimestamp());
        dest.setEvmAddress(src.getEvmAddress());

        if (dest.getAutoRenewPeriod() == null) {
            dest.setAutoRenewPeriod(src.getAutoRenewPeriod());
        }

        if (dest.getAutoRenewAccountId() == null) {
            dest.setAutoRenewAccountId(src.getAutoRenewAccountId());
        }

        dest.addBalance(src.getBalance());

        if (dest.getDeclineReward() == null) {
            dest.setDeclineReward(src.getDeclineReward());
        }

        if (dest.getDeleted() == null) {
            dest.setDeleted(src.getDeleted());
        }

        if (dest.getEthereumNonce() == null) {
            dest.setEthereumNonce(src.getEthereumNonce());
        }

        if (dest.getExpirationTimestamp() == null) {
            dest.setExpirationTimestamp(src.getExpirationTimestamp());
        }

        if (dest.getKey() == null) {
            dest.setKey(src.getKey());
        }

        if (dest.getMaxAutomaticTokenAssociations() == null) {
            dest.setMaxAutomaticTokenAssociations(src.getMaxAutomaticTokenAssociations());
        }

        if (dest.getMemo() == null) {
            dest.setMemo(src.getMemo());
        }

        if (dest.getObtainerId() == null) {
            dest.setObtainerId(src.getObtainerId());
        }

        if (dest.getPermanentRemoval() == null) {
            dest.setPermanentRemoval(src.getPermanentRemoval());
        }

        if (dest.getProxyAccountId() == null) {
            dest.setProxyAccountId(src.getProxyAccountId());
        }

        if (dest.getReceiverSigRequired() == null) {
            dest.setReceiverSigRequired(src.getReceiverSigRequired());
        }

        if (dest.getStakedAccountId() == null) {
            dest.setStakedAccountId(src.getStakedAccountId());
        }

        if (dest.getStakedNodeId() == null) {
            dest.setStakedNodeId(src.getStakedNodeId());
        }

        if (dest.getStakePeriodStart() == null) {
            dest.setStakePeriodStart(src.getStakePeriodStart());
        }

        if (dest.getSubmitKey() == null) {
            dest.setSubmitKey(src.getSubmitKey());
        }

        // There is at least one entity with history. If there is one without history, it must be dest and copy non-null
        // fields and timestamp range from src to dest. Otherwise, both have history, and it's a normal merge from
        // previous to current, so close the src entity's timestamp range
        if (!dest.isHistory()) {
            dest.setNum(src.getNum());
            dest.setRealm(src.getRealm());
            dest.setShard(src.getShard());
            dest.setTimestampRange(src.getTimestampRange());
            // It's important to set the type since some non-history updates may have incorrect entity type.
            // For example, when a contract is created in a child transaction, the initial transfer to the contract may
            // be externalized in the parent transaction record with an earlier consensus timestamp, so the non-history
            // entity is created from the crypto transfer then an entity with correct type is created from the contract
            // create child transaction
            dest.setType(src.getType());
        } else {
            src.setTimestampUpper(dest.getTimestampLower());
        }

        return dest;
    }

    private Nft mergeNft(Nft cachedNft, Nft newNft) {
        if (newNft.getAccountId() != null) { // only domains generated by NftTransfers should set account
            cachedNft.setAccountId(newNft.getAccountId());
        }

        if (cachedNft.getCreatedTimestamp() == null && newNft.getCreatedTimestamp() != null) {
            cachedNft.setCreatedTimestamp(newNft.getCreatedTimestamp());
        }

        if (newNft.getDeleted() != null) {
            cachedNft.setDeleted(newNft.getDeleted());
        }

        if (newNft.getMetadata() != null) {
            cachedNft.setMetadata(newNft.getMetadata());
        }

        cachedNft.setModifiedTimestamp(newNft.getModifiedTimestamp());

        // copy allowance related fields
        cachedNft.setDelegatingSpender(newNft.getDelegatingSpender());
        cachedNft.setSpender(newNft.getSpender());

        return cachedNft;
    }

    private NftTransfer mergeNftTransfer(NftTransfer cachedNftTransfer, NftTransfer newNftTransfer) {
        // flatten multi receiver transfers
        if (!Objects.equals(cachedNftTransfer.getReceiverAccountId(), newNftTransfer.getReceiverAccountId())) {
            cachedNftTransfer.setReceiverAccountId(newNftTransfer.getReceiverAccountId());
        }

        return cachedNftTransfer;
    }

    private NftAllowance mergeNftAllowance(NftAllowance previous, NftAllowance current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    private Schedule mergeSchedule(Schedule cachedSchedule, Schedule schedule) {
        cachedSchedule.setExecutedTimestamp(schedule.getExecutedTimestamp());
        return cachedSchedule;
    }

    private Token mergeToken(Token cachedToken, Token newToken) {
        if (newToken.getFreezeKey() != null) {
            cachedToken.setFreezeKey(newToken.getFreezeKey());
        }

        if (newToken.getKycKey() != null) {
            cachedToken.setKycKey(newToken.getKycKey());
        }

        if (newToken.getName() != null) {
            cachedToken.setName(newToken.getName());
        }

        if (newToken.getPauseKey() != null) {
            cachedToken.setPauseKey(newToken.getPauseKey());
        }

        if (newToken.getPauseStatus() != null) {
            cachedToken.setPauseStatus(newToken.getPauseStatus());
        }

        if (newToken.getSupplyKey() != null) {
            cachedToken.setSupplyKey(newToken.getSupplyKey());
        }

        if (newToken.getSymbol() != null) {
            cachedToken.setSymbol(newToken.getSymbol());
        }

        if (newToken.getTotalSupply() != null) {
            Long newTotalSupply = newToken.getTotalSupply();
            if (cachedToken.getTotalSupply() != null && newTotalSupply < 0) {
                // if the cached token has total supply set, and the new total supply is negative because it's an update
                // from the token transfer of a token dissociate of a deleted token, aggregate the change
                cachedToken.setTotalSupply(cachedToken.getTotalSupply() + newTotalSupply);
            } else {
                // if the cached token doesn't have total supply or the new total supply is non-negative, set it to the
                // new token's total supply. Later step should apply the change on the current total supply in db if
                // the value is negative.
                cachedToken.setTotalSupply(newToken.getTotalSupply());
            }
        }

        if (newToken.getTreasuryAccountId() != null) {
            cachedToken.setTreasuryAccountId(newToken.getTreasuryAccountId());
        }

        if (newToken.getWipeKey() != null) {
            cachedToken.setWipeKey(newToken.getWipeKey());
        }

        cachedToken.setModifiedTimestamp(newToken.getModifiedTimestamp());
        return cachedToken;
    }

    private TokenAccount mergeTokenAccount(TokenAccount lastTokenAccount, TokenAccount newTokenAccount) {
        if (!lastTokenAccount.isHistory()) {
            if (!newTokenAccount.isHistory()) {
                lastTokenAccount.setBalance(newTokenAccount.getBalance() + lastTokenAccount.getBalance());
            } else {
                lastTokenAccount.setAutomaticAssociation(newTokenAccount.getAutomaticAssociation());
                lastTokenAccount.setAssociated(newTokenAccount.getAssociated());
                lastTokenAccount.setCreatedTimestamp(newTokenAccount.getCreatedTimestamp());
                lastTokenAccount.setFreezeStatus(newTokenAccount.getFreezeStatus());
                lastTokenAccount.setKycStatus(newTokenAccount.getKycStatus());
                lastTokenAccount.setTimestampRange(newTokenAccount.getTimestampRange());
            }

            return lastTokenAccount;
        }

        if (lastTokenAccount.isHistory() && !newTokenAccount.isHistory()) {
            lastTokenAccount.setBalance(newTokenAccount.getBalance() + lastTokenAccount.getBalance());
            return lastTokenAccount;
        }

        if (lastTokenAccount.getTimestampRange().equals(newTokenAccount.getTimestampRange())) {
            // The token accounts are for the same range, accept the previous one
            // This is a workaround for https://github.com/hashgraph/hedera-services/issues/3240
            log.warn("Skipping duplicate token account association: {}", newTokenAccount);
            return lastTokenAccount;
        }

        lastTokenAccount.setTimestampUpper(newTokenAccount.getTimestampLower());

        if (newTokenAccount.getCreatedTimestamp() != null) {
            return newTokenAccount;
        }

        // newTokenAccount is a partial update. It must have its id (tokenId, accountId) set.
        // copy the lifespan immutable fields createdTimestamp and automaticAssociation from the previous snapshot.
        // copy other fields from the previous snapshot if not set in newTokenAccount
        newTokenAccount.setCreatedTimestamp(lastTokenAccount.getCreatedTimestamp());
        newTokenAccount.setBalance(lastTokenAccount.getBalance());
        newTokenAccount.setAutomaticAssociation(lastTokenAccount.getAutomaticAssociation());

        if (newTokenAccount.getAssociated() == null) {
            newTokenAccount.setAssociated(lastTokenAccount.getAssociated());
        }

        if (newTokenAccount.getFreezeStatus() == null) {
            newTokenAccount.setFreezeStatus(lastTokenAccount.getFreezeStatus());
        }

        if (newTokenAccount.getKycStatus() == null) {
            newTokenAccount.setKycStatus(lastTokenAccount.getKycStatus());
        }

        return newTokenAccount;
    }

    private TokenAllowance mergeTokenAllowance(TokenAllowance previous, TokenAllowance current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }
}
