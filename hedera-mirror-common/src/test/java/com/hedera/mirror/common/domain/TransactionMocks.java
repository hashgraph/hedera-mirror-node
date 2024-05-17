/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.CommonUtils.duration;
import static com.hedera.mirror.common.util.CommonUtils.nextBytes;
import static com.hedera.mirror.common.util.CommonUtils.timestamp;
import static com.hedera.mirror.common.util.CommonUtils.toAccountID;
import static com.hedera.mirror.common.util.CommonUtils.toContractID;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class TransactionMocks {

    private static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();

    /**
     * ETH Transaction Types
     */
    private static final int LEGACY_TYPE_BYTE = 0;
    private static final int EIP2930_TYPE_BYTE = 1;
    private static final int EIP1559_TYPE_BYTE = 2;

    /**
     * Contract Create Transaction
     */
    public static class ContractCreate {
        private static final byte[] HASH = transactionHash();
        private static final Timestamp CONSENSUS_TIMESTAMP = timestamp(1, 2000);

        public static @NotNull Transaction getCreateContractTransaction() {
            return getTransaction(HASH, CONSENSUS_TIMESTAMP, TransactionType.CONTRACTCREATEINSTANCE);
        }

        public static @NotNull RecordFile getCreateContractRecordFile() {
            return getRecordFile(CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * Contract Call Transaction
     */
    public static class ContractCall {
        private static final byte[] HASH = transactionHash();
        private static final Timestamp CONSENSUS_TIMESTAMP = timestamp(2, 3000);

        public static @NotNull Transaction getContractCallTransaction() {
            return getTransaction(HASH, CONSENSUS_TIMESTAMP, TransactionType.CONTRACTCALL);
        }

        public static @NotNull RecordFile getContractCallRecordFile() {
            return getRecordFile(CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * EIP-1559 ETH Transaction
     */
    public static class Eip1559 {
        private static final byte[] HASH = transactionHash();
        private static final Timestamp CONSENSUS_TIMESTAMP = timestamp(3, 4000);

        public static @NotNull Transaction getEip1559Transaction() {
            return getTransaction(HASH, CONSENSUS_TIMESTAMP, EIP1559_TYPE_BYTE);
        }

        public static @NotNull EthereumTransaction getEip1559EthTransaction() {
            return getEthereumTransaction(HASH, CONSENSUS_TIMESTAMP, EIP1559_TYPE_BYTE);
        }

        public static @NotNull RecordFile getEip1559RecordFile() {
            return getRecordFile(CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * EIP-2930 ETH Transaction
     */
    public static class Eip2930 {
        private static final byte[] HASH = transactionHash();
        private static final Timestamp CONSENSUS_TIMESTAMP = timestamp(4, 5000);

        public static @NotNull Transaction getEip2930Transaction() {
            return getTransaction(HASH, CONSENSUS_TIMESTAMP, EIP2930_TYPE_BYTE);
        }

        public static @NotNull EthereumTransaction getEip2930EthTransaction() {
            return getEthereumTransaction(HASH, CONSENSUS_TIMESTAMP, EIP2930_TYPE_BYTE);
        }

        public static @NotNull RecordFile getEip2930RecordFile() {
            return getRecordFile(CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * Legacy ETH Transaction
     */
    public static class Legacy {
        private static final byte[] HASH = transactionHash();
        private static final Timestamp CONSENSUS_TIMESTAMP = timestamp(5, 6000);

        public static @NotNull Transaction getLegacyTransaction() {
            return getTransaction(HASH, CONSENSUS_TIMESTAMP, LEGACY_TYPE_BYTE);
        }

        public static @NotNull EthereumTransaction getLegacyEthTransaction() {
            return getEthereumTransaction(HASH, CONSENSUS_TIMESTAMP, LEGACY_TYPE_BYTE);
        }

        public static @NotNull RecordFile getLegacyRecordFile() {
            return getRecordFile(CONSENSUS_TIMESTAMP);
        }
    }

    private static @NotNull Transaction getTransaction(final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final TransactionType transactionType) {
        return getTransaction(hash, consensusTimestamp, transactionType, LEGACY_TYPE_BYTE);
    }

    private static @NotNull Transaction getTransaction(final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final int ethTypeByte) {
        return getTransaction(hash, consensusTimestamp, TransactionType.ETHEREUMTRANSACTION, ethTypeByte);
    }

    @SuppressWarnings("deprecation")
    private static @NotNull Transaction getTransaction(final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final TransactionType transactionType,
                                                       final int ethTypeByte) {
        final var transactionID = getTransactionID(consensusTimestamp);
        final var ethType = switch (ethTypeByte) {
            case LEGACY_TYPE_BYTE -> "LEGACY";
            case EIP2930_TYPE_BYTE -> "EIP2930";
            case EIP1559_TYPE_BYTE -> "EIP1559";
            default -> "UNKNOWN";
        };
        final var memo = transactionType.name() + "_" + ethType;
        return DOMAIN_BUILDER.transaction()
                .customize(tx -> {
                    tx.type(transactionType.getProtoId());
                    tx.memo(memo.getBytes());
                    tx.transactionHash(hash);
                    tx.payerAccountId(EntityId.of(transactionID.getAccountID()));
                    tx.validStartNs(convertToNanosMax(
                            transactionID.getTransactionValidStart().getSeconds(),
                            transactionID.getTransactionValidStart().getNanos()));
                    tx.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos()));
                    tx.transactionRecordBytes(
                            getTransactionRecord(tx).build().toByteArray());
                    tx.transactionBytes(
                            com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                                    .setBodyBytes(getTransactionBody(tx).build().toByteString())
                                    .build()
                                    .toByteArray());
                })
                .get();
    }

    private static @NotNull EthereumTransaction getEthereumTransaction(final byte[] hash,
                                                                       final Timestamp consensusTimestamp,
                                                                       final int typeByte) {
        return DOMAIN_BUILDER.ethereumTransaction(true)
                .customize(tx -> {
                    tx.type(typeByte);
                    tx.hash(hash);
                    tx.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos()));
                    if (typeByte == EIP1559_TYPE_BYTE) {
                        tx.maxGasAllowance(Long.MAX_VALUE);
                        tx.maxFeePerGas(nextBytes(32));
                        tx.maxPriorityFeePerGas(nextBytes(32));
                    }
                    if (typeByte == EIP2930_TYPE_BYTE) {
                        tx.accessList(nextBytes(100));
                    }
                })
                .get();
    }

    private static @NotNull RecordFile getRecordFile(final Timestamp consensusTimestamp) {
        return DOMAIN_BUILDER.recordFile()
                .customize(recordFile -> {
                    recordFile.consensusStart(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos() - 1000));
                    recordFile.consensusEnd(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos() + 1000));
                    recordFile.count(1L);
                    recordFile.items(List.of());
                })
                .get();
    }

    private static @NotNull TransactionID getTransactionID(final Timestamp consensusTimestamp) {
        return TransactionID.newBuilder()
                .setAccountID(toAccountID(DOMAIN_BUILDER.entityId()))
                .setTransactionValidStart(Timestamp.newBuilder()
                        .setSeconds(consensusTimestamp.getSeconds())
                        .setNanos(consensusTimestamp.getNanos() - 1000)
                        .build())
                .build();
    }

    @SneakyThrows
    private static @NotNull TransactionBody.Builder getTransactionBody(final Transaction.TransactionBuilder transactionBuilder) {
        final var transaction = transactionBuilder.build();
        final var transactionBodyBuilder = TransactionBody.newBuilder()
                .setMemo(new String(transaction.getMemo()))
                .setNodeAccountID(toAccountID(transaction.getNodeAccountId()))
                .setTransactionFee(transaction.getMaxFee())
                .setTransactionID(TransactionID.newBuilder()
                        .setAccountID(toAccountID(transaction.getPayerAccountId()))
                        .setTransactionValidStart(timestamp(transaction.getValidStartNs()))
                        .build())
                .setTransactionValidDuration(duration(transaction.getValidDurationSeconds().intValue()));
        if (transaction.getType() == TransactionType.CONTRACTCALL.getProtoId()) {
            transactionBodyBuilder.setContractCall(ContractCallTransactionBody.newBuilder()
                    .setGas(transaction.getMaxFee())
                    .setAmount(5_000L)
                    .setContractID(toContractID(transaction.getEntityId()))
                    .setFunctionParameters(ByteString.copyFrom(nextBytes(64)))
                    .build());
        } else if (transaction.getType() == TransactionType.CONTRACTCREATEINSTANCE.getProtoId()) {
            transactionBodyBuilder.setContractCreateInstance(ContractCreateTransactionBody.newBuilder()
                    .setGas(transaction.getMaxFee())
                    .setInitialBalance(5_000L)
                    .setInitcode(ByteString.copyFromUtf8("init code"))
                    .setConstructorParameters(ByteString.copyFrom(nextBytes(64)))
                    .build());
        }
        return transactionBodyBuilder;
    }

    @SuppressWarnings("deprecation")
    private static @NotNull TransactionRecord.Builder getTransactionRecord(final Transaction.TransactionBuilder transactionBuilder) {
        final var transaction = transactionBuilder.build();
        final var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(timestamp(transaction.getConsensusTimestamp()))
                .setMemoBytes(ByteString.copyFrom(transaction.getMemo()))
                .setTransactionFee(transaction.getChargedTxFee())
                .setTransactionHash(ByteString.copyFrom(transaction.getTransactionHash()))
                .setTransactionID(TransactionID.newBuilder()
                        .setAccountID(toAccountID(transaction.getPayerAccountId()))
                        .setTransactionValidStart(timestamp(transaction.getValidStartNs()))
                        .build())
                .setTransferList(TransferList.getDefaultInstance())
                .setReceipt(getTransactionReceipt(transaction));

        if (transaction.getType() == TransactionType.CONTRACTCALL.getProtoId()) {
            transactionRecord.setContractCallResult(getContractFunctionResult(transaction));
        } else if (transaction.getType() == TransactionType.CONTRACTCREATEINSTANCE.getProtoId()) {
            transactionRecord.setContractCreateResult(getContractFunctionResult(transaction));
        }

        return transactionRecord;
    }

    private static @NotNull TransactionReceipt getTransactionReceipt(final Transaction transaction) {
        final var transactionReceipt = TransactionReceipt.newBuilder()
                .setStatus(ResponseCodeEnum.forNumber(transaction.getResult()));
        if (transaction.getType() == TransactionType.CONTRACTCREATEINSTANCE.getProtoId()) {
            return transactionReceipt
                    .setContractID(toContractID(transaction.getEntityId()))
                    .build();
        } else {
            return transactionReceipt.build();
        }
    }

    @SuppressWarnings("deprecation")
    private static @NotNull ContractFunctionResult getContractFunctionResult(final Transaction transaction) {
        final var contractFunctionResult = ContractFunctionResult.newBuilder()
                .setBloom(ByteString.copyFrom(nextBytes(256)))
                .setGasUsed(transaction.getChargedTxFee())
                .setSenderId(toAccountID(transaction.getPayerAccountId()));

        if (transaction.getResult() != ResponseCodeEnum.SUCCESS.getNumber()) {
            final var errorMessage = RandomStringUtils.randomAlphanumeric((10));
            contractFunctionResult.setErrorMessage(errorMessage);
            contractFunctionResult.setErrorMessageBytes(ByteString.copyFrom(errorMessage.getBytes()));
        }

        if (transaction.getType() == TransactionType.CONTRACTCALL.getProtoId()) {
            final var contractId = toContractID(transaction.getEntityId());
            contractFunctionResult
                    .setContractCallResult(ByteString.copyFrom(nextBytes(16)))
                    .setContractID(contractId);
        } else if (transaction.getType() == TransactionType.CONTRACTCREATEINSTANCE.getProtoId()) {
            final var contractId = toContractID(transaction.getEntityId());
            contractFunctionResult
                    .setEvmAddress(BytesValue.of(contractId.getEvmAddress()))
                    .addCreatedContractIDs(contractId)
                    .addContractNonces(ContractNonceInfo.newBuilder()
                            .setContractId(contractId)
                            .setNonce(1))
                    .addLogInfo(ContractLoginfo.newBuilder()
                            .setBloom(ByteString.copyFrom(nextBytes(256)))
                            .setContractID(contractId)
                            .setData(ByteString.copyFrom(nextBytes(128)))
                            .addTopic(ByteString.copyFrom(nextBytes(32)))
                            .addTopic(ByteString.copyFrom(nextBytes(32)))
                            .addTopic(ByteString.copyFrom(nextBytes(32)))
                            .addTopic(ByteString.copyFrom(nextBytes(32))));
        }

        return contractFunctionResult.build();
    }

    private static byte[] transactionHash() {
        return nextBytes(32);
    }
}
