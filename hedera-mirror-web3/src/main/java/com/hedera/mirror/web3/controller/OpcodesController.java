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

import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.ContractCallDebugService;
import com.hedera.mirror.web3.service.OpcodeService;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
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
class OpcodesController {

    private final OpcodeService opcodeService;
    private final ContractCallDebugService contractCallDebugService;
    private final Bucket rateLimitBucket;
    private final Bucket gasLimitBucket;

    /**
     * <p>
     *     Returns a result containing detailed information for the transaction execution,
     *     including all values from the {@code stack}, {@code memory} and {@code storage}
     *     and the entire trace of opcodes that were executed during the replay.
     * </p>
     * <p>
     *     Note that to provide the output, the transaction needs to be re-executed on the EVM,
     *     which may take a significant amount of time to complete if stack and memory information is requested.
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
    OpcodesResponse getContractOpcodes(
            @PathVariable @Valid TransactionIdOrHashParameter transactionIdOrHash,
            @RequestParam(required = false, defaultValue = "true") boolean stack,
            @RequestParam(required = false, defaultValue = "false") boolean memory,
            @RequestParam(required = false, defaultValue = "false") boolean storage
    ) {
        if (!rateLimitBucket.tryConsume(1)) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);
        if (!gasLimitBucket.tryConsume(params.getGas())) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        final var options = new OpcodeTracerOptions(stack, memory, storage);
        final var result = contractCallDebugService.processOpcodeCall(params, options, transactionIdOrHash);

        return opcodeService.buildOpcodesResponse(result);
    }
}
