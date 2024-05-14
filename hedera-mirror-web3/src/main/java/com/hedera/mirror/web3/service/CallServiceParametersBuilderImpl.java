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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallServiceParametersBuilderImpl implements CallServiceParametersBuilder {

    private final TransactionService transactionService;
    private final EthereumTransactionService ethereumTransactionService;
    private final RecordFileService recordFileService;

    @Override
    @SneakyThrows
    public CallServiceParameters buildFromTransaction(@NonNull TransactionIdOrHashParameter transactionIdOrHash) {
        final Optional<Transaction> transaction;
        final Optional<EthereumTransaction> ethTransaction;

        if (transactionIdOrHash.isHash()) {
            ethTransaction = ethereumTransactionService.findByHash(transactionIdOrHash.hash().toByteArray());
            transaction = ethTransaction
                    .map(EthereumTransaction::getConsensusTimestamp)
                    .flatMap(transactionService::findByConsensusTimestamp);
        } else if (transactionIdOrHash.isTransactionId()) {
            transaction = transactionService.findByTransactionId(transactionIdOrHash.transactionID());
            ethTransaction = transaction
                    .map(Transaction::getConsensusTimestamp)
                    .flatMap(ethereumTransactionService::findByConsensusTimestamp);
        } else {
            throw new IllegalArgumentException("Invalid transaction ID or hash: %s".formatted(transactionIdOrHash));
        }

        return buildFromTransaction(transaction, ethTransaction);
    }

    private CallServiceParameters buildFromTransaction(Optional<Transaction> transaction,
                                                       Optional<EthereumTransaction> ethTransaction) throws InvalidProtocolBufferException {
        if (transaction.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found");
        }

        final RecordFile recordFile = recordFileService
                .findRecordFileForTimestamp(transaction.get().getConsensusTimestamp())
                .orElseThrow(() -> new IllegalArgumentException("Record file with transaction not found"));

        final RecordItem recordItem = RecordItem.builder()
                .hapiVersion(recordFile.getHapiVersion())
                .transactionRecord(TransactionRecord.parseFrom(transaction.get().getTransactionRecordBytes()))
                .transaction(com.hederahashgraph.api.proto.java.Transaction.parseFrom(transaction.get().getTransactionBytes()))
                .ethereumTransaction(ethTransaction.orElse(null))
                .build();

        return buildFromRecordItem(recordItem, recordFile.getIndex());
    }

    private CallServiceParameters buildFromRecordItem(RecordItem recordItem, Long blockNumber) {
        return CallServiceParameters.builder()
                .sender(new HederaEvmAccount(getSenderAddress(recordItem)))
                .receiver(getReceiverAddress(recordItem))
                .gas(getGasLimit(recordItem))
                .value(getValue(recordItem).longValue())
                .callData(getCallData(recordItem))
                .isStatic(false)
                .callType(ETH_DEBUG_TRACE_TRANSACTION)
                .isEstimate(false)
                .block(BlockType.of(blockNumber.toString()))
                .build();
    }

    private Address getSenderAddress(RecordItem recordItem) {
        final EntityId senderId;
        if (recordItem.getTransactionRecord().hasContractCreateResult()
                && recordItem.getTransactionRecord().getContractCreateResult().hasSenderId()) {
            senderId = EntityId.of(recordItem.getTransactionRecord().getContractCreateResult().getSenderId());
        } else if (recordItem.getTransactionRecord().hasContractCallResult()
                && recordItem.getTransactionRecord().getContractCallResult().hasSenderId()) {
            senderId = EntityId.of(recordItem.getTransactionRecord().getContractCallResult().getSenderId());
        } else {
            senderId = recordItem.getPayerAccountId();
        }

        return Address.fromHexString(Bytes.of(DomainUtils.toEvmAddress(senderId)).toHexString());
    }

    private Address getReceiverAddress(RecordItem recordItem) {
        return Optional.ofNullable(recordItem.getEthereumTransaction())
                .map(EthereumTransaction::getToAddress)
                .map(Bytes::of)
                .map(Bytes::toHexString)
                .map(Address::fromHexString)
                .orElse(getReceiverAddress(recordItem.getTransactionBody()));
    }

    private Address getReceiverAddress(TransactionBody transactionBody) {
        if (transactionBody.hasContractCall()) {
            final byte[] evmAddress = transactionBody.getContractCall().getContractID().getEvmAddress().toByteArray();
            return Address.fromHexString(Bytes.of(evmAddress).toHexString());
        }
        return Address.ZERO;
    }

    private Long getGasLimit(RecordItem recordItem) {
        return Optional.ofNullable(recordItem.getEthereumTransaction())
                .map(EthereumTransaction::getGasLimit)
                .orElse(getGasLimit(recordItem.getTransactionBody()));
    }

    private Long getGasLimit(TransactionBody transactionBody) {
        if (transactionBody.hasContractCall()) {
            return transactionBody.getContractCall().getGas();
        }
        if (transactionBody.hasContractCreateInstance()) {
            return transactionBody.getContractCreateInstance().getGas();
        }
        return 0L;
    }

    private BigInteger getValue(RecordItem recordItem) {
        return Optional.ofNullable(recordItem.getEthereumTransaction())
                .map(EthereumTransaction::getValue)
                .map(BigInteger::new)
                .orElse(getValue(recordItem.getTransactionBody()));
    }

    private BigInteger getValue(TransactionBody transactionBody) {
        if (transactionBody.hasContractCall()) {
            return BigInteger.valueOf(transactionBody.getContractCall().getAmount());
        }
        if (transactionBody.hasContractCreateInstance()) {
            return BigInteger.valueOf(transactionBody.getContractCreateInstance().getInitialBalance());
        }
        return BigInteger.ZERO;
    }

    private Bytes getCallData(RecordItem recordItem) {
        return Optional.ofNullable(recordItem.getEthereumTransaction())
                .map(EthereumTransaction::getCallData)
                .map(Bytes::of)
                .orElse(getCallData(recordItem.getTransactionBody()));
    }

    private Bytes getCallData(TransactionBody transactionBody) {
        if (transactionBody.hasContractCall()) {
            return Bytes.of(transactionBody.getContractCall().getFunctionParameters().toByteArray());
        }
        if (transactionBody.hasContractCreateInstance()) {
            return Bytes.of(transactionBody.getContractCreateInstance().getConstructorParameters().toByteArray());
        }
        return Bytes.EMPTY;
    }
}
