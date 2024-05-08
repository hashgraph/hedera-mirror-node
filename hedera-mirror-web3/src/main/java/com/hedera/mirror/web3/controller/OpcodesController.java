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

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.TransactionService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.OpcodesResponse;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.bucket4j.Bucket;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
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

    private final TransactionService transactionService;
    private final ContractCallService contractCallService;
    private final Bucket bucket;
    private final RecordFileService recordFileService;

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

        return OpcodesResponse.builder()
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
                .returnValue(result.transactionProcessingResult().getOutput().toHexString())
                .opcodes(result.opcodes().stream()
                        .map(opcode -> OpcodesResponse.Opcode.builder()
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
                                .reason(opcode.reason())
                                .build())
                        .toList())
                .build();
    }

    private CallServiceParameters constructServiceParameters(@NonNull TransactionIdOrHashParameter transactionIdOrHash) {
        final Bytes receiverAddress;
        final long consensusTimestamp;

        if (transactionIdOrHash.isHash()) {
            final EthereumTransaction ethTransaction = transactionService
                    .findByEthHash(transactionIdOrHash.hash().toByteArray())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
            receiverAddress = Bytes.of(ethTransaction.getToAddress());
            consensusTimestamp = ethTransaction.getConsensusTimestamp();
        } else if (transactionIdOrHash.isTransactionId()) {
            final Transaction transaction = transactionService
                    .findByTransactionId(transactionIdOrHash.transactionID())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
            receiverAddress = Bytes.of(toEvmAddress(transaction.getEntityId()));
            consensusTimestamp = transaction.getConsensusTimestamp();
        } else {
            throw new IllegalArgumentException("Invalid transaction ID or hash");
        }

        final RecordFile recordFile = recordFileService.findRecordFileForTimestamp(consensusTimestamp)
                .orElseThrow(() -> new IllegalArgumentException("Record file with transaction not found"));

        final RecordItem recordItem = recordFile.getRecordItem(consensusTimestamp)
                .orElseThrow(() -> new IllegalArgumentException("Record item for transaction not found"));

        return CallServiceParameters.builder()
                .sender(new HederaEvmAccount(getSenderAddress(recordItem)))
                .receiver(Address.fromHexString(receiverAddress.toHexString()))
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
                .orElse(0L);
    }

    private BigInteger getValue(RecordItem recordItem) {
        return Optional.ofNullable(recordItem.getEthereumTransaction())
                .map(EthereumTransaction::getValue)
                .map(BigInteger::new)
                .orElse(BigInteger.ZERO);
    }

    private Bytes getCallData(RecordItem recordItem) {
        return Optional.ofNullable(recordItem.getEthereumTransaction())
                .map(EthereumTransaction::getCallData)
                .map(Bytes::of)
                .orElse(Bytes.EMPTY);
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
}
