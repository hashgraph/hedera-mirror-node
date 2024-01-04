/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.service.ContractCallTestConstants.EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.PRNG_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.longValueOf;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.mirror.web3.viewmodel.BlockType;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallSystemPrecompileTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(SystemContractFunctions.class)
    void systemPrecompileFunctionsTestEthCall(final SystemContractFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(SystemContractFunctions.class)
    void systemPrecompileFunctionsTestEthEstimateGas(final SystemContractFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGas() {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRNG_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCall() {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, PRNG_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        final var result = contractCallService.processCall(serviceParameters);

        // Length of "0x" + 64 hex characters (2 per byte * 32 bytes)
        assertEquals(66, result.length(), "The string should represent a 32-byte long array");
    }

    @RequiredArgsConstructor
    enum SystemContractFunctions {
        TINYCENTS_TO_TINYBARS("tinycentsToTinybars", new Object[] {100L}, new Long[] {1550L}),
        TINYBARS_TO_TINYCENTS("tinybarsToTinycents", new Object[] {1550L}, new Object[] {100L});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }
}
