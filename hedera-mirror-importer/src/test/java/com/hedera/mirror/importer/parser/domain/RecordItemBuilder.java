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

package com.hedera.mirror.importer.parser.domain;

import static com.hedera.mirror.common.domain.DomainBuilder.KEY_LENGTH_ECDSA;
import static com.hedera.mirror.common.domain.DomainBuilder.KEY_LENGTH_ED25519;
import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties.PersistProperties;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.util.Version;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RecordItemBuilder {

    public static final ByteString EVM_ADDRESS = ByteString.fromHex("ebb9a1be370150759408cd7af48e9eda2b8ead57");
    public static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";
    public static final long STAKING_REWARD_ACCOUNT = 800L;

    private static final long INITIAL_ID = 1000L;
    private static final AccountID NODE =
            AccountID.newBuilder().setAccountNum(3).build();
    private static final RealmID REALM_ID = RealmID.getDefaultInstance();
    private static final ShardID SHARD_ID = ShardID.getDefaultInstance();
    private static final AccountID TREASURY =
            AccountID.newBuilder().setAccountNum(98).build();

    private final Map<TransactionType, Supplier<Builder<?>>> builders = new HashMap<>();
    private final AtomicLong id = new AtomicLong(INITIAL_ID);
    private final SecureRandom random = new SecureRandom();

    @Getter
    private final PersistProperties persistProperties = new PersistProperties();

    private Instant now = Instant.now();

    {
        // Dynamically lookup method references for every transaction body builder in this class
        Collection<Supplier<Builder<?>>> suppliers = TestUtils.gettersByType(this, Builder.class);
        suppliers.forEach(s -> builders.put(s.get().type, s));
    }

    public Supplier<Builder<?>> lookup(TransactionType type) {
        return builders.get(type);
    }

    public Builder<ConsensusCreateTopicTransactionBody.Builder> consensusCreateTopic() {
        var transactionBody = ConsensusCreateTopicTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setMemo(text(16))
                .setSubmitKey(key());
        return new Builder<>(TransactionType.CONSENSUSCREATETOPIC, transactionBody)
                .receipt(r -> r.setTopicID(topicId()));
    }

    public Builder<ConsensusDeleteTopicTransactionBody.Builder> consensusDeleteTopic() {
        var transactionBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId());
        return new Builder<>(TransactionType.CONSENSUSDELETETOPIC, transactionBody);
    }

    public Builder<ConsensusSubmitMessageTransactionBody.Builder> consensusSubmitMessage() {
        var transactionBody = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setMessage(bytes(128))
                .setTopicID(topicId());

        var builder = new Builder<>(TransactionType.CONSENSUSSUBMITMESSAGE, transactionBody)
                .receipt(r -> r.setTopicRunningHash(bytes(48))
                        .setTopicRunningHashVersion(3)
                        .setTopicSequenceNumber(id()));

        transactionBody.setChunkInfo(ConsensusMessageChunkInfo.newBuilder()
                .setInitialTransactionID(builder.transactionBodyWrapper.getTransactionID())
                .setNumber(1)
                .setTotal(1));

        return builder;
    }

    public Builder<ConsensusUpdateTopicTransactionBody.Builder> consensusUpdateTopic() {
        var transactionBody = ConsensusUpdateTopicTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setExpirationTime(timestamp())
                .setMemo(StringValue.of(text(16)))
                .setSubmitKey(key())
                .setTopicID(topicId());
        return new Builder<>(TransactionType.CONSENSUSUPDATETOPIC, transactionBody);
    }

    public Builder<ContractCallTransactionBody.Builder> contractCall() {
        return contractCall(contractId());
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractCallTransactionBody.Builder> contractCall(ContractID contractId) {
        ContractCallTransactionBody.Builder transactionBody = ContractCallTransactionBody.newBuilder()
                .setAmount(5_000L)
                .setContractID(contractId)
                .setFunctionParameters(bytes(64))
                .setGas(10_000L);

        return new Builder<>(TransactionType.CONTRACTCALL, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCallResult(
                        contractFunctionResult(contractId).clearCreatedContractIDs()))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> r.add(contractActions()));
    }

    public Builder<ContractCreateTransactionBody.Builder> contractCreate() {
        return contractCreate(contractId());
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractCreateTransactionBody.Builder> contractCreate(ContractID contractId) {
        ContractCreateTransactionBody.Builder transactionBody = ContractCreateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccountId(accountId())
                .setAutoRenewPeriod(duration(30))
                .setConstructorParameters(bytes(64))
                .setDeclineReward(true)
                .setFileID(fileId())
                .setGas(10_000L)
                .setInitialBalance(20_000L)
                .setMaxAutomaticTokenAssociations(5)
                .setMemo(text(16))
                .setNewRealmAdminKey(key())
                .setProxyAccountID(accountId())
                .setRealmID(REALM_ID)
                .setShardID(SHARD_ID)
                .setStakedNodeId(1L);

        return new Builder<>(TransactionType.CONTRACTCREATEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCreateResult(
                        contractFunctionResult(contractId).addCreatedContractIDs(contractId)))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> {
                    var contractActions = contractActions();
                    contractActions
                            .getActionsBuilder()
                            .getContractActionsBuilderList()
                            .forEach(ContractAction.Builder::clearRecipient);
                    r.add(contractActions);
                })
                .sidecarRecords(r -> r.add(contractBytecode(contractId)));
    }

    public Builder<ContractDeleteTransactionBody.Builder> contractDelete() {
        var contractId = contractId();
        ContractDeleteTransactionBody.Builder transactionBody = ContractDeleteTransactionBody.newBuilder()
                .setContractID(contractId)
                .setTransferAccountID(accountId());

        return new Builder<>(TransactionType.CONTRACTDELETEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId));
    }

    public ContractFunctionResult.Builder contractFunctionResult() {
        return contractFunctionResult(contractId());
    }

    @SuppressWarnings("deprecation")
    public ContractFunctionResult.Builder contractFunctionResult(ContractID contractId) {
        return ContractFunctionResult.newBuilder()
                .setAmount(5_000L)
                .setBloom(bytes(256))
                .setContractCallResult(bytes(16))
                .setContractID(contractId)
                .addCreatedContractIDs(contractId())
                .setErrorMessage(text(10))
                .setFunctionParameters(bytes(64))
                .setGas(10_000L)
                .setGasUsed(1000L)
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(bytes(256))
                        .setContractID(contractId)
                        .setData(bytes(128))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .build())
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(bytes(256))
                        .setContractID(contractId())
                        .setData(bytes(128))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .addTopic(bytes(32))
                        .build())
                .setSenderId(accountId())
                .addContractNonces(ContractNonceInfo.newBuilder()
                        .setContractId(contractId)
                        .setNonce(1)
                        .build());
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractUpdateTransactionBody.Builder> contractUpdate() {
        var contractId = contractId();
        ContractUpdateTransactionBody.Builder transactionBody = ContractUpdateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccountId(accountId())
                .setAutoRenewPeriod(duration(30))
                .setContractID(contractId)
                .setDeclineReward(BoolValue.of(true))
                .setExpirationTime(timestamp())
                .setMaxAutomaticTokenAssociations(Int32Value.of(10))
                .setMemoWrapper(StringValue.of(text(16)))
                .setProxyAccountID(accountId())
                .setStakedAccountId(accountId());

        return new Builder<>(TransactionType.CONTRACTUPDATEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId));
    }

    public Builder<CryptoAddLiveHashTransactionBody.Builder> cryptoAddLiveHash() {
        var builder = CryptoAddLiveHashTransactionBody.newBuilder()
                .setLiveHash(LiveHash.newBuilder()
                        .setAccountId(accountId())
                        .setDuration(duration(900))
                        .setHash(bytes(48))
                        .setKeys(KeyList.newBuilder().addKeys(key())));
        return new Builder<>(TransactionType.CRYPTOADDLIVEHASH, builder);
    }

    public Builder<CryptoApproveAllowanceTransactionBody.Builder> cryptoApproveAllowance() {
        var builder = CryptoApproveAllowanceTransactionBody.newBuilder()
                .addCryptoAllowances(CryptoAllowance.newBuilder()
                        .setAmount(10L)
                        .setOwner(accountId())
                        .setSpender(accountId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(accountId())
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setApprovedForAll(BoolValue.of(false))
                        .setOwner(accountId())
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setApprovedForAll(BoolValue.of(true))
                        .setOwner(accountId())
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setApprovedForAll(BoolValue.of(true))
                        .setOwner(accountId())
                        .addSerialNumbers(2L)
                        .addSerialNumbers(3L)
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addTokenAllowances(TokenAllowance.newBuilder()
                        .setAmount(10L)
                        .setOwner(accountId())
                        .setSpender(accountId())
                        .setTokenId(tokenId()));
        // duplicate allowances
        builder.addCryptoAllowances(builder.getCryptoAllowances(0))
                .addTokenAllowances(builder.getTokenAllowances(0))
                .addNftAllowances(builder.getNftAllowances(0))
                .addNftAllowances(builder.getNftAllowances(2).toBuilder().setApprovedForAll(BoolValue.of(false)));
        return new Builder<>(TransactionType.CRYPTOAPPROVEALLOWANCE, builder);
    }

    @SuppressWarnings("deprecation")
    public Builder<CryptoCreateTransactionBody.Builder> cryptoCreate() {
        var builder = CryptoCreateTransactionBody.newBuilder()
                .setAlias(bytes(20))
                .setAutoRenewPeriod(duration(30))
                .setDeclineReward(true)
                .setInitialBalance(1000L)
                .setKey(key())
                .setMaxAutomaticTokenAssociations(2)
                .setMemo(text(16))
                .setProxyAccountID(accountId())
                .setRealmID(REALM_ID)
                .setReceiverSigRequired(false)
                .setShardID(SHARD_ID)
                .setStakedNodeId(1L);
        return new Builder<>(TransactionType.CRYPTOCREATEACCOUNT, builder).receipt(r -> r.setAccountID(accountId()));
    }

    public Builder<CryptoDeleteTransactionBody.Builder> cryptoDelete() {
        var builder = CryptoDeleteTransactionBody.newBuilder()
                .setDeleteAccountID(accountId())
                .setTransferAccountID(accountId());
        return new Builder<>(TransactionType.CRYPTODELETE, builder);
    }

    public Builder<CryptoDeleteAllowanceTransactionBody.Builder> cryptoDeleteAllowance() {
        var builder = CryptoDeleteAllowanceTransactionBody.newBuilder()
                .addNftAllowances(NftRemoveAllowance.newBuilder()
                        .setOwner(accountId())
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .setTokenId(tokenId()))
                .addNftAllowances(NftRemoveAllowance.newBuilder()
                        .setOwner(accountId())
                        .addSerialNumbers(2L)
                        .addSerialNumbers(3L)
                        .setTokenId(tokenId()));
        return new Builder<>(TransactionType.CRYPTODELETEALLOWANCE, builder);
    }

    public Builder<CryptoTransferTransactionBody.Builder> cryptoTransfer() {
        return new Builder<>(TransactionType.CRYPTOTRANSFER, cryptoTransferTransactionBody());
    }

    @SuppressWarnings("deprecation")
    public Builder<CryptoUpdateTransactionBody.Builder> cryptoUpdate() {
        var accountId = accountId();
        var builder = CryptoUpdateTransactionBody.newBuilder()
                .setAutoRenewPeriod(duration(30))
                .setAccountIDToUpdate(accountId)
                .setDeclineReward(BoolValue.of(true))
                .setKey(key())
                .setProxyAccountID(accountId())
                .setReceiverSigRequired(false)
                .setStakedNodeId(1L);
        return new Builder<>(TransactionType.CRYPTOUPDATEACCOUNT, builder).receipt(r -> r.setAccountID(accountId));
    }

    public CustomFee.Builder customFee(CustomFee.FeeCase feeCase) {
        var accountId = accountId();
        var customFee =
                CustomFee.newBuilder().setFeeCollectorAccountId(accountId).setAllCollectorsAreExempt(false);
        switch (feeCase) {
            case FIXED_FEE -> customFee.setFixedFee(fixedFee());
            case ROYALTY_FEE -> customFee.setRoyaltyFee(royaltyFee());
            case FRACTIONAL_FEE -> customFee.setFractionalFee(fractionalFee());
        }
        return customFee;
    }

    private FixedFee.Builder fixedFee() {
        return FixedFee.newBuilder().setAmount(100L).setDenominatingTokenId(tokenId());
    }

    private FractionalFee.Builder fractionalFee() {
        return FractionalFee.newBuilder()
                .setFractionalAmount(Fraction.newBuilder().setNumerator(1).setDenominator(10))
                .setMaximumAmount(1000L)
                .setMinimumAmount(1L)
                .setNetOfTransfers(false);
    }

    private RoyaltyFee.Builder royaltyFee() {
        return RoyaltyFee.newBuilder()
                .setExchangeValueFraction(Fraction.newBuilder().setNumerator(50).setDenominator(100))
                .setFallbackFee(fixedFee());
    }

    public Builder<EthereumTransactionBody.Builder> ethereumTransaction() {
        return ethereumTransaction(false);
    }

    @SneakyThrows
    public Builder<EthereumTransactionBody.Builder> ethereumTransaction(boolean create) {
        var transactionBytes = Hex.decodeHex(LONDON_RAW_TX);
        EthereumTransactionBody.Builder transactionBody = EthereumTransactionBody.newBuilder()
                .setEthereumData(ByteString.copyFrom(transactionBytes))
                .setMaxGasAllowance(10_000L);

        var contractId = contractId();
        var digestedHash = bytes(32);
        var functionResult = contractFunctionResult(contractId);
        var builder = new Builder<>(TransactionType.ETHEREUMTRANSACTION, transactionBody)
                .record(r -> r.setContractCallResult(functionResult).setEthereumHash(digestedHash))
                .recordItem(r -> r.hapiVersion(new Version(0, 26, 0)))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> r.add(contractActions()));

        if (create) {
            transactionBody.setCallData(fileId());
            builder.record(r -> r.setContractCreateResult(functionResult));
        }

        return builder;
    }

    public Builder<FileAppendTransactionBody.Builder> fileAppend() {
        var builder =
                FileAppendTransactionBody.newBuilder().setContents(bytes(100)).setFileID(fileId());
        return new Builder<>(TransactionType.FILEAPPEND, builder);
    }

    public Builder<FileCreateTransactionBody.Builder> fileCreate() {
        var builder = FileCreateTransactionBody.newBuilder()
                .setContents(bytes(100))
                .setExpirationTime(timestamp())
                .setKeys(KeyList.newBuilder().addKeys(key()))
                .setRealmID(RealmID.newBuilder().setRealmNum(0L))
                .setShardID(ShardID.newBuilder().setShardNum(0L))
                .setMemo(text(10));
        return new Builder<>(TransactionType.FILECREATE, builder).receipt(b -> b.setFileID(fileId()));
    }

    public Builder<FileDeleteTransactionBody.Builder> fileDelete() {
        var fileId = fileId();
        var builder = FileDeleteTransactionBody.newBuilder().setFileID(fileId);
        return new Builder<>(TransactionType.FILEDELETE, builder).receipt(b -> b.setFileID(fileId));
    }

    public Builder<FileUpdateTransactionBody.Builder> fileUpdate() {
        var builder = FileUpdateTransactionBody.newBuilder()
                .setContents(bytes(100))
                .setExpirationTime(timestamp())
                .setFileID(fileId())
                .setKeys(KeyList.newBuilder().addKeys(key()))
                .setMemo(StringValue.newBuilder().setValue(text(10)));
        return new Builder<>(TransactionType.FILEUPDATE, builder);
    }

    public Builder<FreezeTransactionBody.Builder> freeze() {
        var builder = FreezeTransactionBody.newBuilder()
                .setFileHash(bytes(48))
                .setFreezeType(FreezeType.PREPARE_UPGRADE)
                .setStartTime(timestamp())
                .setUpdateFile(fileId());
        return new Builder<>(TransactionType.FREEZE, builder);
    }

    public Builder<NodeStakeUpdateTransactionBody.Builder> nodeStakeUpdate() {
        var builder = NodeStakeUpdateTransactionBody.newBuilder()
                .setEndOfStakingPeriod(timestamp())
                .setMaxStakingRewardRatePerHbar(17_808L)
                .setNodeRewardFeeFraction(Fraction.newBuilder().setNumerator(0L).setDenominator(100L))
                .setStakingPeriod(1440)
                .setStakingPeriodsStored(365)
                .setStakingRewardFeeFraction(
                        Fraction.newBuilder().setNumerator(100L).setDenominator(100L))
                .setStakingRewardRate(100_000_000_000L)
                .setStakingStartThreshold(25_000_000_000_000_000L)
                .addNodeStake(nodeStake());
        return new Builder<>(TransactionType.NODESTAKEUPDATE, builder);
    }

    public Builder<UtilPrngTransactionBody.Builder> prng() {
        return prng(0);
    }

    public Builder<UtilPrngTransactionBody.Builder> prng(int range) {
        var builder = UtilPrngTransactionBody.newBuilder().setRange(range);
        var transactionBodyBuilder = new Builder<>(TransactionType.UTILPRNG, builder);

        return transactionBodyBuilder.record(r -> {
            if (range == 0) {
                r.setPrngBytes(ByteString.copyFrom(randomBytes(382)));
            } else if (range > 0) {
                r.setPrngNumber(random.nextInt());
            }
        });
    }

    public void reset(Instant start) {
        now = start;
        id.set(INITIAL_ID);
    }

    public Builder<ScheduleCreateTransactionBody.Builder> scheduleCreate() {
        var scheduledTransaction = SchedulableTransactionBody.newBuilder()
                .setTransactionFee(1_00_000_000)
                .setMemo(text(16))
                .setCryptoTransfer(cryptoTransferTransactionBody());
        var builder = ScheduleCreateTransactionBody.newBuilder()
                .setScheduledTransactionBody(scheduledTransaction)
                .setMemo(text(16))
                .setAdminKey(key())
                .setPayerAccountID(accountId())
                .setExpirationTime(timestamp())
                .setWaitForExpiry(true);
        return new Builder<>(TransactionType.SCHEDULECREATE, builder).receipt(r -> r.setScheduleID(scheduleId()));
    }

    public Builder<ScheduleDeleteTransactionBody.Builder> scheduleDelete() {
        var builder = ScheduleDeleteTransactionBody.newBuilder().setScheduleID(scheduleId());
        return new Builder<>(TransactionType.SCHEDULEDELETE, builder);
    }

    public Builder<ScheduleSignTransactionBody.Builder> scheduleSign() {
        var builder = ScheduleSignTransactionBody.newBuilder().setScheduleID(scheduleId());
        return new Builder<>(TransactionType.SCHEDULESIGN, builder);
    }

    public Builder<SystemDeleteTransactionBody.Builder> systemDelete() {
        var builder = SystemDeleteTransactionBody.newBuilder()
                .setFileID(fileId())
                .setExpirationTime(
                        TimestampSeconds.newBuilder().setSeconds(Instant.now().getEpochSecond() + id()));
        return new Builder<>(TransactionType.SYSTEMDELETE, builder);
    }

    public Builder<SystemUndeleteTransactionBody.Builder> systemUndelete() {
        var builder = SystemUndeleteTransactionBody.newBuilder().setFileID(fileId());
        return new Builder<>(TransactionType.SYSTEMUNDELETE, builder);
    }

    public Builder<TokenAssociateTransactionBody.Builder> tokenAssociate() {
        var transactionBody = TokenAssociateTransactionBody.newBuilder()
                .setAccount(accountId())
                .addTokens(tokenId());
        return new Builder<>(TransactionType.TOKENASSOCIATE, transactionBody);
    }

    public Builder<TokenBurnTransactionBody.Builder> tokenBurn() {
        var transactionBody = TokenBurnTransactionBody.newBuilder()
                .setAmount(1L)
                .setToken(tokenId())
                .addSerialNumbers(1L);
        return new Builder<>(TransactionType.TOKENBURN, transactionBody).receipt(b -> b.setNewTotalSupply(2L));
    }

    public Builder<TokenDissociateTransactionBody.Builder> tokenDissociate() {
        var transactionBody = TokenDissociateTransactionBody.newBuilder()
                .setAccount(accountId())
                .addTokens(tokenId());
        return new Builder<>(TransactionType.TOKENDISSOCIATE, transactionBody);
    }

    public Builder<TokenFreezeAccountTransactionBody.Builder> tokenFreeze() {
        var transactionBody = TokenFreezeAccountTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENFREEZE, transactionBody);
    }

    public Builder<TokenGrantKycTransactionBody.Builder> tokenGrantKyc() {
        var transactionBody = TokenGrantKycTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENGRANTKYC, transactionBody);
    }

    public Builder<TokenMintTransactionBody.Builder> tokenMint() {
        return tokenMint(NON_FUNGIBLE_UNIQUE);
    }

    public Builder<TokenMintTransactionBody.Builder> tokenMint(TokenType tokenType) {
        var transactionBody = TokenMintTransactionBody.newBuilder().setToken(tokenId());
        var builder = new Builder<>(TransactionType.TOKENMINT, transactionBody);

        if (tokenType == FUNGIBLE_COMMON) {
            transactionBody.setAmount(1000L);
        } else {
            transactionBody.addMetadata(bytes(16)).addMetadata(bytes(16));
            builder.receipt(b -> b.addSerialNumbers(1L).addSerialNumbers(2L));
        }

        return builder;
    }

    public Builder<TokenPauseTransactionBody.Builder> tokenPause() {
        var transactionBody = TokenPauseTransactionBody.newBuilder().setToken(tokenId());
        return new Builder<>(TransactionType.TOKENPAUSE, transactionBody);
    }

    public Builder<TokenRevokeKycTransactionBody.Builder> tokenRevokeKyc() {
        var transactionBody = TokenRevokeKycTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENREVOKEKYC, transactionBody);
    }

    public Builder<TokenUnfreezeAccountTransactionBody.Builder> tokenUnfreeze() {
        var transactionBody = TokenUnfreezeAccountTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENUNFREEZE, transactionBody);
    }

    public Builder<TokenUnpauseTransactionBody.Builder> tokenUnpause() {
        var transactionBody = TokenUnpauseTransactionBody.newBuilder().setToken(tokenId());
        return new Builder<>(TransactionType.TOKENUNPAUSE, transactionBody);
    }

    public Builder<TokenUpdateTransactionBody.Builder> tokenUpdate() {
        var transactionBody = TokenUpdateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setExpiry(timestamp())
                .setFeeScheduleKey(key())
                .setFreezeKey(key())
                .setKycKey(key())
                .setMemo(StringValue.of(text(16)))
                .setName(text(4))
                .setPauseKey(key())
                .setSupplyKey(key())
                .setSymbol(text(4))
                .setToken(tokenId())
                .setTreasury(accountId())
                .setWipeKey(key());
        return new Builder<>(TransactionType.TOKENUPDATE, transactionBody);
    }

    public Builder<TokenCreateTransactionBody.Builder> tokenCreate() {
        var tokenId = tokenId();
        var treasury = accountId();
        var transactionBody = TokenCreateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setExpiry(timestamp())
                .setFeeScheduleKey(key())
                .setFreezeKey(key())
                .setKycKey(key())
                .setMemo(String.valueOf(text(16)))
                .setName(text(4))
                .setPauseKey(key())
                .setSupplyKey(key())
                .setSymbol(text(4))
                .setTreasury(treasury)
                .addCustomFees(customFee(FIXED_FEE))
                .setWipeKey(key());
        return new Builder<>(TransactionType.TOKENCREATION, transactionBody)
                .receipt(r -> r.setTokenID(tokenId))
                .record(r -> r.addAutomaticTokenAssociations(
                        TokenAssociation.newBuilder().setAccountId(treasury).setTokenId(tokenId)));
    }

    public Builder<TokenDeleteTransactionBody.Builder> tokenDelete() {
        var builder = TokenDeleteTransactionBody.newBuilder().setToken(tokenId());
        return new Builder<>(TransactionType.TOKENDELETION, builder);
    }

    public Builder<TokenFeeScheduleUpdateTransactionBody.Builder> tokenFeeScheduleUpdate() {
        var transactionBody = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(tokenId())
                .addCustomFees(customFee(FIXED_FEE));
        return new Builder<>(TransactionType.TOKENFEESCHEDULEUPDATE, transactionBody);
    }

    public Builder<TokenWipeAccountTransactionBody.Builder> tokenWipe() {
        return tokenWipe(NON_FUNGIBLE_UNIQUE);
    }

    public Builder<TokenWipeAccountTransactionBody.Builder> tokenWipe(TokenType type) {
        var transactionBody = TokenWipeAccountTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        switch (type) {
            case FUNGIBLE_COMMON -> transactionBody.setAmount(1000L);
            case NON_FUNGIBLE_UNIQUE -> transactionBody.addSerialNumbers(1L);
        }
        return new Builder<>(TransactionType.TOKENWIPE, transactionBody).receipt(r -> r.setNewTotalSupply(2L));
    }

    public Builder<UncheckedSubmitBody.Builder> uncheckedSubmit() {
        var transactionBody = UncheckedSubmitBody.newBuilder().setTransactionBytes(bytes(32));
        return new Builder<>(TransactionType.UNCHECKEDSUBMIT, transactionBody);
    }

    public AccountID accountId() {
        return AccountID.newBuilder().setAccountNum(id()).build();
    }

    public ByteString bytes(int length) {
        byte[] bytes = randomBytes(length);
        return ByteString.copyFrom(bytes);
    }

    // Helper methods
    private AccountAmount accountAmount(AccountID accountID, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .build();
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private CryptoTransferTransactionBody.Builder cryptoTransferTransactionBody() {
        return CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(accountId())
                                .setAmount(-100))
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(accountId())
                                .setAmount(100)));
    }

    private TransactionSidecarRecord.Builder contractActions() {
        return TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addContractActions(contractAction())
                        .addContractActions(contractAction()
                                .setCallingAccount(accountId())
                                .setError(bytes(10))
                                .setRecipientAccount(accountId()))
                        .addContractActions(
                                contractAction().setTargetedAddress(bytes(20)).setRevertReason(bytes(10))));
    }

    private ContractAction.Builder contractAction() {
        return ContractAction.newBuilder()
                .setCallDepth(3)
                .setCallingContract(contractId())
                .setCallOperationType(CallOperationType.OP_CALL)
                .setCallType(ContractActionType.CALL)
                .setGas(100)
                .setGasUsed(50)
                .setInput(bytes(100))
                .setRecipientContract(contractId())
                .setOutput(bytes(256))
                .setValue(20);
    }

    private TransactionSidecarRecord.Builder contractBytecode(ContractID contractId) {
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(contractId)
                .setInitcode(bytes(2048))
                .setRuntimeBytecode(bytes(3048));
        return TransactionSidecarRecord.newBuilder().setBytecode(contractBytecode);
    }

    private ContractID contractId() {
        return ContractID.newBuilder().setContractNum(id()).build();
    }

    private TransactionSidecarRecord.Builder contractStateChanges(ContractID contractId) {
        var contractStateChange = ContractStateChange.newBuilder()
                .setContractId(contractId)
                .addStorageChanges(storageChange())
                .addStorageChanges(storageChange().setValueWritten(BytesValue.of(ByteString.EMPTY)));
        return TransactionSidecarRecord.newBuilder()
                .setStateChanges(ContractStateChanges.newBuilder().addContractStateChanges(contractStateChange));
    }

    private Duration duration(int seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    public BytesValue evmAddress() {
        return BytesValue.of(bytes(20));
    }

    private FileID fileId() {
        return FileID.newBuilder().setFileNum(id()).build();
    }

    private long id() {
        return id.incrementAndGet();
    }

    private Key key() {
        if (id.get() % 2 == 0) {
            return Key.newBuilder().setECDSASecp256K1(bytes(KEY_LENGTH_ECDSA)).build();
        } else {
            return Key.newBuilder().setEd25519(bytes(KEY_LENGTH_ED25519)).build();
        }
    }

    public NodeStake.Builder nodeStake() {
        long stake = id() * TINYBARS_IN_ONE_HBAR;
        long maxStake = 50_000_000_000L * TINYBARS_IN_ONE_HBAR / 26L;
        long minStake = stake / 2;
        return NodeStake.newBuilder()
                .setMaxStake(maxStake)
                .setMinStake(minStake)
                .setNodeId(id())
                .setRewardRate(id())
                .setStake(stake)
                .setStakeNotRewarded(TINYBARS_IN_ONE_HBAR)
                .setStakeRewarded(stake - TINYBARS_IN_ONE_HBAR);
    }

    public ScheduleID scheduleId() {
        return ScheduleID.newBuilder().setScheduleNum(id()).build();
    }

    private StorageChange.Builder storageChange() {
        return StorageChange.newBuilder()
                .setSlot(bytes(32))
                .setValueRead(bytes(32))
                .setValueWritten(BytesValue.of(bytes(32)));
    }

    public String text(int characters) {
        return RandomStringUtils.randomAlphanumeric(characters);
    }

    public Timestamp timestamp() {
        return timestamp(ChronoUnit.SECONDS);
    }

    public Timestamp timestamp(TemporalUnit unit) {
        return Utility.instantToTimestamp(now.plus(id() - INITIAL_ID, unit));
    }

    public TokenID tokenId() {
        return TokenID.newBuilder().setTokenNum(id()).build();
    }

    private TopicID topicId() {
        return TopicID.newBuilder().setTopicNum(id()).build();
    }

    public class Builder<T extends GeneratedMessageV3.Builder<T>> {

        private final TransactionType type;
        private final T transactionBody;
        private final SignatureMap.Builder signatureMap;
        private final TransactionBody.Builder transactionBodyWrapper;
        private final TransactionRecord.Builder transactionRecord;
        private final List<TransactionSidecarRecord.Builder> sidecarRecords;
        private final AccountID payerAccountId;
        private final RecordItem.RecordItemBuilder recordItemBuilder;

        private Predicate<EntityId> entityTransactionPredicate = persistProperties::shouldPersistEntityTransaction;

        private Builder(TransactionType type, T transactionBody) {
            this.payerAccountId = accountId();
            this.recordItemBuilder = RecordItem.builder().hapiVersion(RecordFile.HAPI_VERSION_NOT_SET);
            this.sidecarRecords = new ArrayList<>();
            this.signatureMap = defaultSignatureMap();
            this.type = type;
            this.transactionBody = transactionBody;
            this.transactionBodyWrapper = defaultTransactionBody();
            this.transactionRecord = defaultTransactionRecord();
        }

        public RecordItem build() {
            var field = transactionBodyWrapper.getDescriptorForType().findFieldByNumber(type.getProtoId());
            transactionBodyWrapper.setField(field, transactionBody.build());

            Transaction transaction = transaction().build();
            TransactionRecord record = transactionRecord.build();
            var contractId = record.getReceipt().getContractID();

            var sidecarRecords = this.sidecarRecords.stream()
                    .map(r -> {
                        if (r.hasBytecode() && !contractId.equals(ContractID.getDefaultInstance())) {
                            r.getBytecodeBuilder().setContractId(contractId);
                        }
                        return r.setConsensusTimestamp(record.getConsensusTimestamp())
                                .build();
                    })
                    .collect(Collectors.toList());

            return recordItemBuilder
                    .entityTransactionPredicate(entityTransactionPredicate)
                    .transactionRecordBytes(record.toByteArray())
                    .transactionBytes(transaction.toByteArray())
                    .sidecarRecords(sidecarRecords)
                    .build();
        }

        public Builder<T> entityTransactionPredicate(Predicate<EntityId> entityTransactionPredicate) {
            this.entityTransactionPredicate = entityTransactionPredicate;
            return this;
        }

        public Builder<T> receipt(Consumer<TransactionReceipt.Builder> consumer) {
            consumer.accept(transactionRecord.getReceiptBuilder());
            return this;
        }

        public Builder<T> record(Consumer<TransactionRecord.Builder> consumer) {
            consumer.accept(transactionRecord);
            return this;
        }

        public Builder<T> recordItem(Consumer<RecordItem.RecordItemBuilder> consumer) {
            consumer.accept(recordItemBuilder);
            return this;
        }

        public Builder<T> sidecarRecords(Consumer<List<TransactionSidecarRecord.Builder>> consumer) {
            consumer.accept(sidecarRecords);
            return this;
        }

        public Builder<T> status(ResponseCodeEnum status) {
            transactionRecord.getReceiptBuilder().setStatus(status);
            return this;
        }

        public Builder<T> signatureMap(Consumer<SignatureMap.Builder> consumer) {
            consumer.accept(signatureMap);
            return this;
        }

        public Builder<T> transactionBody(Consumer<T> consumer) {
            consumer.accept(transactionBody);
            return this;
        }

        public Builder<T> transactionBodyWrapper(Consumer<TransactionBody.Builder> consumer) {
            consumer.accept(transactionBodyWrapper);
            return this;
        }

        private SignatureMap.Builder defaultSignatureMap() {
            return SignatureMap.newBuilder()
                    .addSigPair(SignaturePair.newBuilder().setEd25519(bytes(32)).setPubKeyPrefix(bytes(16)));
        }

        private TransactionBody.Builder defaultTransactionBody() {
            return TransactionBody.newBuilder()
                    .setMemo(type.name())
                    .setNodeAccountID(NODE)
                    .setTransactionFee(100L)
                    .setTransactionID(Utility.getTransactionId(payerAccountId))
                    .setTransactionValidDuration(duration(120));
        }

        private TransactionRecord.Builder defaultTransactionRecord() {
            TransactionRecord.Builder transactionRecord = TransactionRecord.newBuilder()
                    .setConsensusTimestamp(timestamp())
                    .setMemoBytes(ByteString.copyFromUtf8(transactionBodyWrapper.getMemo()))
                    .setTransactionFee(transactionBodyWrapper.getTransactionFee())
                    .setTransactionHash(bytes(48))
                    .setTransactionID(transactionBodyWrapper.getTransactionID())
                    .setTransferList(TransferList.newBuilder()
                            .addAccountAmounts(accountAmount(payerAccountId, -3000L))
                            .addAccountAmounts(accountAmount(NODE, 1000L))
                            .addAccountAmounts(accountAmount(TREASURY, 2000L))
                            .build());
            transactionRecord.getReceiptBuilder().setStatus(ResponseCodeEnum.SUCCESS);
            return transactionRecord;
        }

        private Transaction.Builder transaction() {
            return Transaction.newBuilder()
                    .setSignedTransactionBytes(SignedTransaction.newBuilder()
                            .setBodyBytes(transactionBodyWrapper.build().toByteString())
                            .setSigMap(signatureMap)
                            .build()
                            .toByteString());
        }
    }
}
