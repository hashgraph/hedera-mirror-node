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
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.*;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import io.github.bucket4j.Bucket;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
//@ConditionalOnProperty(prefix = "hedera.mirror.opcode.tracer", name = "enabled", havingValue = "true")
class OpcodesController {

    private final CallServiceParametersBuilder callServiceParametersBuilder;
    private final ContractCallService contractCallService;
    private final Bucket bucket;

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
            @PathVariable TransactionIdOrHashParameter transactionIdOrHash,
            @RequestParam(required = false, defaultValue = "true") boolean stack,
            @RequestParam(required = false, defaultValue = "false") boolean memory,
            @RequestParam(required = false, defaultValue = "false") boolean storage
    ) {
        if (!bucket.tryConsume(1)) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        final var params = callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash);
        final var result = contractCallService.processOpcodeCall(params, transactionIdOrHash);

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
                        .orElse(Bytes.EMPTY.toHexString()))
                .opcodes(result.opcodes().stream()
                        .map(opcode -> new Opcode()
                                .pc(opcode.pc())
                                .op(String.valueOf(opcode.op()))
                                .gas(opcode.gas())
                                .gasCost(opcode.gasCost())
                                .depth(opcode.depth())
                                .stack(stack ?
                                        opcode.stack().stream()
                                                .map(byteValue -> String.format("%02X", byteValue))
                                                .toList() :
                                        List.of())
                                .memory(memory ?
                                        opcode.memory().stream()
                                                .map(byteValue -> String.format("%02X", byteValue))
                                                .toList() :
                                        List.of())
                                .storage(storage ?
                                        opcode.storage().get().entrySet().stream()
                                                .collect(Collectors.toMap(
                                                        entry -> entry.getKey().toHexString(),
                                                        entry -> entry.getValue().toHexString())) :
                                        Map.of())
                                .reason(opcode.reason())).toList()
                );
    }
}
