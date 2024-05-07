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

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.web3.evm.utils.TransactionUtils;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.repository.EthereumTransactionRepository;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.OpcodesResponse;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.bucket4j.Bucket;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.web3.evm.utils.TransactionUtils.isValidEthHash;
import static com.hedera.mirror.web3.evm.utils.TransactionUtils.isValidTransactionId;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
@ConditionalOnProperty(prefix = "hedera.mirror.opcode.tracer", name = "enabled", havingValue = "true")
class OpcodesController {

    private final ContractCallService contractCallService;
    private final Bucket bucket;
    private final EthereumTransactionRepository transactionRepository;

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/{transactionIdOrHash}/opcodes")
    OpcodesResponse opcodes(@PathVariable String transactionIdOrHash,
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
                // TODO: Not sure if this is the correct way to get the address here
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

    private CallServiceParameters constructServiceParameters(@NonNull String transactionIdOrHash) {
        EthereumTransaction ethTransaction;
        if (isValidEthHash(transactionIdOrHash)) {
            // TODO: Need to get the transaction by hash here (not sure if this is the correct way to do it)
            final var transactionHash = ByteString.fromHex(transactionIdOrHash).toByteArray();
            ethTransaction = transactionRepository
                    .findByHash(transactionHash)
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        } else if (isValidTransactionId(transactionIdOrHash)) {
            // TODO: Need to get the transaction by ID here (not sure if this is the correct way to do it)
            final var transactionId = TransactionUtils.parseTransactionId(transactionIdOrHash);
            final var transactionValidStart = Objects.requireNonNull(transactionId.transactionValidStart());
            ethTransaction = transactionRepository
                    .findById(convertToNanosMax(transactionValidStart.seconds(), transactionValidStart.nanos()))
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        } else {
            throw new IllegalArgumentException("Invalid transaction ID or hash");
        }

        return CallServiceParameters.builder()
                .isStatic(false)
                .callType(ETH_CALL)
                .isEstimate(false)
                // TODO: Need to get block number somehow from the fetched transaction above
                .block(BlockType.LATEST)
                // TODO: Need to get sender address somehow from the fetched transaction above
                .sender(new HederaEvmAccount(Address.ZERO))
                .receiver(Address.fromHexString(Bytes.of(ethTransaction.getToAddress()).toHexString()))
                .gas(ethTransaction.getGasLimit())
                .value(new BigInteger(ethTransaction.getValue()).longValue())
                .callData(Bytes.of(ethTransaction.getCallData()))
                .build();
    }
}
