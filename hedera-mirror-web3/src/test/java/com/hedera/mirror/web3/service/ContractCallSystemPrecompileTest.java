/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ContractCallSystemPrecompileTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(SystemContractFunctions.class)
    void systemPrecompileFunctionsTestEthCall(SystemContractFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_CALL, 0L);
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
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
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
