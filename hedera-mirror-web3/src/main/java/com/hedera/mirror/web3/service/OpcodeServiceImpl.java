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

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.rest.model.Opcode;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.repository.ContractResultRepository;
import com.hedera.mirror.web3.repository.ContractTransactionHashRepository;
import com.hedera.mirror.web3.repository.EthereumTransactionRepository;
import com.hedera.mirror.web3.repository.TransactionRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class OpcodeServiceImpl implements OpcodeService {

    private final RecordFileService recordFileService;
    private final ContractCallService contractCallService;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final ContractResultRepository contractResultRepository;
    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Override
    public OpcodesResponse processOpcodeCall(
            @NonNull TransactionIdOrHashParameter transactionIdOrHashParameter, @NonNull OpcodeTracerOptions options) {
        final CallServiceParameters params = buildCallServiceParameters(transactionIdOrHashParameter);
        final OpcodesProcessingResult result = contractCallService.processOpcodeCall(params, options);
        return buildOpcodesResponse(result);
    }

    private CallServiceParameters buildCallServiceParameters(
            @NonNull TransactionIdOrHashParameter transactionIdOrHash) {
        final Long consensusTimestamp;
        final Optional<EthereumTransaction> ethereumTransaction;

        switch (transactionIdOrHash) {
            case TransactionHashParameter transactionHash -> {
                ContractTransactionHash contractTransactionHash = contractTransactionHashRepository
                        .findByHash(transactionHash.hash().toArray())
                        .orElseThrow(() -> new EntityNotFoundException("Contract transaction hash not found"));

                consensusTimestamp = contractTransactionHash.getConsensusTimestamp();

                ethereumTransaction = ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        consensusTimestamp, EntityId.of(contractTransactionHash.getPayerAccountId()));
            }
            case TransactionIdParameter transactionId -> {
                final var validStartNs = convertToNanosMax(transactionId.validStart());
                Transaction transaction = transactionRepository
                        .findByPayerAccountIdAndValidStartNs(transactionId.payerAccountId(), validStartNs)
                        .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));

                consensusTimestamp = transaction.getConsensusTimestamp();

                ethereumTransaction = ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        consensusTimestamp, transaction.getPayerAccountId());
            }
        }

        return buildCallServiceParameters(consensusTimestamp, ethereumTransaction);
    }

    private OpcodesResponse buildOpcodesResponse(@NonNull OpcodesProcessingResult result) {
        final Optional<Address> recipientAddress =
                result.transactionProcessingResult().getRecipient();

        final Optional<Entity> recipientEntity =
                recipientAddress.flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()));

        return new OpcodesResponse()
                .address(recipientEntity
                        .map(this::getEntityAddress)
                        .map(Address::toHexString)
                        .orElse(Address.ZERO.toHexString()))
                .contractId(recipientEntity
                        .map(Entity::toEntityId)
                        .map(EntityId::toString)
                        .orElse(null))
                .failed(!result.transactionProcessingResult().isSuccessful())
                .gas(result.transactionProcessingResult().getGasUsed())
                .opcodes(result.opcodes().stream()
                        .map(opcode -> new Opcode()
                                .depth(opcode.depth())
                                .gas(opcode.gas())
                                .gasCost(opcode.gasCost())
                                .op(opcode.op())
                                .pc(opcode.pc())
                                .reason(opcode.reason())
                                .stack(opcode.stack().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .memory(opcode.memory().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .storage(opcode.storage().entrySet().stream()
                                        .collect(Collectors.toMap(
                                                entry -> entry.getKey().toHexString(),
                                                entry -> entry.getValue().toHexString()))))
                        .toList())
                .returnValue(
                        Optional.ofNullable(result.transactionProcessingResult().getOutput())
                                .map(Bytes::toHexString)
                                .orElse(Bytes.EMPTY.toHexString()));
    }

    private CallServiceParameters buildCallServiceParameters(
            Long consensusTimestamp, Optional<EthereumTransaction> ethTransaction) {
        final ContractResult contractResult = contractResultRepository
                .findById(consensusTimestamp)
                .orElseThrow(() -> new EntityNotFoundException("Contract result not found"));

        final BlockType blockType = recordFileService
                .findByTimestamp(consensusTimestamp)
                .map(recordFile -> BlockType.of(recordFile.getIndex().toString()))
                .orElse(BlockType.LATEST);

        return CallServiceParameters.builder()
                .sender(new HederaEvmAccount(getSenderAddress(contractResult)))
                .receiver(getReceiverAddress(ethTransaction, contractResult))
                .gas(getGasLimit(ethTransaction, contractResult))
                .value(getValue(ethTransaction, contractResult).longValue())
                .callData(getCallData(ethTransaction, contractResult))
                .isStatic(false)
                .callType(ETH_DEBUG_TRACE_TRANSACTION)
                .isEstimate(false)
                .block(blockType)
                .build();
    }

    private Address getSenderAddress(ContractResult contractResult) {
        return entityDatabaseAccessor.evmAddressFromId(contractResult.getSenderId(), Optional.empty());
    }

    private Address getReceiverAddress(
            Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        return ethereumTransaction
                .filter(transaction -> ArrayUtils.isNotEmpty(transaction.getToAddress()))
                .map(transaction -> Address.wrap(Bytes.wrap(transaction.getToAddress())))
                .flatMap(address -> {
                    if (isMirror(address.toArrayUnsafe())) {
                        return entityDatabaseAccessor
                                .get(address, Optional.empty())
                                .map(this::getEntityAddress);
                    }
                    return Optional.of(address);
                })
                .orElseGet(() -> {
                    final var contractId = EntityId.of(contractResult.getContractId());
                    return entityDatabaseAccessor.evmAddressFromId(contractId, Optional.empty());
                });
    }

    private Long getGasLimit(Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        return ethereumTransaction.map(EthereumTransaction::getGasLimit).orElse(contractResult.getGasLimit());
    }

    private BigInteger getValue(Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        return ethereumTransaction
                .map(transaction -> new BigInteger(transaction.getValue()))
                .or(() -> Optional.ofNullable(contractResult.getAmount()).map(BigInteger::valueOf))
                .orElse(BigInteger.ZERO);
    }

    private Bytes getCallData(Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        final byte[] callData = ethereumTransaction
                .map(EthereumTransaction::getCallData)
                .orElse(contractResult.getFunctionParameters());

        return Optional.ofNullable(callData).map(Bytes::of).orElse(Bytes.EMPTY);
    }

    private Address getEntityAddress(Entity entity) {
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return EntityId.isEmpty(entity.toEntityId()) ? Address.ZERO : toAddress(entity.toEntityId());
    }
}
