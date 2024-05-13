/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.DomainBuilder.KEY_LENGTH_ECDSA;
import static com.hedera.mirror.common.domain.DomainBuilder.KEY_LENGTH_ED25519;
import static com.hedera.mirror.common.util.CommonUtils.nextBytes;
import static com.hedera.mirror.common.util.CommonUtils.toAccountID;
import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Int64Value;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.util.Version;
import org.springframework.stereotype.Component;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Component
@CustomLog
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RecordItemBuilder {

    private static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    private static final long INITIAL_ID = 1000L;
    private static final RealmID REALM_ID = RealmID.getDefaultInstance();
    private static final ShardID SHARD_ID = ShardID.getDefaultInstance();

    private final AtomicLong entityId = new AtomicLong(INITIAL_ID);
    private final Map<TransactionType, Function<com.hedera.mirror.common.domain.transaction.Transaction, Builder<?>>>
            builders = Map.of(
                    TransactionType.CONTRACTCALL, this::contractCall,
                    TransactionType.CONTRACTCREATEINSTANCE, this::contractCreate,
                    TransactionType.ETHEREUMTRANSACTION, this::ethereumTransaction
            );

    public Builder<?> forTransaction(com.hedera.mirror.common.domain.transaction.Transaction transaction) {
        return builders.get(TransactionType.of(transaction.getType())).apply(transaction);
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractCallTransactionBody.Builder> contractCall(com.hedera.mirror.common.domain.transaction.Transaction transaction) {
        ContractID contractId = ContractID.newBuilder()
                .setRealmNum(transaction.getEntityId().getRealm())
                .setShardNum(transaction.getEntityId().getShard())
                .setContractNum(transaction.getEntityId().getNum())
                .setEvmAddress(ByteString.copyFrom(toEvmAddress(transaction.getEntityId())))
                .build();
        ContractCallTransactionBody.Builder transactionBody = ContractCallTransactionBody.newBuilder()
                .setAmount(5_000L)
                .setContractID(contractId)
                .setFunctionParameters(bytes(64))
                .setGas(transaction.getMaxFee());

        return new Builder<>(transaction, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCallResult(contractFunctionResult(contractId).clearCreatedContractIDs()))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> r.add(contractActions()));
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractCreateTransactionBody.Builder> contractCreate(com.hedera.mirror.common.domain.transaction.Transaction transaction) {
        ContractID contractId = contractId();

        ContractCreateTransactionBody.Builder transactionBody = ContractCreateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccountId(toAccountID(transaction.getNodeAccountId()))
                .setAutoRenewPeriod(duration(30))
                .setConstructorParameters(bytes(64))
                .setDeclineReward(true)
                .setFileID(fileId())
                .setGas(transaction.getMaxFee())
                .setInitialBalance(20_000L)
                .setMaxAutomaticTokenAssociations(5)
                .setMemo(text(16))
                .setNewRealmAdminKey(key())
                .setProxyAccountID(toAccountID(transaction.getNodeAccountId()))
                .setRealmID(REALM_ID)
                .setShardID(SHARD_ID)
                .setStakedNodeId(1L);

        return new Builder<>(transaction, transactionBody)
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

    @SuppressWarnings("deprecation")
    public ContractFunctionResult.Builder contractFunctionResult(ContractID contractId) {
        return ContractFunctionResult.newBuilder()
                .setAmount(5_000L)
                .setBloom(bytes(256))
                .setContractCallResult(bytes(16))
                .setContractID(contractId)
                .addContractNonces(ContractNonceInfo.newBuilder()
                        .setContractId(contractId)
                        .setNonce(1)
                        .build())
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
                .setSignerNonce(Int64Value.of(10));
    }

    @SneakyThrows
    public Builder<EthereumTransactionBody.Builder> ethereumTransaction(com.hedera.mirror.common.domain.transaction.Transaction transaction) {
        var transactionBytes = Hex.decodeHex(LONDON_RAW_TX);
        EthereumTransactionBody.Builder transactionBody = EthereumTransactionBody.newBuilder()
                .setEthereumData(ByteString.copyFrom(transactionBytes))
                .setMaxGasAllowance(10_000L);

        var contractId = contractId();
        var digestedHash = bytes(32);
        var functionResult = contractFunctionResult(contractId);
        var builder = new Builder<>(transaction, transactionBody)
                .record(r -> r.setContractCallResult(functionResult).setEthereumHash(digestedHash))
                .recordItem(r -> r.hapiVersion(new Version(0, 47, 0)))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> r.add(contractActions()));

        if (transaction.getType() == TransactionType.CONTRACTCREATEINSTANCE.getProtoId()) {
            transactionBody.setCallData(fileId());
            builder.sidecarRecordBuilders
                    .get(1)
                    .getActionsBuilder()
                    .getContractActionsBuilder(0)
                    .setCallType(ContractActionType.CREATE);
            builder.record(r -> r.setContractCreateResult(functionResult));
        }

        return builder;
    }

    private TransactionSidecarRecord.Builder contractActions() {
        return TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addContractActions(contractAction().setCallDepth(0))
                        .addContractActions(contractAction()
                                .setCallDepth(1)
                                .setCallingAccount(accountId())
                                .setError(bytes(10))
                                .setRecipientAccount(accountId()))
                        .addContractActions(contractAction()
                                .setCallDepth(2)
                                .setTargetedAddress(bytes(20))
                                .setRevertReason(bytes(10))));
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

    private TransactionSidecarRecord.Builder contractStateChanges(ContractID contractId) {
        var contractStateChange = ContractStateChange.newBuilder()
                .setContractId(contractId)
                .addStorageChanges(storageChange())
                .addStorageChanges(storageChange().setValueWritten(BytesValue.of(ByteString.EMPTY)));
        return TransactionSidecarRecord.newBuilder()
                .setStateChanges(ContractStateChanges.newBuilder().addContractStateChanges(contractStateChange));
    }

    private StorageChange.Builder storageChange() {
        return StorageChange.newBuilder()
                .setSlot(bytes(32))
                .setValueRead(bytes(32))
                .setValueWritten(BytesValue.of(bytes(32)));
    }

    private FileID fileId() {
        return FileID.newBuilder().setFileNum(id()).build();
    }

    private ContractID contractId() {
        return ContractID.newBuilder().setContractNum(id()).build();
    }

    public AccountID accountId() {
        return AccountID.newBuilder().setAccountNum(id()).build();
    }

    private long id() {
        return entityId.incrementAndGet();
    }

    private Key key() {
        if (entityId.get() % 2 == 0) {
            return Key.newBuilder().setECDSASecp256K1(bytes(KEY_LENGTH_ECDSA)).build();
        } else {
            return Key.newBuilder().setEd25519(bytes(KEY_LENGTH_ED25519)).build();
        }
    }

    private NodeStake.Builder nodeStake() {
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

    private static Duration duration(int seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    private static ByteString bytes(int length) {
        byte[] bytes = nextBytes(length);
        return ByteString.copyFrom(bytes);
    }

    private static String text(int characters) {
        return RandomStringUtils.randomAlphanumeric(characters);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static class Builder<T extends GeneratedMessageV3.Builder<T>> {

        private final T transactionBody;
        private final TransactionType type;
        private final ResponseCodeEnum status;
        private final SignatureMap.Builder signatureMap;
        private final TransactionBody.Builder transactionBodyBuilder;
        private final TransactionRecord.Builder transactionRecordBuilder;
        private final List<TransactionSidecarRecord.Builder> sidecarRecordBuilders;
        private final RecordItem.RecordItemBuilder recordItemBuilder;

        private Builder(com.hedera.mirror.common.domain.transaction.Transaction transaction, T transactionBody) {
            final var consensusInstant = Instant.ofEpochSecond(0, transaction.getConsensusTimestamp());
            this.transactionBody = transactionBody;
            this.type = TransactionType.of(transaction.getType());
            this.status = ResponseCodeEnum.forNumber(transaction.getResult());
            this.signatureMap = defaultSignatureMap();
            this.transactionBodyBuilder = defaultTransactionBody(transaction);
            this.transactionRecordBuilder = defaultTransactionRecord(transaction)
                    .setConsensusTimestamp(timestamp(consensusInstant));
            this.sidecarRecordBuilders = new ArrayList<>();
            this.recordItemBuilder = RecordItem.builder()
                    .consensusTimestamp(convertToNanosMax(consensusInstant))
                    .hapiVersion(RecordFile.HAPI_VERSION_NOT_SET);
        }

        public Builder<T> receipt(Consumer<TransactionReceipt.Builder> consumer) {
            consumer.accept(transactionRecordBuilder.getReceiptBuilder());
            return this;
        }

        public Builder<T> record(Consumer<TransactionRecord.Builder> consumer) {
            consumer.accept(transactionRecordBuilder);
            return this;
        }

        public Builder<T> recordItem(Consumer<RecordItem.RecordItemBuilder> consumer) {
            consumer.accept(recordItemBuilder);
            return this;
        }

        public Builder<T> sidecarRecords(Consumer<List<TransactionSidecarRecord.Builder>> consumer) {
            consumer.accept(sidecarRecordBuilders);
            return this;
        }

        public RecordItem build() {
            final var field = transactionBodyBuilder.getDescriptorForType().findFieldByNumber(type.getProtoId());
            if (field != null) { // Not UNKNOWN transaction type
                transactionBodyBuilder.setField(field, transactionBody.build());
            }

            TransactionRecord record = transactionRecordBuilder.build();
            TransactionBody transactionBody = transactionBodyBuilder.build();
            Transaction transaction = transaction(transactionBody).build();
            final var contractId = record.getReceipt().getContractID();

            final var sidecarRecords = this.sidecarRecordBuilders.stream()
                    .map(r -> {
                        if (r.hasBytecode() && !contractId.equals(ContractID.getDefaultInstance())) {
                            r.getBytecodeBuilder().setContractId(contractId);
                        }
                        return r.setConsensusTimestamp(record.getConsensusTimestamp())
                                .build();
                    })
                    .collect(Collectors.toList());

            return recordItemBuilder
                    .transactionRecord(record)
                    .transaction(transaction)
                    .sidecarRecords(sidecarRecords)
                    .successful(status == ResponseCodeEnum.SUCCESS)
                    .build();
        }

        private SignatureMap.Builder defaultSignatureMap() {
            return SignatureMap.newBuilder()
                    .addSigPair(SignaturePair.newBuilder()
                            .setEd25519(bytes(32))
                            .setPubKeyPrefix(bytes(16)));
        }

        private TransactionBody.Builder defaultTransactionBody(com.hedera.mirror.common.domain.transaction.Transaction transaction) {
            return TransactionBody.newBuilder()
                    .setMemo(new String(transaction.getMemo()))
                    .setNodeAccountID(toAccountID(transaction.getNodeAccountId()))
                    .setTransactionFee(transaction.getMaxFee())
                    .setTransactionID(TransactionID.newBuilder()
                            .setAccountID(toAccountID(transaction.getPayerAccountId()))
                            .setTransactionValidStart(timestamp(Instant.ofEpochSecond(0, transaction.getValidStartNs())))
                            .build())
                    .setTransactionValidDuration(duration(transaction.getValidDurationSeconds().intValue()));
        }

        private TransactionRecord.Builder defaultTransactionRecord(com.hedera.mirror.common.domain.transaction.Transaction transaction) {
            TransactionRecord.Builder transactionRecord = TransactionRecord.newBuilder()
                    .setConsensusTimestamp(timestamp(Instant.ofEpochSecond(0, transaction.getConsensusTimestamp())))
                    .setMemoBytes(ByteString.copyFrom(transaction.getMemo()))
                    .setTransactionFee(transaction.getChargedTxFee())
                    .setTransactionHash(ByteString.copyFrom(transaction.getTransactionHash()))
                    .setTransactionID(TransactionID.newBuilder()
                            .setAccountID(toAccountID(transaction.getPayerAccountId()))
                            .setTransactionValidStart(timestamp(Instant.ofEpochSecond(0, transaction.getValidStartNs())))
                            .build())
                    .setTransferList(TransferList.getDefaultInstance());
            transactionRecord.getReceiptBuilder().setStatus(status);
            return transactionRecord;
        }

        private Transaction.Builder transaction(TransactionBody transactionBody) {
            return Transaction.newBuilder()
                    .setSignedTransactionBytes(SignedTransaction.newBuilder()
                            .setBodyBytes(transactionBody.toByteString())
                            .setSigMap(signatureMap)
                            .build()
                            .toByteString());
        }
    }
}
