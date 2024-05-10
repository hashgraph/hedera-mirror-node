package com.hedera.mirror.common.domain;

import static com.hedera.mirror.common.util.CommonUtils.toAccountID;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.security.SecureRandom;
import java.util.List;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@CustomLog
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TransactionMocks {

    // ETH Transaction Types
    private static final int LEGACY_TYPE_BYTE = 0;
    private static final int EIP2930_TYPE_BYTE = 1;
    private static final int EIP1559_TYPE_BYTE = 2;

    // Legacy Transaction - Contract Create
    private static final byte[] CREATE_CONTRACT_TX_HASH = generateTransactionHash();
    private static final Timestamp CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP = Timestamp.newBuilder()
            .setSeconds(1L)
            .setNanos(2000)
            .build();
    public final Transaction createContractTx;
    public final EthereumTransaction createContractEthTx;
    public final RecordFile createContractRecordFile;

    // Legacy Transaction - Contract Call
    private static final byte[] CONTRACT_CALL_TX_HASH = generateTransactionHash();
    private static final Timestamp CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP = Timestamp.newBuilder()
            .setSeconds(2L)
            .setNanos(3000)
            .build();
    public final Transaction contractCallTx;
    public final EthereumTransaction contractCallEthTx;
    public final RecordFile contractCallRecordFile;

    // EIP-1559 Transaction
    private static final byte[] EIP_1559_TX_HASH = generateTransactionHash();
    private static final Timestamp EIP1559_TX_CONSENSUS_TIMESTAMP = Timestamp.newBuilder()
            .setSeconds(3L)
            .setNanos(4000)
            .build();
    public final Transaction eip1559Tx;
    public final EthereumTransaction eip1559EthTx;
    public final RecordFile eip1559RecordFile;

    // EIP-2930 Transaction
    private static final byte[] EIP_2930_TX_HASH = generateTransactionHash();
    private static final Timestamp EIP2930_TX_CONSENSUS_TIMESTAMP = Timestamp.newBuilder()
            .setSeconds(4L)
            .setNanos(5000)
            .build();
    public final Transaction eip2930Tx;
    public final EthereumTransaction eip2930EthTx;
    public final RecordFile eip2930RecordFile;

    public TransactionMocks() {
        final var domainBuilder = new DomainBuilder();
        final var recordItemBuilder = new RecordItemBuilder();

        // Legacy Transaction - Contract Create
        this.createContractTx = getTransaction(domainBuilder, CREATE_CONTRACT_TX_HASH, CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP, TransactionType.CONTRACTCREATEINSTANCE);
        this.createContractEthTx = getEthereumTransaction(domainBuilder, CREATE_CONTRACT_TX_HASH, CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP, LEGACY_TYPE_BYTE);
        this.createContractRecordFile = getRecordFile(domainBuilder, recordItemBuilder, CREATE_CONTRACT_TX_CONSENSUS_TIMESTAMP, createContractTx);

        // Legacy Transaction - Contract Call
        this.contractCallTx = getTransaction(domainBuilder, CONTRACT_CALL_TX_HASH, CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP, TransactionType.CONTRACTCALL);
        this.contractCallEthTx = getEthereumTransaction(domainBuilder, CONTRACT_CALL_TX_HASH, CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP, LEGACY_TYPE_BYTE);
        this.contractCallRecordFile = getRecordFile(domainBuilder, recordItemBuilder, CONTRACT_CALL_TX_CONSENSUS_TIMESTAMP, contractCallTx);

        // EIP-1559 Transaction
        this.eip1559Tx = getTransaction(domainBuilder, EIP_1559_TX_HASH, EIP1559_TX_CONSENSUS_TIMESTAMP, EIP1559_TYPE_BYTE);
        this.eip1559EthTx = getEthereumTransaction(domainBuilder, EIP_1559_TX_HASH, EIP1559_TX_CONSENSUS_TIMESTAMP, EIP1559_TYPE_BYTE);
        this.eip1559RecordFile = getRecordFile(domainBuilder, recordItemBuilder, EIP1559_TX_CONSENSUS_TIMESTAMP, eip1559Tx);

        // EIP-2930 Transaction
        this.eip2930Tx = getTransaction(domainBuilder, EIP_2930_TX_HASH, EIP2930_TX_CONSENSUS_TIMESTAMP, EIP2930_TYPE_BYTE);
        this.eip2930EthTx = getEthereumTransaction(domainBuilder, EIP_2930_TX_HASH, EIP2930_TX_CONSENSUS_TIMESTAMP, EIP2930_TYPE_BYTE);
        this.eip2930RecordFile = getRecordFile(domainBuilder, recordItemBuilder, EIP2930_TX_CONSENSUS_TIMESTAMP, eip2930Tx);
    }

    private static @NotNull Transaction getTransaction(final DomainBuilder domainBuilder,
                                                       final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final TransactionType transactionType) {
        return getTransaction(domainBuilder, hash, consensusTimestamp, transactionType, LEGACY_TYPE_BYTE);
    }

    private static @NotNull Transaction getTransaction(final DomainBuilder domainBuilder,
                                                       final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final int typeByte) {
        return getTransaction(domainBuilder, hash, consensusTimestamp, TransactionType.ETHEREUMTRANSACTION, typeByte);
    }

    private static @NotNull Transaction getTransaction(final DomainBuilder domainBuilder,
                                                       final byte[] hash,
                                                       final Timestamp consensusTimestamp,
                                                       final TransactionType transactionType,
                                                       final int typeByte) {
        final var transactionID = getTransactionID(domainBuilder, consensusTimestamp);
        final var ethType = switch (typeByte) {
            case LEGACY_TYPE_BYTE -> "LEGACY";
            case EIP2930_TYPE_BYTE -> "EIP2930";
            case EIP1559_TYPE_BYTE -> "EIP1559";
            default -> "UNKNOWN";
        };
        final var memo = transactionType.name() + "_" + ethType;
        return domainBuilder.transaction()
                .customize(tx -> {
                    tx.type(transactionType.getProtoId());
                    tx.memo(memo.getBytes());
                    tx.transactionHash(hash);
                    tx.payerAccountId(EntityId.of(transactionID.getAccountID()));
                    tx.validStartNs(convertToNanosMax(
                            transactionID.getTransactionValidStart().getSeconds(),
                            transactionID.getTransactionValidStart().getNanos()));
                    tx.consensusTimestamp(convertToNanosMax(
                            EIP2930_TX_CONSENSUS_TIMESTAMP.getSeconds(),
                            EIP2930_TX_CONSENSUS_TIMESTAMP.getNanos()));
                })
                .get();
    }

    private static @NotNull EthereumTransaction getEthereumTransaction(final DomainBuilder domainBuilder,
                                                                       final byte[] hash,
                                                                       final Timestamp consensusTimestamp,
                                                                       final int typeByte) {
        return domainBuilder.ethereumTransaction(true)
                .customize(tx -> {
                    tx.type(typeByte);
                    tx.hash(hash);
                    tx.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos()));
                })
                .get();
    }

    private static @NotNull RecordFile getRecordFile(final DomainBuilder domainBuilder,
                                                     final RecordItemBuilder recordItemBuilder,
                                                     final Timestamp consensusTimestamp,
                                                     final Transaction transaction) {
        return domainBuilder.recordFile()
                .customize(recordFile -> {
                    recordFile.consensusStart(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos() - 1000));
                    recordFile.consensusEnd(convertToNanosMax(
                            consensusTimestamp.getSeconds(),
                            consensusTimestamp.getNanos() + 1000));
                    recordFile.count(1L);
                    recordFile.items(List.of(recordItemBuilder.forTransaction(transaction).build()));
                })
                .get();
    }

    private static @NotNull TransactionID getTransactionID(DomainBuilder domainBuilder, Timestamp consensusTimestamp) {
        return TransactionID.newBuilder()
                .setAccountID(toAccountID(domainBuilder.entityId()))
                .setTransactionValidStart(Timestamp.newBuilder()
                        .setSeconds(consensusTimestamp.getSeconds())
                        .setNanos(consensusTimestamp.getNanos() - 1000)
                        .build())
                .build();
    }

    private static byte[] generateTransactionHash() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
