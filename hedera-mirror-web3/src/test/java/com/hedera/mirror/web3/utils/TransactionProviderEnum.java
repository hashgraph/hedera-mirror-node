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

package com.hedera.mirror.web3.utils;

import static com.hedera.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static com.hedera.mirror.common.domain.transaction.TransactionType.CONTRACTCREATEINSTANCE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static com.hedera.mirror.common.util.CommonUtils.nextBytes;
import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public enum TransactionProviderEnum {

    CONTRACT_CREATE(Instant.ofEpochSecond(1, 2000), CONTRACTCREATEINSTANCE, EthTransactionType.LEGACY.getTypeByte()),
    CONTRACT_CALL(Instant.ofEpochSecond(2, 3000), CONTRACTCALL, EthTransactionType.LEGACY.getTypeByte()),
    EIP1559(Instant.ofEpochSecond(3, 4000), ETHEREUMTRANSACTION, EthTransactionType.EIP_1559.getTypeByte()),
    EIP2930(Instant.ofEpochSecond(4, 5000), ETHEREUMTRANSACTION, EthTransactionType.EIP_2930.getTypeByte()),
    LEGACY(Instant.ofEpochSecond(5, 6000), ETHEREUMTRANSACTION, EthTransactionType.LEGACY.getTypeByte());

    private final Instant consensusTimestamp;
    private final TransactionType transactionType;
    private final int typeByte;
    private final long amount = 1000L;
    private final byte[] hash = nextBytes(32);

    @NotNull
    private DomainBuilder domainBuilder = new DomainBuilder();

    @Setter
    private EntityId contractId = domainBuilder.entityId();

    @Setter
    private byte[] contractEvmAddress = domainBuilder.evmAddress();

    @Setter
    private byte[] contractAlias = domainBuilder.key();

    @Setter
    private EntityId payerAccountId = domainBuilder.entityId();

    @Setter
    private byte[] payerEvmAddress = domainBuilder.evmAddress();

    @Setter
    private byte[] payerAlias = domainBuilder.key();

    public TransactionProviderEnum customize(Consumer<TransactionProviderEnum> consumer) {
        consumer.accept(this);
        return this;
    }

    public void init(DomainBuilder domainBuilder) {
        this.domainBuilder = domainBuilder;
        this.contractId = domainBuilder.entityId();
        this.payerAccountId = domainBuilder.entityId();
    }

    public void init(DomainBuilder domainBuilder, Entity contract, Entity payerAccount) {
        this.domainBuilder = domainBuilder;
        setContract(contract);
        setPayerAccount(payerAccount);
    }

    public void setContract(Entity contract) {
        this.contractId = contract.toEntityId();
        this.contractEvmAddress = contract.getEvmAddress();
        this.contractAlias = contract.getAlias();
    }

    public void setPayerAccount(Entity payerAccount) {
        this.payerAccountId = payerAccount.toEntityId();
        this.payerEvmAddress = payerAccount.getEvmAddress();
        this.payerAlias = payerAccount.getAlias();
    }

    public DomainWrapper<Transaction, Transaction.TransactionBuilder> getTransaction() {
        return domainBuilder.transaction()
                .customize(tx -> {
                    tx.type(transactionType.getProtoId());
                    tx.memo("%s_%d".formatted(transactionType.name(), typeByte).getBytes());
                    tx.transactionHash(hash);
                    tx.payerAccountId(payerAccountId);
                    tx.entityId(contractId);
                    tx.validStartNs(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano() - 1000));
                    tx.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano()));
                });
    }

    public boolean hasEthTransaction() {
        return transactionType == ETHEREUMTRANSACTION;
    }

    public DomainWrapper<EthereumTransaction, EthereumTransaction.EthereumTransactionBuilder> getEthTransaction() {
        if (!hasEthTransaction()) {
            return domainBuilder.wrap(EthereumTransaction.builder(), () -> null);
        }
        return domainBuilder.ethereumTransaction(true)
                .customize(tx -> {
                    tx.type(typeByte);
                    tx.hash(hash);
                    tx.value(ByteBuffer.allocate(Long.BYTES).putLong(amount).array());
                    tx.payerAccountId(payerAccountId);
                    tx.toAddress(contractEvmAddress);
                    tx.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano()));
                    if (typeByte == EthTransactionType.EIP_1559.getTypeByte()) {
                        tx.maxGasAllowance(Long.MAX_VALUE);
                        tx.maxFeePerGas(nextBytes(32));
                        tx.maxPriorityFeePerGas(nextBytes(32));
                    }
                    if (typeByte == EthTransactionType.EIP_2930.getTypeByte()) {
                        tx.accessList(nextBytes(100));
                    }
                });
    }

    public DomainWrapper<RecordFile, RecordFile.RecordFileBuilder> getRecordFile() {
        return domainBuilder.recordFile()
                .customize(recordFile -> {
                    recordFile.consensusStart(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano() - 1000));
                    recordFile.consensusEnd(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano() + 1000));
                });
    }

    public DomainWrapper<ContractTransactionHash, ContractTransactionHash.ContractTransactionHashBuilder> getContractTransactionHash() {
        return domainBuilder.contractTransactionHash()
                .customize(contractTransactionHash -> {
                    contractTransactionHash.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano()));
                    contractTransactionHash.entityId(contractId.getId());
                    contractTransactionHash.hash(hash);
                    contractTransactionHash.payerAccountId(payerAccountId.getId());
                    contractTransactionHash.transactionResult(ResponseCodeEnum.SUCCESS_VALUE);
                });
    }

    public DomainWrapper<ContractResult, ContractResult.ContractResultBuilder<?, ?>> getContractResult() {
        return domainBuilder.contractResult()
                .customize(result -> {
                    result.amount(amount);
                    result.consensusTimestamp(convertToNanosMax(
                            consensusTimestamp.getEpochSecond(),
                            consensusTimestamp.getNano()));
                    result.contractId(contractId.getId());
                    result.createdContractIds(transactionType == CONTRACTCREATEINSTANCE ?
                            List.of(contractId.getId()) : Collections.emptyList());
                    result.functionParameters(ByteString.copyFrom(nextBytes(256)).toByteArray());
                    result.payerAccountId(payerAccountId);
                    result.senderId(payerAccountId);
                    result.transactionHash(hash);
                });
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> getContractEntity() {
        final long createdAt = convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano());
        return domainBuilder.entity(contractId.getId(), createdAt)
                .customize(entity -> {
                    entity.alias(contractAlias);
                    entity.evmAddress(contractEvmAddress);
                });
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> getSenderEntity() {
        return domainBuilder.entity(payerAccountId.getId(), domainBuilder.timestamp())
                .customize(entity -> {
                    entity.id(payerAccountId.getId());
                    entity.num(payerAccountId.getNum());
                    entity.evmAddress(payerEvmAddress);
                    entity.alias(payerAlias);
                    entity.balance(10000 * 100_000_000L);
                    entity.deleted(false);
                });
    }

    public static Address entityAddress(Entity entity) {
        if (entity == null) {
            return Address.ZERO;
        }
        if (entity.getEvmAddress() != null) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return toAddress(entity.toEntityId());
    }
}
