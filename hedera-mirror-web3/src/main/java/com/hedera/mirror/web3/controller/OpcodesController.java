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

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.Opcode;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.EthereumTransactionService;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.TransactionService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.bucket4j.Bucket;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
@ConditionalOnProperty(prefix = "hedera.mirror.opcode.tracer", name = "enabled", havingValue = "true")
class OpcodesController {

    private final RecordFileService recordFileService;
    private final TransactionService transactionService;
    private final EthereumTransactionService ethereumTransactionService;
    private final ContractCallService contractCallService;
    private final Bucket bucket;

    /**
     * Returns a result containing detailed information for the transaction execution with stack, memory and storage.\
     * For providing output formatted by opcodeLogger, the transaction is re-executed using the state from the
     * contract_state_changes sidecars produced by the consensus nodes.
     * In this way, we can have a track on all the storage/memory information and the entire trace of opcodes
     * that were executed during the replay.
     *
     * @param transactionIdOrHash The transaction ID or hash
     * @param stack Include stack information
     * @param memory Include memory information
     * @param storage Include storage information
     * @return {@link OpcodesResponse} containing the result of the transaction execution
     */
    @CrossOrigin(origins = "*")
    @PostMapping(value = "/{transactionIdOrHash}/opcodes")
    OpcodesResponse opcodes(@PathVariable TransactionIdOrHashParameter transactionIdOrHash,
                            @RequestParam(required = false, defaultValue = "true") boolean stack,
                            @RequestParam(required = false, defaultValue = "false") boolean memory,
                            @RequestParam(required = false, defaultValue = "false") boolean storage) {
        if (!bucket.tryConsume(1)) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        final var params = constructServiceParameters(transactionIdOrHash);
        final var result = contractCallService.processOpcodeCall(params);

        return new OpcodesResponse()
                // TODO: Not sure if this is the correct way to get the contractId here
                .contractId(result.transactionProcessingResult()
                        .getRecipient()
                        .map(EntityIdUtils::contractIdFromEvmAddress)
                        .map(ContractID::toString)
                        .orElse(null))
                .address(result.transactionProcessingResult()
                        .getRecipient()
                        .map(Address::toHexString)
                        .orElse(Address.ZERO.toHexString()))
                .gas(result.transactionProcessingResult().getGasPrice())
                .failed(!result.transactionProcessingResult().isSuccessful())
                .returnValue(Optional.ofNullable(result.transactionProcessingResult().getOutput())
                        .map(Bytes::toHexString)
                        .orElse(null))
                .opcodes(result.opcodes().stream()
                        .map(opcode -> new Opcode()
                                .pc(opcode.pc())
                                .op(opcode.op())
                                .gas(opcode.gas())
                                .gasCost(opcode.gasCost())
                                .depth(opcode.depth())
                                .stack(stack ?
                                        opcode.stack().stream()
                                                .map(Bytes::toHexString)
                                                .toList() :
                                        List.of())
                                .memory(memory ?
                                        opcode.memory().stream()
                                                .map(Bytes::toHexString)
                                                .toList() :
                                        List.of())
                                .storage(storage ?
                                        opcode.storage().entrySet().stream()
                                                .collect(Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        entry -> entry.getValue().toHexString())) :
                                        Map.of())
                                .reason(opcode.reason()))
                        .toList());
    }

    @SneakyThrows
    private CallServiceParameters constructServiceParameters(@NonNull TransactionIdOrHashParameter transactionIdOrHash) {
        final Transaction transaction;
        final Optional<EthereumTransaction> ethTransactionOpt;

        if (transactionIdOrHash.isHash()) {
            final EthereumTransaction ethTransaction = ethereumTransactionService
                    .findByHash(transactionIdOrHash.hash().toByteArray())
                    .orElseThrow(() -> new IllegalArgumentException("EthereumTransaction not found"));
            transaction = transactionService.findByConsensusTimestamp(ethTransaction.getConsensusTimestamp())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
            ethTransactionOpt = Optional.of(ethTransaction);
        } else if (transactionIdOrHash.isTransactionId()) {
            transaction = transactionService.findByTransactionId(transactionIdOrHash.transactionID())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
            ethTransactionOpt = ethereumTransactionService.findByConsensusTimestamp(transaction.getConsensusTimestamp());
        } else {
            throw new IllegalArgumentException("Invalid transaction ID or hash: %s".formatted(transactionIdOrHash));
        }

        final RecordFile recordFile = recordFileService.findRecordFileForTimestamp(transaction.getConsensusTimestamp())
                .orElseThrow(() -> new IllegalArgumentException("Record file with transaction not found"));

        final RecordItem recordItem = RecordItem.builder()
                .hapiVersion(recordFile.getHapiVersion())
                .transactionRecord(TransactionRecord.parseFrom(transaction.getTransactionRecordBytes()))
                .transaction(com.hederahashgraph.api.proto.java.Transaction.parseFrom(transaction.getTransactionBytes()))
                .ethereumTransaction(ethTransactionOpt.orElse(null))
                .build();

        return CallServiceParameters.builder()
                .sender(new HederaEvmAccount(getSenderAddress(recordItem)))
                .receiver(getReceiverAddress(recordItem))
                .gas(getGasLimit(recordItem))
                .value(getValue(recordItem).longValue())
                .callData(getCallData(recordItem))
                .isStatic(false)
                .callType(ETH_CALL)
                .isEstimate(false)
                .block(BlockType.of(recordFile.getIndex().toString()))
                .build();
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
}
