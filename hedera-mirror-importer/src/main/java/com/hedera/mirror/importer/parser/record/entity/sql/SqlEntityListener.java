/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.contract.ContractTransaction;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.FungibleAllowance;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
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
import com.hedera.mirror.importer.parser.record.entity.ParserContext;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.SidecarFileRepository;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;

@CustomLog
@Named
@Order(0)
@ConditionOnEntityRecordParser
@RequiredArgsConstructor
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    private static final List<Class<?>> NFT_FLUSH = List.of(Token.class, TokenAccount.class, Nft.class);

    private final BatchPersister batchPersister;
    private final EntityIdService entityIdService;
    private final EntityProperties entityProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final NftRepository nftRepository;
    private final RecordFileRepository recordFileRepository;
    private final SidecarFileRepository sidecarFileRepository;
    private final SqlProperties sqlProperties;

    private final ParserContext context = new ParserContext();

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
        context.add(assessedCustomFee);
    }

    @Override
    public void onContract(Contract contract) {
        context.add(contract);
    }

    @Override
    public void onContractAction(ContractAction contractAction) {
        context.add(contractAction);
    }

    @Override
    public void onContractLog(ContractLog contractLog) {
        context.add(contractLog);
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        context.add(contractResult);
        if (entityProperties.getPersist().isContractTransactionHash()) {
            context.add(contractResult.toContractTransactionHash());
        }
    }

    @Override
    public void onContractStateChange(ContractStateChange contractStateChange) {
        context.add(contractStateChange);

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
            context.merge(state.getId(), state, this::mergeContractState);
        }
    }

    @Override
    public void onContractTransactions(Collection<ContractTransaction> contractTransactions) {
        if (entityProperties.getPersist().isContractTransaction()) {
            context.addAll(contractTransactions);
        }
    }

    @Override
    public void onCryptoAllowance(CryptoAllowance cryptoAllowance) {
        context.merge(cryptoAllowance.getId(), cryptoAllowance, this::mergeFungibleAllowance);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        if (entityProperties.getPersist().isTrackBalance()) {
            var entity = new Entity();
            entity.setId(cryptoTransfer.getEntityId());
            entity.setBalance(cryptoTransfer.getAmount());
            entity.setBalanceTimestamp(cryptoTransfer.getConsensusTimestamp());
            onEntity(entity);
        }

        context.add(cryptoTransfer);
    }

    @Override
    public void onCustomFee(CustomFee customFee) throws ImporterException {
        context.merge(customFee.getTokenId(), customFee, this::mergeCustomFee);
    }

    @Override
    public void onEntity(Entity entity) throws ImporterException {
        long id = entity.getId();
        if (id == EntityId.EMPTY.getId()) {
            return;
        }

        if (entity.hasHistory()
                && entity.getCreatedTimestamp() == null
                && !entityProperties.getPersist().isEntityHistory()) {
            return;
        }

        context.merge(id, entity, this::mergeEntity);
        entityIdService.notify(entity);
    }

    @Override
    public void onEntityTransactions(Collection<EntityTransaction> entityTransactions) throws ImporterException {
        context.addAll(entityTransactions);
    }

    @Override
    public void onEthereumTransaction(EthereumTransaction ethereumTransaction) throws ImporterException {
        context.add(ethereumTransaction);
    }

    @Override
    public void onFileData(FileData fileData) {
        context.add(fileData);
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        context.add(liveHash);
    }

    @Override
    public void onNetworkFreeze(NetworkFreeze networkFreeze) {
        context.add(networkFreeze);
    }

    @Override
    public void onNetworkStake(NetworkStake networkStake) throws ImporterException {
        context.add(networkStake);
    }

    @Override
    public void onNft(Nft nft) throws ImporterException {
        context.merge(nft.getId(), nft, this::mergeNft);
    }

    @Override
    public void onNftAllowance(NftAllowance nftAllowance) {
        context.merge(nftAllowance.getId(), nftAllowance, this::mergeNftAllowance);
    }

    @Override
    public void onNodeStake(NodeStake nodeStake) {
        context.add(nodeStake);
    }

    @Override
    public void onPrng(Prng prng) {
        context.add(prng);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        context.merge(schedule.getScheduleId(), schedule, this::mergeSchedule);
    }

    @Override
    public void onStakingRewardTransfer(StakingRewardTransfer stakingRewardTransfer) {
        context.add(stakingRewardTransfer);

        var current = context.get(Entity.class, stakingRewardTransfer.getAccountId());
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
            var entity = EntityId.of(stakingRewardTransfer.getAccountId()).toEntity();
            entity.setStakePeriodStart(stakePeriodStart);
            entity.setTimestampLower(consensusTimestamp);
            entity.setType(null); // Clear the type since it's uncertain if the entity is ACCOUNT or CONTRACT
            onEntity(entity);
        }
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        context.merge(token.getTokenId(), token, this::mergeToken);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        context.merge(tokenAccount.getId(), tokenAccount, this::mergeTokenAccount);
    }

    @Override
    public void onTokenAllowance(TokenAllowance tokenAllowance) {
        context.merge(tokenAllowance.getId(), tokenAllowance, this::mergeFungibleAllowance);
    }

    @Override
    @SuppressWarnings("java:S2259")
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        if (entityProperties.getPersist().isTrackBalance()) {
            var tokenAccount = new TokenAccount();
            var tokenTransferId = tokenTransfer.getId();
            tokenAccount.setAccountId(tokenTransferId.getAccountId().getId());
            tokenAccount.setTokenId(tokenTransferId.getTokenId().getId());
            tokenAccount.setBalance(tokenTransfer.getAmount());
            tokenAccount.setBalanceTimestamp(tokenTransferId.getConsensusTimestamp());
            onTokenAccount(tokenAccount);
        }

        context.add(tokenTransfer);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        context.add(topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        context.add(transaction);

        if (entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.of(transaction.getType()))) {
            context.add(transaction.toTransactionHash());
        }

        onNftTransferList(transaction);
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        context.add(transactionSignature);
    }

    private void cleanup() {
        try {
            context.drainTo(i -> {});
            eventPublisher.publishEvent(new EntityBatchCleanupEvent(this));
        } catch (BeanCreationNotAllowedException e) {
            // This error can occur during shutdown
        }
    }

    private void flush() {
        try {
            // batch save action may run asynchronously, triggering it before other operations can reduce latency
            eventPublisher.publishEvent(new EntityBatchSaveEvent(this));

            var stopwatch = Stopwatch.createStarted();
            context.drainTo(batchPersister::persist);

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
            context.drainTo(NFT_FLUSH, batchPersister::persist);
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    private CustomFee mergeCustomFee(CustomFee previous, CustomFee current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    private ContractState mergeContractState(ContractState previous, ContractState current) {
        previous.setValue(current.getValue());
        previous.setModifiedTimestamp(current.getModifiedTimestamp());
        return previous;
    }

    @SuppressWarnings("java:S3776")
    private Entity mergeEntity(Entity previous, Entity current) {
        // This entity should not trigger a history record, so just copy common non-history fields, if set, to previous
        if (!current.hasHistory()) {
            previous.addBalance(current.getBalance());
            if (current.getBalanceTimestamp() != null) {
                previous.setBalanceTimestamp(current.getBalanceTimestamp());
            }

            if (current.getEthereumNonce() != null) {
                previous.setEthereumNonce(current.getEthereumNonce());
            }

            if (current.getStakePeriodStart() != null) {
                previous.setStakePeriodStart(current.getStakePeriodStart());
            }

            return previous;
        }

        // If previous doesn't have history, merge reversely from current to previous
        var src = previous.hasHistory() ? previous : current;
        var dest = previous.hasHistory() ? current : previous;

        boolean isSameTimestampLower = Objects.equals(current.getTimestampLower(), previous.getTimestampLower());
        if (current.hasHistory() && isSameTimestampLower) {
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
        if (dest.getBalanceTimestamp() == null) {
            dest.setBalanceTimestamp(src.getBalanceTimestamp());
        }

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
        if (!dest.hasHistory()) {
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
        if (current.hasHistory()) {
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

        if (!current.hasHistory()) {
            previous.setTotalSupply(current.getTotalSupply());
            return previous;
        }

        // When current has history, current should always have a null totalSupply,
        // Hence, it is safe to merge total supply from previous to current here.
        if (!previous.hasHistory()) {
            current.setTotalSupply(previous.getTotalSupply());
            return current;
        }

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

        // This method should not be called with negative total supply since wipe/burn/token dissociate of a deleted
        // token will not have history so will not reach here.
        current.setTotalSupply(previous.getTotalSupply());

        return current;
    }

    private TokenAccount mergeTokenAccount(TokenAccount lastTokenAccount, TokenAccount newTokenAccount) {
        if (!lastTokenAccount.hasHistory()) {
            if (newTokenAccount.hasHistory()) {
                lastTokenAccount.setAutomaticAssociation(newTokenAccount.getAutomaticAssociation());
                lastTokenAccount.setAssociated(newTokenAccount.getAssociated());
                lastTokenAccount.setCreatedTimestamp(newTokenAccount.getCreatedTimestamp());
                lastTokenAccount.setFreezeStatus(newTokenAccount.getFreezeStatus());
                lastTokenAccount.setKycStatus(newTokenAccount.getKycStatus());
                lastTokenAccount.setTimestampRange(newTokenAccount.getTimestampRange());
                return lastTokenAccount;
            }

            return mergeTokenAccountBalance(lastTokenAccount, newTokenAccount);
        }

        if (lastTokenAccount.hasHistory() && !newTokenAccount.hasHistory()) {
            return mergeTokenAccountBalance(lastTokenAccount, newTokenAccount);
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
        if (lastTokenAccount.getBalanceTimestamp() != null) {
            newTokenAccount.setBalanceTimestamp(lastTokenAccount.getBalanceTimestamp());
        }

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

    private TokenAccount mergeTokenAccountBalance(TokenAccount lastTokenAccount, TokenAccount newTokenAccount) {
        lastTokenAccount.setBalance(newTokenAccount.getBalance() + lastTokenAccount.getBalance());
        if (newTokenAccount.getBalanceTimestamp() != null) {
            lastTokenAccount.setBalanceTimestamp(newTokenAccount.getBalanceTimestamp());
        }
        return lastTokenAccount;
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
                tokenAccount.setBalanceTimestamp(transaction.getConsensusTimestamp());
                onTokenAccount(tokenAccount);
            }

            if (!EntityId.isEmpty(nftTransfer.getReceiverAccountId())) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getReceiverAccountId().getId());
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(1);
                tokenAccount.setBalanceTimestamp(transaction.getConsensusTimestamp());
                onTokenAccount(tokenAccount);
            }
        }
    }
}
