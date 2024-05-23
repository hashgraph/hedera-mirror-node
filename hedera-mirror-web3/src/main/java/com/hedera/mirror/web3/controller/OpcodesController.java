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

import com.hedera.mirror.rest.model.Opcode;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.CallServiceParametersBuilder;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.services.utils.EntityIdUtils;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
@ConditionalOnProperty(prefix = "hedera.mirror.opcode.tracer", name = "enabled", havingValue = "true")
@SuppressWarnings("java:S5122") // "Make sure that enabling CORS is safe here"
class OpcodesController {

    private final CallServiceParametersBuilder callServiceParametersBuilder;
    private final ContractCallService contractCallService;
    private final Bucket rateLimitBucket;
    private final Bucket gasLimitBucket;

    /**
     * <p>
     *     Returns a result containing detailed information for the transaction execution,
     *     including all values from the {@code stack}, {@code memory} and {@code storage}
     *     and the entire trace of opcodes that were executed during the replay.
     * </p>
     * <p>
     *     To provide the output, the transaction is re-executed using the state from the
     *     {@code contract_state_changes} sidecars produced by the consensus nodes.
     * </p>
     * <p>
     *     The endpoint depends on the following properties to be set to true when starting up the mirror-node:
     *     <ul>
     *         <li>{@systemProperty hedera.mirror.importer.parser.record.entity.persist.transactionBytes}</li>
     *         <li>{@systemProperty hedera.mirror.importer.parser.record.entity.persist.transactionRecordBytes}</li>
     *     </ul>
     * </p>
     *
     * @param transactionIdOrHash The transaction ID or hash
     * @param stack Include stack information
     * @param memory Include memory information
     * @param storage Include storage information
     * @return {@link OpcodesResponse} containing the result of the transaction execution
     */
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/{transactionIdOrHash}/opcodes")
    OpcodesResponse getContractOpcodesByTransactionIdOrHash(
            @PathVariable @Valid TransactionIdOrHashParameter transactionIdOrHash,
            @RequestParam(required = false, defaultValue = "true") boolean stack,
            @RequestParam(required = false, defaultValue = "false") boolean memory,
            @RequestParam(required = false, defaultValue = "false") boolean storage
    ) {
        if (!rateLimitBucket.tryConsume(1)) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        final var params = callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash);
        if (!gasLimitBucket.tryConsume(params.getGas())) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        final var options = new OpcodeTracerOptions(stack, memory, storage);
        final var result = contractCallService.processOpcodeCall(params, options);

        return new OpcodesResponse()
                .address(result.transactionProcessingResult()
                        .getRecipient()
                        .map(Address::toHexString)
                        .orElse(Address.ZERO.toHexString()))
                .contractId(result.transactionProcessingResult()
                        .getRecipient()
                        .map(EntityIdUtils::contractIdFromEvmAddress)
                        .map(contractId -> "%d.%d.%d".formatted(
                                contractId.getShardNum(),
                                contractId.getRealmNum(),
                                contractId.getContractNum()))
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
                                .stack(opcode.stack().isPresent() ?
                                        Arrays.stream(opcode.stack().get())
                                                .map(Bytes::toHexString)
                                                .toList() :
                                        null)
                                .memory(opcode.memory().isPresent() ?
                                        Arrays.stream(opcode.memory().get())
                                                .map(Bytes::toHexString)
                                                .toList() :
                                        null)
                                .storage(opcode.storage().isPresent() ?
                                        opcode.storage().get().entrySet().stream()
                                                .collect(Collectors.toMap(
                                                        entry -> entry.getKey().toHexString(),
                                                        entry -> entry.getValue().toHexString())) :
                                        null))
                        .toList())
                .returnValue(Optional.ofNullable(result.transactionProcessingResult().getOutput())
                        .map(Bytes::toHexString)
                        .orElse(Bytes.EMPTY.toHexString()));
    }
}
