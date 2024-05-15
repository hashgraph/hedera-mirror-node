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
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.List;
import lombok.experimental.UtilityClass;
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
        private static final byte[] CREATE_CONTRACT_TX_HASH = transactionHash();
        private static final Timestamp CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP = timestamp(1, 2000);

        public static @NotNull Transaction getCreateContractTransaction() {
            return getTransaction(CREATE_CONTRACT_TX_HASH, CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP, TransactionType.CONTRACTCREATEINSTANCE);
        }

        public static @NotNull RecordFile getCreateContractRecordFile() {
            return getRecordFile(CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * Contract Call Transaction
     */
    public static class ContractCall {
        private static final byte[] CONTRACT_CALL_TX_HASH = transactionHash();
        private static final Timestamp CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP = timestamp(2, 3000);

        public static @NotNull Transaction getContractCallTransaction() {
            return getTransaction(CONTRACT_CALL_TX_HASH, CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP, TransactionType.CONTRACTCALL);
        }

        public static @NotNull RecordFile getContractCallRecordFile() {
            return getRecordFile(CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * EIP-1559 ETH Transaction
     */
    public static class Eip1559 {
        private static final byte[] EIP_1559_TX_HASH = transactionHash();
        private static final Timestamp EIP1559_TX_CONSENSUS_TIMESTAMP = timestamp(3, 4000);

        public static @NotNull Transaction getEip1559Transaction() {
            return getTransaction(EIP_1559_TX_HASH, EIP1559_TX_CONSENSUS_TIMESTAMP, EIP1559_TYPE_BYTE);
        }

        public static @NotNull EthereumTransaction getEip1559EthTransaction() {
            return getEthereumTransaction(EIP_1559_TX_HASH, EIP1559_TX_CONSENSUS_TIMESTAMP, EIP1559_TYPE_BYTE);
        }

        public static @NotNull RecordFile getEip1559RecordFile() {
            return getRecordFile(EIP1559_TX_CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * EIP-2930 ETH Transaction
     */
    public static class Eip2930 {
        private static final byte[] EIP_2930_TX_HASH = transactionHash();
        private static final Timestamp EIP2930_TX_CONSENSUS_TIMESTAMP = timestamp(4, 5000);

        public static @NotNull Transaction getEip2930Transaction() {
            return getTransaction(EIP_2930_TX_HASH, EIP2930_TX_CONSENSUS_TIMESTAMP, EIP2930_TYPE_BYTE);
        }

        public static @NotNull EthereumTransaction getEip2930EthTransaction() {
            return getEthereumTransaction(EIP_2930_TX_HASH, EIP2930_TX_CONSENSUS_TIMESTAMP, EIP2930_TYPE_BYTE);
        }

        public static @NotNull RecordFile getEip2930RecordFile() {
            return getRecordFile(EIP2930_TX_CONSENSUS_TIMESTAMP);
        }
    }

    /**
     * Legacy ETH Transaction
     */
    public static class Legacy {
        private static final byte[] LEGACY_TX_HASH = transactionHash();
        private static final Timestamp LEGACY_TX_CONSENSUS_TIMESTAMP = timestamp(5, 6000);

        public static @NotNull Transaction getLegacyTransaction() {
            return getTransaction(LEGACY_TX_HASH, LEGACY_TX_CONSENSUS_TIMESTAMP, LEGACY_TYPE_BYTE);
        }

        public static @NotNull EthereumTransaction getLegacyEthTransaction() {
            return getEthereumTransaction(LEGACY_TX_HASH, LEGACY_TX_CONSENSUS_TIMESTAMP, LEGACY_TYPE_BYTE);
        }

        public static @NotNull RecordFile getLegacyRecordFile() {
            return getRecordFile(LEGACY_TX_CONSENSUS_TIMESTAMP);
        }
    }

    private static @NotNull Transaction getTransaction(final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final TransactionType transactionType) {
        return getTransaction(hash, consensusTimestamp, transactionType, LEGACY_TYPE_BYTE);
    }

    private static @NotNull Transaction getTransaction(final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final int typeByte) {
        return getTransaction(hash, consensusTimestamp, TransactionType.ETHEREUMTRANSACTION, typeByte);
    }

    @SuppressWarnings("deprecation")
    private static @NotNull Transaction getTransaction(final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final TransactionType transactionType,
                                                       final int typeByte) {
        final var transactionID = getTransactionID(consensusTimestamp);
        final var ethType = switch (typeByte) {
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
                    tx.transactionRecordBytes(getTransactionRecord(tx).build().toByteArray());
                    tx.transactionBytes(com.hederahashgraph.api.proto.java.Transaction.newBuilder()
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

    private static @NotNull TransactionBody.Builder getTransactionBody(final Transaction.TransactionBuilder transactionBuilder) {
        final var transaction = transactionBuilder.build();
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

    private static @NotNull TransactionRecord.Builder getTransactionRecord(final Transaction.TransactionBuilder transactionBuilder) {
        final var transaction = transactionBuilder.build();
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
        transactionRecord.getReceiptBuilder().setStatus(ResponseCodeEnum.forNumber(transaction.getResult()));
        return transactionRecord;
    }

    private static byte[] transactionHash() {
        return nextBytes(32);
    }
}
