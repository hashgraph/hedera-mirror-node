package com.hedera.mirror.importer.parser.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.DomainBuilder.KEY_LENGTH_ECDSA;
import static com.hedera.mirror.common.domain.DomainBuilder.KEY_LENGTH_ED25519;
import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractStateChange;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.StorageChange;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.util.Version;
import org.web3j.crypto.Hash;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.util.Utility;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RecordItemBuilder {

    public static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";
    public static final AccountID STAKING_REWARD_ACCOUNT = AccountID.newBuilder().setAccountNum(800).build();

    private static final AccountID NODE = AccountID.newBuilder().setAccountNum(3).build();
    private static final RealmID REALM_ID = RealmID.getDefaultInstance();
    private static final ShardID SHARD_ID = ShardID.getDefaultInstance();
    private static final AccountID TREASURY = AccountID.newBuilder().setAccountNum(98).build();

    private final AtomicLong id = new AtomicLong(1000L);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();

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

    public Builder<ContractCallTransactionBody.Builder> contractCall(ContractID contractId) {
        ContractCallTransactionBody.Builder transactionBody = ContractCallTransactionBody.newBuilder()
                .setAmount(5_000L)
                .setContractID(contractId)
                .setFunctionParameters(bytes(64))
                .setGas(10_000L);

        return new Builder<>(TransactionType.CONTRACTCALL, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCallResult(contractFunctionResult(contractId)));
    }

    public Builder<ContractCreateTransactionBody.Builder> contractCreate() {
        return contractCreate(contractId());
    }

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
                .record(r -> r.setContractCreateResult(contractFunctionResult(contractId)
                        .addCreatedContractIDs(contractId)));
    }

    public Builder<ContractDeleteTransactionBody.Builder> contractDelete() {
        var contractId = contractId();
        ContractDeleteTransactionBody.Builder transactionBody = ContractDeleteTransactionBody.newBuilder()
                .setContractID(contractId)
                .setTransferAccountID(accountId());

        return new Builder<>(TransactionType.CONTRACTDELETEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId));
    }

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
                .addStateChanges(ContractStateChange.newBuilder()
                        .setContractID(contractId)
                        .addStorageChanges(storageChange())
                        .addStorageChanges(storageChange().setValueWritten(BytesValue.of(ByteString.EMPTY)))
                        .build());
    }

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

    public Builder<CryptoCreateTransactionBody.Builder> cryptoCreate() {
        var builder = CryptoCreateTransactionBody.newBuilder()
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
        return new Builder<>(TransactionType.CRYPTOCREATEACCOUNT, builder)
                .receipt(r -> r.setAccountID(accountId()));
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
        return new Builder<>(TransactionType.CRYPTOUPDATEACCOUNT, builder)
                .receipt(r -> r.setAccountID(accountId));
    }

    @SneakyThrows
    public Builder<EthereumTransactionBody.Builder> ethereumTransaction(boolean create) {
        var transactionBytes = Hex.decodeHex(LONDON_RAW_TX);
        EthereumTransactionBody.Builder transactionBody = EthereumTransactionBody.newBuilder()
                .setEthereumData(ByteString.copyFrom(transactionBytes))
                .setMaxGasAllowance(10_000L);

        var contractId = contractId();
        var digestedHash = ByteString.copyFrom(Hash.sha3(transactionBytes));
        if (create) {
            transactionBody.setCallData(fileId());
            return new Builder<>(TransactionType.ETHEREUMTRANSACTION, transactionBody)
                    .record(r -> r
                            .setContractCreateResult(contractFunctionResult(contractId))
                            .setEthereumHash(digestedHash));
        } else {
            return new Builder<>(TransactionType.ETHEREUMTRANSACTION, transactionBody)
                    .record(r -> r
                            .setContractCallResult(contractFunctionResult(contractId))
                            .setEthereumHash(digestedHash));
        }
    }

    public Builder<NodeStakeUpdateTransactionBody.Builder> nodeStakeUpdate() {
        var builder = NodeStakeUpdateTransactionBody.newBuilder()
                .setEndOfStakingPeriod(timestamp())
                .setRewardRate(id() * TINYBARS_IN_ONE_HBAR)
                .addNodeStake(nodeStake());
        return new Builder<>(TransactionType.NODESTAKEUPDATE, builder);
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
        return new Builder<>(TransactionType.SCHEDULECREATE, builder);
    }

    public Builder<TokenMintTransactionBody.Builder> tokenMint(TokenType tokenType) {
        var transactionBody = TokenMintTransactionBody.newBuilder().setToken(tokenId());

        if (tokenType == FUNGIBLE_COMMON) {
            transactionBody.setAmount(1000L);
        } else {
            transactionBody.addMetadata(bytes(16)).addMetadata(bytes(16));
        }

        return new Builder<>(TransactionType.TOKENMINT, transactionBody);
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

    // Helper methods
    public AccountAmount accountAmount(AccountID accountID, long amount) {
        return AccountAmount.newBuilder().setAccountID(accountID).setAmount(amount).build();
    }

    public AccountID accountId() {
        return AccountID.newBuilder().setAccountNum(id()).build();
    }

    public ByteString bytes(int length) {
        byte[] bytes = randomBytes(length);
        return ByteString.copyFrom(bytes);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private CryptoTransferTransactionBody.Builder cryptoTransferTransactionBody() {
        return CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder().setAccountID(accountId()).setAmount(-100))
                        .addAccountAmounts(AccountAmount.newBuilder().setAccountID(accountId()).setAmount(100)));
    }

    private ContractID contractId() {
        return ContractID.newBuilder().setContractNum(id()).build();
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
        var stake = id() * TINYBARS_IN_ONE_HBAR;
        return NodeStake.newBuilder()
                .setNodeId(id())
                .setStake(stake)
                .setStakeRewarded(stake - 100L);
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
        return Utility.instantToTimestamp(now.plusSeconds(id()));
    }

    public TokenID tokenId() {
        return TokenID.newBuilder().setTokenNum(id()).build();
    }

    private TopicID topicId() {
        return TopicID.newBuilder().setTopicNum(id()).build();
    }

    public class Builder<T extends GeneratedMessageV3.Builder> {
        private final TransactionType type;
        private final T transactionBody;
        private final SignatureMap.Builder signatureMap;
        private final TransactionBody.Builder transactionBodyWrapper;
        private final TransactionRecord.Builder transactionRecord;
        private final AccountID payerAccountId;
        private RecordItem parent;
        private Version hapiVersion = RecordFile.HAPI_VERSION_NOT_SET;

        private Builder(TransactionType type, T transactionBody) {
            payerAccountId = accountId();
            this.type = type;
            this.transactionBody = transactionBody;
            signatureMap = defaultSignatureMap();
            transactionBodyWrapper = defaultTransactionBody();
            transactionRecord = defaultTransactionRecord();
        }

        public RecordItem build() {
            var field = transactionBodyWrapper.getDescriptorForType().findFieldByNumber(type.getProtoId());
            transactionBodyWrapper.setField(field, transactionBody.build());

            Transaction transaction = transaction().build();
            TransactionRecord record = transactionRecord.build();
            return RecordItem.builder()
                    .hapiVersion(hapiVersion)
                    .parent(parent)
                    .recordBytes(record.toByteArray())
                    .transactionBytes(transaction.toByteArray())
                    .build();
        }

        public Builder<T> hapiVersion(Version hapiVersion) {
            this.hapiVersion = hapiVersion;
            return this;
        }

        public Builder<T> parent(RecordItem parent) {
            this.parent = parent;
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
                    .addSigPair(SignaturePair.newBuilder()
                            .setEd25519(bytes(32))
                            .setPubKeyPrefix(bytes(16)));
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
                            .toByteString()
                    );
        }
    }
}
