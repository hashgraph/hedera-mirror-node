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
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.FungibleAllowance;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
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
import lombok.CustomLog;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;

@CustomLog
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
    private final Collection<EntityTransaction> entityTransactions;
    private final Collection<EthereumTransaction> ethereumTransactions;
    private final Collection<FileData> fileData;
    private final Collection<LiveHash> liveHashes;
    private final Collection<NetworkFreeze> networkFreezes;
    private final Collection<NetworkStake> networkStakes;
    private final Collection<NftAllowance> nftAllowances;
    private final Collection<Nft> nfts;
    private final Collection<NodeStake> nodeStakes;
    private final Collection<Prng> prngs;
    private final Collection<StakingRewardTransfer> stakingRewardTransfers;
    private final Collection<TokenAccount> tokenAccounts;
    private final Collection<TokenAllowance> tokenAllowances;
    private final Collection<Token> tokens;
    private final Collection<TokenTransfer> tokenTransfers;
    private final Collection<TopicMessage> topicMessages;
    private final Collection<Transaction> transactions;
    private final Collection<TransactionHash> transactionHashes;
    private final Collection<TransactionSignature> transactionSignatures;

    // maps of upgradable domains
    private final Map<ContractState.Id, ContractState> contractStates;
    private final Map<AbstractCryptoAllowance.Id, CryptoAllowance> cryptoAllowanceState;
    private final Map<Long, Entity> entityState;
    private final Map<AbstractNft.Id, Nft> nftState;
    private final Map<AbstractNftAllowance.Id, NftAllowance> nftAllowanceState;
    private final Map<Long, Schedule> schedules;
    private final Map<Long, Token> tokenState;
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
        entityTransactions = new ArrayList<>();
        ethereumTransactions = new ArrayList<>();
        fileData = new ArrayList<>();
        liveHashes = new ArrayList<>();
        networkFreezes = new ArrayList<>();
        networkStakes = new ArrayList<>();
        nftAllowances = new ArrayList<>();
        nfts = new ArrayList<>();
        nodeStakes = new ArrayList<>();
        prngs = new ArrayList<>();
        stakingRewardTransfers = new ArrayList<>();
        tokenAccounts = new ArrayList<>();
        tokenAllowances = new ArrayList<>();
        tokens = new ArrayList<>();
        tokenTransfers = new ArrayList<>();
        topicMessages = new ArrayList<>();
        transactions = new ArrayList<>();
        transactionHashes = new ArrayList<>();
        transactionSignatures = new ArrayList<>();

        contractStates = new HashMap<>();
        cryptoAllowanceState = new HashMap<>();
        entityState = new HashMap<>();
        nftState = new HashMap<>();
        nftAllowanceState = new HashMap<>();
        schedules = new HashMap<>();
        tokenState = new HashMap<>();
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
            var sidecars = recordFile.getSidecars();
            recordFileRepository.save(recordFile);

            if (!sidecars.isEmpty()) {
                sidecarFileRepository.saveAll(sidecars);
                log.info("Processed {} sidecars", sidecars.size());
            }
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
        var merged = cryptoAllowanceState.merge(cryptoAllowance.getId(), cryptoAllowance, this::mergeFungibleAllowance);
        if (merged == cryptoAllowance) {
            // Only add the merged object to the collection if it is a crypto allowance grant rather than
            // just a debit to an existing grant.
            cryptoAllowances.add(merged);
        }
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
    public void onEntityTransactions(Collection<EntityTransaction> entityTransactions) throws ImporterException {
        this.entityTransactions.addAll(entityTransactions);
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
    public void onNetworkFreeze(NetworkFreeze networkFreeze) {
        networkFreezes.add(networkFreeze);
    }

    @Override
    public void onNetworkStake(NetworkStake networkStake) throws ImporterException {
        networkStakes.add(networkStake);
    }

    @Override
    public void onNft(Nft nft) throws ImporterException {
        var merged = nftState.merge(nft.getId(), nft, this::mergeNft);
        if (merged == nft) {
            // only add the merged object to the collection if the state is replaced with the new nft object, i.e.,
            // attributes only in the previous state are merged into the new nft object
            nfts.add(nft);
        }
    }

    @Override
    public void onNftAllowance(NftAllowance nftAllowance) {
        var merged = nftAllowanceState.merge(nftAllowance.getId(), nftAllowance, this::mergeNftAllowance);
        nftAllowances.add(merged);
    }

    @Override
    public void onNodeStake(NodeStake nodeStake) {
        nodeStakes.add(nodeStake);
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

        var current = entityState.get(stakingRewardTransfer.getConsensusTimestamp());
        long consensusTimestamp = stakingRewardTransfer.getConsensusTimestamp();
        // The new stake period start is set to today - 1, so that when today ends, the account / contract will earn
        // staking reward for today
        long stakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
        if (current == null
                || current.getStakePeriodStart() == null
                || current.getStakePeriodStart() < stakePeriodStart) {
            // Set the stake period start when any of the following is true
            // 1. The entity is not in the state
            // 2. The current stake period start is not set
            // 3. The current stake period start is before the new stake period start as result of the reward payout
            // Note condition 3 handles the edge case that the staking reward transfer is triggered by an entity update
            // transaction with entity staking changes so the stake period start should be today, not today - 1
            var entity = EntityId.of(stakingRewardTransfer.getAccountId(), EntityType.ACCOUNT)
                    .toEntity();
            entity.setStakePeriodStart(stakePeriodStart);
            entity.setTimestampLower(consensusTimestamp);
            entity.setType(null); // Clear the type since it's uncertain if the entity is ACCOUNT or CONTRACT
            onEntity(entity);
        }
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        var merged = tokenState.merge(token.getTokenId(), token, this::mergeToken);
        tokens.add(merged);
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
        var merged = tokenAllowanceState.merge(tokenAllowance.getId(), tokenAllowance, this::mergeFungibleAllowance);
        // Only add the merged object to the collection if it is a token allowance grant rather than
        // just a debit to an existing grant.
        if (merged == tokenAllowance) {
            tokenAllowances.add(merged);
        }
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

        onNftTransferList(transaction);

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
            entityTransactions.clear();
            ethereumTransactions.clear();
            fileData.clear();
            liveHashes.clear();
            networkFreezes.clear();
            networkStakes.clear();
            nftState.clear();
            nfts.clear();
            nftAllowances.clear();
            nftAllowanceState.clear();
            nodeStakes.clear();
            prngs.clear();
            schedules.clear();
            stakingRewardTransfers.clear();
            topicMessages.clear();
            tokenAccounts.clear();
            tokenAccountState.clear();
            tokenAllowances.clear();
            tokenAllowanceState.clear();
            tokens.clear();
            tokenState.clear();
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
            batchPersister.persist(entityTransactions);
            batchPersister.persist(ethereumTransactions);
            batchPersister.persist(fileData);
            batchPersister.persist(liveHashes);
            batchPersister.persist(networkFreezes);
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
            batchPersister.persist(tokens);
            // ingest tokenAccounts after tokens since some fields of token accounts depends on the associated token
            batchPersister.persist(tokenAccounts);
            batchPersister.persist(tokenAllowances);
            batchPersister.persist(nfts); // persist nft after token entity
            batchPersister.persist(schedules.values());

            // transfers operations should be last to ensure insert logic completeness, entities should already exist
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
            batchPersister.persist(tokens);
            batchPersister.persist(tokenAccounts);
            batchPersister.persist(nfts);
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        } finally {
            tokens.clear();
            tokenAccounts.clear();
            tokenState.clear();
            nftState.clear();
            nfts.clear();
        }
    }

    private ContractState mergeContractState(ContractState previous, ContractState current) {
        previous.setValue(current.getValue());
        previous.setModifiedTimestamp(current.getModifiedTimestamp());
        return previous;
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

        boolean isSameTimestampLower = Objects.equals(current.getTimestampLower(), previous.getTimestampLower());
        if (current.isHistory() && isSameTimestampLower) {
            // Copy from current to previous if the updates have the same lower timestamp (thus from same transaction)
            src = current;
            dest = previous;
        }

        dest.setCreatedTimestamp(src.getCreatedTimestamp());

        if (dest.getAlias() == null) {
            dest.setAlias(src.getAlias());
        }

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

        if (dest.getEvmAddress() == null) {
            dest.setEvmAddress(src.getEvmAddress());
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

        if (dest.getNum() == null) {
            dest.setNum(src.getNum());
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

        if (dest.getRealm() == null) {
            dest.setRealm(src.getRealm());
        }

        if (dest.getShard() == null) {
            dest.setShard(src.getShard());
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

        if (dest.getType() == null) {
            dest.setType(src.getType());
        }

        // There is at least one entity with history. If there is one without history, it must be dest and copy non-null
        // fields and timestamp range from src to dest. Otherwise, both have history, and it's a normal merge from
        // previous to current, so close the src entity's timestamp range
        if (!dest.isHistory()) {
            dest.setTimestampRange(src.getTimestampRange());
            // It's important to set the type since some non-history updates may have incorrect entity type.
            // For example, when a contract is created in a child transaction, the initial transfer to the contract may
            // be externalized in the parent transaction record with an earlier consensus timestamp, so the non-history
            // entity is created from the crypto transfer then an entity with correct type is created from the contract
            // create child transaction
            dest.setType(src.getType());
        } else if (!isSameTimestampLower) {
            src.setTimestampUpper(dest.getTimestampLower());
        }

        return dest;
    }

    private <T extends FungibleAllowance> T mergeFungibleAllowance(T previous, T current) {
        if (current.isHistory()) {
            // Current is an allowance grant / revoke so close the previous timestamp range
            previous.setTimestampUpper(current.getTimestampLower());
            return current;
        }

        // Current must be an approved transfer and previous can be either so should accumulate the amounts regardless.
        previous.setAmount(previous.getAmount() + current.getAmount());
        return previous;
    }

    private Nft mergeNft(Nft cachedNft, Nft newNft) {
        var dest = newNft;
        var src = cachedNft;
        if (Objects.equals(cachedNft.getTimestampLower(), newNft.getTimestampLower())) {
            // Two NFT updates for the same nft can have the same lower timestamp in 3 cases
            // - token mint, the NFT object built in the transaction handler has everything but accountId, the NFT
            //   object built in EntityRecordItemListener has accountId for ownership transfer
            // - duplicate NFT objects due to possible duplicate nft transfers in record
            // - same NFT with multiple transfers in record
            // We should merge updates from newNft to cachedNft instead
            if (newNft.getAccountId() != null) {
                // If the same nft is transferred from Alice to Bob, and from Bob to Carol at the same time, set the
                // owner account to Carol
                cachedNft.setAccountId(newNft.getAccountId());
            }

            dest = cachedNft;
            src = newNft;
        }

        // Never merge delegatingSpender and spender since any change to the same nft afterward should clear them
        if (dest.getAccountId() == null) {
            dest.setAccountId(src.getAccountId());
        }

        if (dest.getCreatedTimestamp() == null) {
            dest.setCreatedTimestamp(src.getCreatedTimestamp());
        }

        if (dest.getDeleted() == null) {
            dest.setDeleted(src.getDeleted());
        }

        if (dest.getMetadata() == null) {
            dest.setMetadata(src.getMetadata());
        }

        if (dest.getTimestampLower() > src.getTimestampLower()) {
            // Only close the source NFT timestamp range when the dest timestamp is after the src
            src.setTimestampUpper(dest.getTimestampLower());
        }

        return dest;
    }

    private NftAllowance mergeNftAllowance(NftAllowance previous, NftAllowance current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    private Schedule mergeSchedule(Schedule cachedSchedule, Schedule schedule) {
        cachedSchedule.setExecutedTimestamp(schedule.getExecutedTimestamp());
        return cachedSchedule;
    }

    /**
     * Merges two token objects into one. The previous may or not be an initial create with all fields while the current
     * will always be a partial update. Copy immutable fields from the previous to the current and close the previous'
     * timestamp range. Copy other fields from the previous if not set in current.
     *
     * @param previous token to merge into the current
     * @param current  token
     * @return the merged token
     */
    private Token mergeToken(Token previous, Token current) {
        previous.setTimestampUpper(current.getTimestampLower());

        current.setCreatedTimestamp(previous.getCreatedTimestamp());
        current.setDecimals(previous.getDecimals());
        current.setFreezeDefault(previous.getFreezeDefault());
        current.setInitialSupply(previous.getInitialSupply());
        current.setMaxSupply(previous.getMaxSupply());
        current.setSupplyType(previous.getSupplyType());
        current.setType(previous.getType());

        if (current.getFeeScheduleKey() == null) {
            current.setFeeScheduleKey(previous.getFeeScheduleKey());
        }

        if (current.getFreezeKey() == null) {
            current.setFreezeKey(previous.getFreezeKey());
        }

        if (current.getKycKey() == null) {
            current.setKycKey(previous.getKycKey());
        }

        if (current.getName() == null) {
            current.setName(previous.getName());
        }

        if (current.getPauseKey() == null) {
            current.setPauseKey(previous.getPauseKey());
        }

        if (current.getPauseStatus() == null) {
            current.setPauseStatus(previous.getPauseStatus());
        }

        if (current.getSupplyKey() == null) {
            current.setSupplyKey(previous.getSupplyKey());
        }

        if (current.getSymbol() == null) {
            current.setSymbol(previous.getSymbol());
        }

        if (current.getTreasuryAccountId() == null) {
            current.setTreasuryAccountId(previous.getTreasuryAccountId());
        }

        if (current.getWipeKey() == null) {
            current.setWipeKey(previous.getWipeKey());
        }

        Long currentTotalSupply = current.getTotalSupply();
        Long previousTotalSupply = previous.getTotalSupply();

        if (currentTotalSupply == null) {
            current.setTotalSupply(previousTotalSupply);
        } else if (previousTotalSupply != null && currentTotalSupply < 0) {
            // Negative from a token transfer of a token dissociate of a deleted token, so we aggregate the change.
            current.setTotalSupply(previousTotalSupply + currentTotalSupply);
        }

        return current;
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

    private void onNftTransferList(Transaction transaction) {
        var nftTransferList = transaction.getNftTransfer();
        if (CollectionUtils.isEmpty(nftTransferList)) {
            return;
        }

        for (var nftTransfer : nftTransferList) {
            long tokenId = nftTransfer.getTokenId().getId();
            if (nftTransfer.getSerialNumber() == NftTransfer.WILDCARD_SERIAL_NUMBER) {
                // nft treasury change, there should be only one such nft transfer in the list
                flushNftState();
                nftRepository.updateTreasury(
                        transaction.getConsensusTimestamp(),
                        nftTransfer.getReceiverAccountId().getId(),
                        nftTransfer.getSenderAccountId().getId(),
                        nftTransfer.getTokenId().getId());
                return;
            }

            if (!entityProperties.getPersist().isTrackBalance()) {
                return;
            }

            if (!EntityId.isEmpty(nftTransfer.getSenderAccountId())) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getSenderAccountId().getId());
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(-1);
                onTokenAccount(tokenAccount);
            }

            if (!EntityId.isEmpty(nftTransfer.getReceiverAccountId())) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getReceiverAccountId().getId());
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(1);
                onTokenAccount(tokenAccount);
            }
        }
    }
}
