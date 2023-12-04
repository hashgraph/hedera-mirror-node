/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.aggregator.LogsBloomAggregator;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.contract.ContractTransaction;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowanceHistory;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.domain.entity.EntityStakeHistory;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.NftAllowanceHistory;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowanceHistory;
import com.hedera.mirror.common.domain.event.EventFile;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.job.ReconciliationJob;
import com.hedera.mirror.common.domain.job.ReconciliationStatus;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.CustomFeeHistory;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftHistory;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccountHistory;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenHistory;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.topic.TopicMessageLookup;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.ItemizedTransfer;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction.ResultDataCase;
import com.hedera.services.stream.proto.ContractActionType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    public static final int KEY_LENGTH_ECDSA = 33;
    public static final int KEY_LENGTH_ED25519 = 32;

    private static final long LAST_RESERVED_ID = 1000;

    private final EntityManager entityManager;
    private final TransactionOperations transactionOperations;
    private final AtomicLong id = new AtomicLong(0L);
    private final AtomicInteger transactionIndex = new AtomicInteger(0);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();

    // Intended for use by unit tests that don't need persistence
    public DomainBuilder() {
        this(null, null);
    }

    public DomainWrapper<AccountBalance, AccountBalance.AccountBalanceBuilder> accountBalance() {
        var builder = AccountBalance.builder().balance(10L).id(new AccountBalance.Id(timestamp(), entityId()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AccountBalanceFile, AccountBalanceFile.AccountBalanceFileBuilder> accountBalanceFile() {
        long timestamp = timestamp();
        var name = Instant.ofEpochSecond(0L, timestamp).toString().replace(':', '_') + "_Balances.pb.gz";
        var builder = AccountBalanceFile.builder()
                .bytes(bytes(16))
                .consensusTimestamp(timestamp)
                .count(1L)
                .fileHash(text(96))
                .loadEnd(timestamp + 1)
                .loadStart(timestamp)
                .name(name)
                .nodeId(number())
                .timeOffset(0);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBook, AddressBook.AddressBookBuilder> addressBook() {
        var builder = AddressBook.builder()
                .fileData(bytes(10))
                .fileId(EntityId.of(0L, 0L, 102))
                .nodeCount(6)
                .startConsensusTimestamp(timestamp())
                .endConsensusTimestamp(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBookEntry, AddressBookEntry.AddressBookEntryBuilder> addressBookEntry() {
        return addressBookEntry(0);
    }

    public DomainWrapper<AddressBookEntry, AddressBookEntry.AddressBookEntryBuilder> addressBookEntry(int endpoints) {
        long consensusTimestamp = timestamp();
        long nodeId = number();
        var builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .description(text(10))
                .memo(text(10))
                .nodeId(nodeId)
                .nodeAccountId(EntityId.of(0L, 0L, nodeId + 3))
                .nodeCertHash(bytes(96))
                .publicKey(text(64))
                .stake(0L);

        var serviceEndpoints = new HashSet<AddressBookServiceEndpoint>();
        builder.serviceEndpoints(serviceEndpoints);

        for (int i = 0; i < endpoints; ++i) {
            var endpoint = addressBookServiceEndpoint()
                    .customize(a -> a.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                    .get();
            serviceEndpoints.add(endpoint);
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBookServiceEndpoint, AddressBookServiceEndpoint.AddressBookServiceEndpointBuilder>
            addressBookServiceEndpoint() {
        String ipAddress = "";
        try {
            ipAddress = InetAddress.getByAddress(bytes(4)).getHostAddress();
        } catch (UnknownHostException e) {
            // This shouldn't happen
        }

        var builder = AddressBookServiceEndpoint.builder()
                .consensusTimestamp(timestamp())
                .ipAddressV4(ipAddress)
                .nodeId(number())
                .port(50211);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AssessedCustomFee, AssessedCustomFee.AssessedCustomFeeBuilder> assessedCustomFee() {
        var builder = AssessedCustomFee.builder()
                .amount(100L)
                .collectorAccountId(entityId().getId())
                .consensusTimestamp(timestamp())
                .effectivePayerAccountIds(List.of(id(), id()))
                .payerAccountId(entityId())
                .tokenId(entityId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Contract, Contract.ContractBuilder<?, ?>> contract() {
        var builder = Contract.builder()
                .fileId(entityId())
                .id(id())
                .initcode(null) // Mutually exclusive with fileId
                .runtimeBytecode(bytes(256));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractAction, ContractAction.ContractActionBuilder> contractAction() {
        var builder = ContractAction.builder()
                .callDepth(1)
                .caller(entityId())
                .callerType(CONTRACT)
                .callOperationType(CallOperationType.OP_CALL.getNumber())
                .callType(ContractActionType.CALL.getNumber())
                .consensusTimestamp(timestamp())
                .gas(100L)
                .gasUsed(50L)
                .index((int) number())
                .input(bytes(256))
                .payerAccountId(entityId())
                .recipientAccount(entityId())
                .resultData(bytes(256))
                .resultDataType(ResultDataCase.OUTPUT.getNumber())
                .value(300L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractLog, ContractLog.ContractLogBuilder> contractLog() {
        var builder = ContractLog.builder()
                .bloom(bytes(256))
                .consensusTimestamp(timestamp())
                .contractId(entityId())
                .data(bytes(128))
                .index((int) number())
                .payerAccountId(entityId())
                .topic0(bytes(64))
                .topic1(bytes(64))
                .topic2(bytes(64))
                .topic3(bytes(64))
                .transactionHash(bytes(48))
                .transactionIndex(transactionIndex());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractResult, ContractResult.ContractResultBuilder<?, ?>> contractResult() {
        var builder = ContractResult.builder()
                .amount(1000L)
                .bloom(bytes(256))
                .callResult(bytes(512))
                .consensusTimestamp(timestamp())
                .contractId(entityId().getId())
                .createdContractIds(List.of(entityId().getId()))
                .errorMessage("")
                .functionParameters(bytes(64))
                .functionResult(bytes(128))
                .gasLimit(200L)
                .gasUsed(100L)
                .payerAccountId(entityId())
                .senderId(entityId())
                .transactionHash(bytes(32))
                .transactionIndex(1)
                .transactionNonce(0)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractState, ContractState.ContractStateBuilder> contractState() {
        var createdTimestamp = timestamp();
        var builder = ContractState.builder()
                .contractId(id())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(createdTimestamp)
                .slot(bytes(32))
                .value(bytes(32));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractStateChange, ContractStateChange.ContractStateChangeBuilder> contractStateChange() {
        var builder = ContractStateChange.builder()
                .consensusTimestamp(timestamp())
                .contractId(entityId().getId())
                .payerAccountId(entityId())
                .slot(bytes(128))
                .valueRead(bytes(64))
                .valueWritten(bytes(64));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractTransaction, ContractTransaction.ContractTransactionBuilder> contractTransaction() {
        var payerAccountId = entityId().getId();
        var contractId = entityId().getId();
        var builder = ContractTransaction.builder()
                .consensusTimestamp(timestamp())
                .entityId(contractId)
                .payerAccountId(payerAccountId)
                .contractIds(Arrays.asList(payerAccountId, contractId));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractTransactionHash, ContractTransactionHash.ContractTransactionHashBuilder>
            contractTransactionHash() {
        var builder = ContractTransactionHash.builder()
                .consensusTimestamp(timestamp())
                .entityId(id())
                .payerAccountId(id())
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .hash(bytes(32));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoAllowance, CryptoAllowance.CryptoAllowanceBuilder<?, ?>> cryptoAllowance() {
        long amount = number() + 1000;
        var spender = entityId();
        var builder = CryptoAllowance.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(Range.atLeast(timestamp()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoAllowanceHistory, CryptoAllowanceHistory.CryptoAllowanceHistoryBuilder<?, ?>>
            cryptoAllowanceHistory() {
        long amount = number() + 1000;
        var spender = entityId();
        var builder = CryptoAllowanceHistory.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(Range.closedOpen(timestamp(), timestamp()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoTransfer, CryptoTransfer.CryptoTransferBuilder> cryptoTransfer() {
        var builder = CryptoTransfer.builder()
                .amount(10L)
                .consensusTimestamp(timestamp())
                .entityId(entityId().getId())
                .isApproval(false)
                .payerAccountId(entityId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CustomFee, CustomFee.CustomFeeBuilder<?, ?>> customFee() {
        var builder = CustomFee.builder()
                .fixedFees(List.of(fixedFee()))
                .fractionalFees(List.of(fractionalFee()))
                .royaltyFees(List.of(royaltyFee()))
                .timestampRange(Range.atLeast(timestamp()))
                .tokenId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CustomFeeHistory, CustomFeeHistory.CustomFeeHistoryBuilder<?, ?>> customFeeHistory() {
        long timestamp = timestamp();
        var builder = CustomFeeHistory.builder()
                .fixedFees(List.of(fixedFee()))
                .fractionalFees(List.of(fractionalFee()))
                .royaltyFees(List.of(royaltyFee()))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> entity(long id, long createdTimestamp) {
        var builder = Entity.builder()
                .alias(key())
                .autoRenewAccountId(id)
                .autoRenewPeriod(1800L)
                .balance(tinybar())
                .balanceTimestamp(createdTimestamp)
                .createdTimestamp(createdTimestamp)
                .declineReward(false)
                .deleted(false)
                .ethereumNonce(1L)
                .evmAddress(evmAddress())
                .expirationTimestamp(createdTimestamp + 30_000_000L)
                .id(id)
                .key(key())
                .maxAutomaticTokenAssociations(1)
                .memo(text(16))
                .obtainerId(entityId())
                .permanentRemoval(false)
                .proxyAccountId(entityId())
                .num(id)
                .realm(0L)
                .receiverSigRequired(true)
                .shard(0L)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .submitKey(key())
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(ACCOUNT);

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> entity() {
        return entity(id(), timestamp());
    }

    public DomainWrapper<EntityHistory, EntityHistory.EntityHistoryBuilder<?, ?>> entityHistory() {
        long id = id();
        long timestamp = timestamp();

        var builder = EntityHistory.builder()
                .alias(key())
                .autoRenewAccountId(id())
                .autoRenewPeriod(1800L)
                .balance(number())
                .balanceTimestamp(timestamp)
                .createdTimestamp(timestamp)
                .declineReward(false)
                .deleted(false)
                .ethereumNonce(1L)
                .evmAddress(evmAddress())
                .expirationTimestamp(timestamp + 30_000_000L)
                .id(id)
                .key(key())
                .maxAutomaticTokenAssociations(1)
                .memo(text(16))
                .obtainerId(entityId())
                .permanentRemoval(false)
                .proxyAccountId(entityId())
                .num(id)
                .realm(0L)
                .receiverSigRequired(true)
                .shard(0L)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .submitKey(key())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .type(ACCOUNT);

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EntityStake, EntityStake.EntityStakeBuilder<?, ?>> entityStake() {
        var builder = EntityStake.builder()
                .declineRewardStart(false)
                .endStakePeriod(0L)
                .id(id())
                .pendingReward(0L)
                .stakedNodeIdStart(-1L)
                .stakedToMe(0L)
                .stakeTotalStart(0L)
                .timestampRange(Range.atLeast(timestamp()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EntityStakeHistory, EntityStakeHistory.EntityStakeHistoryBuilder<?, ?>> entityStakeHistory() {
        long timestamp = timestamp();
        var builder = EntityStakeHistory.builder()
                .declineRewardStart(false)
                .endStakePeriod(0L)
                .id(id())
                .pendingReward(0L)
                .stakedNodeIdStart(-1L)
                .stakedToMe(0L)
                .stakeTotalStart(0L)
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EntityTransaction, EntityTransaction.EntityTransactionBuilder> entityTransaction() {
        var builder = EntityTransaction.builder()
                .consensusTimestamp(timestamp())
                .entityId(id())
                .payerAccountId(entityId())
                .type(TransactionType.CRYPTOCREATEACCOUNT.getProtoId())
                .result(ResponseCodeEnum.SUCCESS_VALUE);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EthereumTransaction, EthereumTransaction.EthereumTransactionBuilder> ethereumTransaction(
            boolean hasInitCode) {
        var builder = EthereumTransaction.builder()
                .accessList(bytes(100))
                .chainId(bytes(1))
                .consensusTimestamp(timestamp())
                .data(bytes(100))
                .gasLimit(Long.MAX_VALUE)
                .gasPrice(bytes(32))
                .hash(bytes(32))
                .maxGasAllowance(Long.MAX_VALUE)
                .maxFeePerGas(bytes(32))
                .maxPriorityFeePerGas(bytes(32))
                .nonce(1234L)
                .payerAccountId(entityId())
                .recoveryId(3)
                .signatureR(bytes(32))
                .signatureS(bytes(32))
                .signatureV(bytes(1))
                .toAddress(bytes(20))
                .type(2)
                .value(bytes(32));

        if (hasInitCode) {
            builder.callData(bytes(100));
        } else {
            builder.callDataId(entityId());
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EventFile, EventFile.EventFileBuilder> eventFile() {
        long timestamp = timestamp();
        var builder = EventFile.builder()
                .bytes(bytes(128))
                .consensusStart(timestamp)
                .consensusEnd(timestamp + 1)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(text(96))
                .hash(text(96))
                .loadEnd(now.plusSeconds(1).getEpochSecond())
                .loadStart(now.getEpochSecond())
                .name(now.toString().replace(':', '_') + ".rcd")
                .nodeId(number())
                .previousHash(text(96))
                .version(3);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<FileData, FileData.FileDataBuilder> fileData() {
        var builder = FileData.builder()
                .consensusTimestamp(timestamp())
                .fileData(bytes(128))
                .entityId(entityId())
                .transactionType(TransactionType.FILECREATE.getProtoId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    private FallbackFee fallbackFee() {
        return FallbackFee.builder()
                .amount(number())
                .denominatingTokenId(entityId())
                .build();
    }

    private FixedFee fixedFee() {
        return FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(number())
                .collectorAccountId(entityId())
                .denominatingTokenId(entityId())
                .build();
    }

    private FractionalFee fractionalFee() {
        return FractionalFee.builder()
                .allCollectorsAreExempt(true)
                .collectorAccountId(entityId())
                .denominator(number())
                .maximumAmount(number())
                .minimumAmount(1L)
                .numerator(number())
                .netOfTransfers(true)
                .build();
    }

    public DomainWrapper<LiveHash, LiveHash.LiveHashBuilder> liveHash() {
        var builder = LiveHash.builder().consensusTimestamp(timestamp()).livehash(bytes(64));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NetworkFreeze, NetworkFreeze.NetworkFreezeBuilder<?, ?>> networkFreeze() {
        var builder = NetworkFreeze.builder()
                .consensusTimestamp(timestamp())
                .endTime(timestamp())
                .fileHash(bytes(48))
                .fileId(entityId())
                .payerAccountId(entityId())
                .startTime(timestamp())
                .type(FreezeType.FREEZE_UPGRADE_VALUE);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NetworkStake, NetworkStake.NetworkStakeBuilder> networkStake() {
        var timestamp = timestamp();
        var builder = NetworkStake.builder()
                .consensusTimestamp(timestamp)
                .epochDay(getEpochDay(timestamp))
                .maxStakingRewardRatePerHbar(17_808L)
                .nodeRewardFeeDenominator(0L)
                .nodeRewardFeeNumerator(100L)
                .stakeTotal(number())
                .stakingPeriod(timestamp - 1L)
                .stakingPeriodDuration(1440)
                .stakingPeriodsStored(365)
                .stakingRewardFeeDenominator(100L)
                .stakingRewardFeeNumerator(100L)
                .stakingRewardRate(100_000_000_000L)
                .stakingStartThreshold(25_000_000_000_000_000L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Nft, Nft.NftBuilder<?, ?>> nft() {
        var createdTimestamp = timestamp();
        var builder = Nft.builder()
                .accountId(entityId())
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .metadata(bytes(16))
                .serialNumber(number())
                .timestampRange(Range.atLeast(createdTimestamp))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftHistory, NftHistory.NftHistoryBuilder<?, ?>> nftHistory() {
        long timestamp = timestamp();
        var builder = NftHistory.builder()
                .accountId(entityId())
                .createdTimestamp(timestamp())
                .deleted(false)
                .metadata(bytes(16))
                .serialNumber(number())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftAllowance, NftAllowance.NftAllowanceBuilder<?, ?>> nftAllowance() {
        var builder = NftAllowance.builder()
                .approvedForAll(false)
                .owner(entityId().getId())
                .payerAccountId(entityId())
                .spender(entityId().getId())
                .timestampRange(Range.atLeast(timestamp()))
                .tokenId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftAllowanceHistory, NftAllowanceHistory.NftAllowanceHistoryBuilder<?, ?>>
            nftAllowanceHistory() {
        long timestamp = timestamp();
        var builder = NftAllowanceHistory.builder()
                .approvedForAll(false)
                .owner(entityId().getId())
                .payerAccountId(entityId())
                .spender(entityId().getId())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftTransfer, NftTransfer.NftTransferBuilder> nftTransfer() {
        var builder = NftTransfer.builder()
                .isApproval(false)
                .receiverAccountId(entityId())
                .senderAccountId(entityId())
                .serialNumber(1L)
                .tokenId(entityId());

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NodeStake, NodeStake.NodeStakeBuilder> nodeStake() {
        long maxStake = 50_000_000_000L * TINYBARS_IN_ONE_HBAR / 26L;
        long stake = number() * TINYBARS_IN_ONE_HBAR;
        long timestamp = timestamp();

        var builder = NodeStake.builder()
                .consensusTimestamp(timestamp)
                .epochDay(getEpochDay(timestamp))
                .maxStake(maxStake)
                .minStake(maxStake / 2L)
                .nodeId(number())
                .rewardRate(number())
                .stake(stake)
                .stakeNotRewarded(TINYBARS_IN_ONE_HBAR)
                .stakeRewarded(stake - TINYBARS_IN_ONE_HBAR)
                .stakingPeriod(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Prng, Prng.PrngBuilder> prng() {
        var builder = Prng.builder()
                .consensusTimestamp(timestamp())
                .payerAccountId(id())
                .range(Integer.MAX_VALUE)
                .prngNumber(random.nextInt(Integer.MAX_VALUE));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ReconciliationJob, ReconciliationJob.ReconciliationJobBuilder> reconciliationJob() {
        var builder = ReconciliationJob.builder()
                .consensusTimestamp(timestamp())
                .error("")
                .status(ReconciliationStatus.SUCCESS)
                .timestampStart(instant())
                .timestampEnd(instant());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<RecordFile, RecordFile.RecordFileBuilder> recordFile() {
        // reset transaction index
        transactionIndex.set(0);

        long timestamp = timestamp();
        long consensusEnd = timestamp + 1;
        var instantString = now.toString().replace(':', '_');
        var builder = RecordFile.builder()
                .bytes(bytes(128))
                .consensusStart(timestamp)
                .consensusEnd(consensusEnd)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(hash(96))
                .gasUsed(100L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(28)
                .hapiVersionPatch(0)
                .hash(hash(96))
                .index(number())
                .logsBloom(bloomFilter())
                .loadEnd(now.plusSeconds(1).getEpochSecond())
                .loadStart(now.getEpochSecond())
                .name(instantString + ".rcd.gz")
                .nodeId(number())
                .previousHash(hash(96))
                .sidecarCount(1)
                .sidecars(List.of(sidecarFile()
                        .customize(s -> s.consensusEnd(consensusEnd).name(instantString + "_01.rcd.gz"))
                        .get()))
                .size(256 * 1024)
                .version(6);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    private RoyaltyFee royaltyFee() {
        return RoyaltyFee.builder()
                .allCollectorsAreExempt(true)
                .collectorAccountId(entityId())
                .denominator(number())
                .fallbackFee(fallbackFee())
                .numerator(number())
                .build();
    }

    public DomainWrapper<Schedule, Schedule.ScheduleBuilder> schedule() {
        var builder = Schedule.builder()
                .consensusTimestamp(timestamp())
                .creatorAccountId(entityId())
                .expirationTime(timestamp())
                .payerAccountId(entityId())
                .scheduleId(entityId().getId())
                .transactionBody(bytes(64))
                .waitForExpiry(true);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<SidecarFile, SidecarFile.SidecarFileBuilder> sidecarFile() {
        var data = bytes(256);
        var builder = SidecarFile.builder()
                .bytes(data)
                .consensusEnd(timestamp())
                .hash(bytes(DigestAlgorithm.SHA_384.getSize()))
                .hashAlgorithm(DigestAlgorithm.SHA_384)
                .index(1)
                .name(now.toString().replace(':', '_') + "_01.rcd.gz")
                .records(Collections.emptyList())
                .size(data.length)
                .types(List.of(1, 2));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<StakingRewardTransfer, StakingRewardTransfer.StakingRewardTransferBuilder>
            stakingRewardTransfer() {
        var accountId = entityId();
        var builder = StakingRewardTransfer.builder()
                .accountId(accountId.getId())
                .amount(number())
                .consensusTimestamp(timestamp())
                .payerAccountId(accountId);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Token, Token.TokenBuilder<?, ?>> token() {
        long timestamp = timestamp();
        var builder = Token.builder()
                .createdTimestamp(timestamp)
                .decimals((int) number())
                .feeScheduleKey(key())
                .freezeDefault(false)
                .freezeKey(key())
                .initialSupply(1_000_000_000L + number())
                .kycKey(key())
                .name(text(8))
                .pauseKey(key())
                .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                .supplyKey(key())
                .supplyType(TokenSupplyTypeEnum.INFINITE)
                .symbol(text(8))
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(entityId().getId())
                .totalSupply(1_000_000_000L + number())
                .treasuryAccountId(entityId())
                .type(TokenTypeEnum.FUNGIBLE_COMMON)
                .wipeKey(key());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenHistory, TokenHistory.TokenHistoryBuilder<?, ?>> tokenHistory() {
        long timestamp = timestamp();
        var builder = TokenHistory.builder()
                .createdTimestamp(timestamp)
                .decimals((int) number())
                .feeScheduleKey(key())
                .freezeDefault(false)
                .freezeKey(key())
                .initialSupply(1_000_000_000L + number())
                .kycKey(key())
                .name(text(8))
                .pauseKey(key())
                .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                .supplyKey(key())
                .supplyType(TokenSupplyTypeEnum.INFINITE)
                .symbol(text(8))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(entityId().getId())
                .totalSupply(1_000_000_000L + number())
                .treasuryAccountId(entityId())
                .type(TokenTypeEnum.FUNGIBLE_COMMON)
                .wipeKey(key());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>> tokenAccount() {
        long timestamp = timestamp();
        var builder = TokenAccount.builder()
                .accountId(id())
                .automaticAssociation(false)
                .associated(true)
                .balance(number())
                .balanceTimestamp(timestamp)
                .createdTimestamp(timestamp)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAccountHistory, TokenAccountHistory.TokenAccountHistoryBuilder<?, ?>>
            tokenAccountHistory() {
        long timestamp = timestamp();
        var builder = TokenAccountHistory.builder()
                .accountId(id())
                .automaticAssociation(false)
                .associated(true)
                .balanceTimestamp(timestamp)
                .createdTimestamp(timestamp)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAllowance, TokenAllowance.TokenAllowanceBuilder<?, ?>> tokenAllowance() {
        long amount = number() + 1000;
        var spender = entityId();
        var builder = TokenAllowance.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(Range.atLeast(timestamp()))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAllowanceHistory, TokenAllowanceHistory.TokenAllowanceHistoryBuilder<?, ?>>
            tokenAllowanceHistory() {
        long amount = number() + 1000;
        var spender = entityId();
        long timestamp = timestamp();
        var builder = TokenAllowanceHistory.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenBalance, TokenBalance.TokenBalanceBuilder> tokenBalance() {
        var builder = TokenBalance.builder().balance(1L).id(new TokenBalance.Id(timestamp(), entityId(), entityId()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenTransfer, TokenTransfer.TokenTransferBuilder> tokenTransfer() {
        var builder = TokenTransfer.builder()
                .amount(100L)
                .id(new TokenTransfer.Id(timestamp(), entityId(), entityId()))
                .payerAccountId(entityId());

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> topic() {
        return entity().customize(e -> e.alias(null)
                .balance(null)
                .balanceTimestamp(null)
                .receiverSigRequired(null)
                .ethereumNonce(null)
                .evmAddress(null)
                .maxAutomaticTokenAssociations(null)
                .proxyAccountId(null)
                .type(TOPIC));
    }

    public DomainWrapper<TopicMessage, TopicMessage.TopicMessageBuilder> topicMessage() {
        var transactionId = TransactionID.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(id()))
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(timestamp()))
                .build()
                .toByteArray();
        var builder = TopicMessage.builder()
                .chunkNum(1)
                .chunkTotal(1)
                .consensusTimestamp(timestamp())
                .initialTransactionId(transactionId)
                .message(bytes(128))
                .payerAccountId(entityId())
                .runningHashVersion(2)
                .runningHash(bytes(48))
                .sequenceNumber(number())
                .topicId(entityId())
                .validStartTimestamp(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TopicMessageLookup, TopicMessageLookup.TopicMessageLookupBuilder> topicMessageLookup() {
        long timestamp = timestamp();
        long sequenceNumber = number();
        var builder = TopicMessageLookup.builder()
                .partition(String.format("topic_message_%d", number()))
                .sequenceNumberRange(Range.closedOpen(sequenceNumber, sequenceNumber + 1))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .topicId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Transaction, Transaction.TransactionBuilder> transaction() {
        var builder = Transaction.builder()
                .chargedTxFee(10000000L)
                .consensusTimestamp(timestamp())
                .entityId(entityId())
                .index(transactionIndex())
                .initialBalance(10000000L)
                .itemizedTransfer(List.of(ItemizedTransfer.builder()
                        .amount(100L)
                        .entityId(entityId())
                        .isApproval(false)
                        .build()))
                .maxFee(100000000L)
                .memo(bytes(10))
                .nodeAccountId(entityId())
                .nonce(0)
                .parentConsensusTimestamp(timestamp())
                .payerAccountId(entityId())
                .result(ResponseCodeEnum.SUCCESS.getNumber())
                .scheduled(false)
                .transactionBytes(bytes(100))
                .transactionHash(bytes(48))
                .transactionRecordBytes(bytes(200))
                .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                .validStartNs(timestamp())
                .validDurationSeconds(120L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TransactionHash, TransactionHash.TransactionHashBuilder> transactionHash() {
        var builder = TransactionHash.builder().consensusTimestamp(timestamp()).hash(bytes(48));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TransactionSignature, TransactionSignature.TransactionSignatureBuilder>
            transactionSignature() {
        var builder = TransactionSignature.builder()
                .consensusTimestamp(timestamp())
                .entityId(entityId())
                .publicKeyPrefix(bytes(16))
                .signature(bytes(32))
                .type(SignaturePair.SignatureCase.ED25519.getNumber());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public <T, B> DomainWrapper<T, B> wrap(B builder, Supplier<T> supplier) {
        return new DomainWrapperImpl<>(builder, supplier);
    }

    public byte[] bloomFilter() {
        return bytes(LogsBloomAggregator.BYTE_SIZE);
    }

    // Helper methods
    public byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public byte[] evmAddress() {
        return bytes(20);
    }

    public EntityId entityId() {
        return EntityId.of(0L, 0L, id());
    }

    /**
     * Generates an encoded entity id above the max reserved id 1000. Use {@link #number()} instead for a number
     * starting from 1.
     *
     * @return The generated encoded entity id.
     */
    public long id() {
        return id.incrementAndGet() + LAST_RESERVED_ID;
    }

    // SQL timestamp type only supports up to microsecond granularity
    private Instant instant() {
        return now.truncatedTo(ChronoUnit.MILLIS).plusMillis(number());
    }

    public byte[] key() {
        if (id.get() % 2 == 0) {
            ByteString bytes = ByteString.copyFrom(bytes(KEY_LENGTH_ECDSA));
            return Key.newBuilder().setECDSASecp256K1(bytes).build().toByteArray();
        } else {
            ByteString bytes = ByteString.copyFrom(bytes(KEY_LENGTH_ED25519));
            return Key.newBuilder().setEd25519(bytes).build().toByteArray();
        }
    }

    public long number() {
        return id.incrementAndGet();
    }

    public String text(int characters) {
        return RandomStringUtils.randomAlphanumeric(characters);
    }

    public String hash(int characters) {
        return RandomStringUtils.random(characters, "0123456789abcdef");
    }

    public long timestamp() {
        return DomainUtils.convertToNanosMax(now.getEpochSecond(), now.getNano()) + number();
    }

    private long tinybar() {
        return number() * TINYBARS_IN_ONE_HBAR;
    }

    private long getEpochDay(long timestamp) {
        return LocalDate.ofInstant(Instant.ofEpochSecond(0, timestamp), ZoneId.of("UTC"))
                .atStartOfDay()
                .toLocalDate()
                .toEpochDay();
    }

    private int transactionIndex() {
        return transactionIndex.getAndIncrement();
    }

    @Value
    private class DomainWrapperImpl<T, B> implements DomainWrapper<T, B> {

        private final B builder;
        private final Supplier<T> supplier;

        @Override
        public DomainWrapper<T, B> customize(Consumer<B> customizer) {
            customizer.accept(builder);
            return this;
        }

        @Override
        public T get() {
            return supplier.get();
        }

        // The DomainBuilder can be used without an active ApplicationContext. If so, this method shouldn't be used.
        @Override
        public T persist() {
            T entity = get();

            if (entityManager == null) {
                throw new IllegalStateException("Unable to persist entity without an EntityManager");
            }

            transactionOperations.executeWithoutResult(t -> entityManager.persist(entity));
            log.trace("Inserted {}", entity);
            return entity;
        }
    }
}
