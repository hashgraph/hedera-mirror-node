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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.Comparator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OpcodeTracerCallsTest extends ContractCallTestSetup {

    private static final OpcodeTracerOptions OPTIONS = new OpcodeTracerOptions(false, false, false);

    private static Comparator<Long> gasComparator() {
        return (d1, d2) -> {
            final var diff = Math.abs(d1 - d2);
            return Math.toIntExact(diff <= 64L ? 0 : d1 - d2);
        };
    }

    @ParameterizedTest
    @EnumSource(ContractCallNestedCallsTest.NestedEthCallContractFunctions.class)
    void nestedPrecompileTokenFunctions(final ContractCallNestedCallsTest.NestedEthCallContractFunctions function) {
        final var callData = functionEncodeDecoder.functionHashFor(
                function.getName(), NESTED_CALLS_ABI_PATH, function.getFunctionParameters());
        final var value = function.isCreateTokenFunction() ?
                10_000L * 100_000_000L : 0L;
        final var params = serviceParametersForExecution(
                callData, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, value, BlockType.LATEST);

        final var expected = expectedOpcodeProcessingResult(params, OPTIONS);
        final var actual = contractCallService.processOpcodeCall(params, OPTIONS, null);

        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .usingRecursiveComparison()
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }

    @ParameterizedTest
    @EnumSource(ContractCallNestedCallsTest.NestedEthCallContractFunctionsNegativeCases.class)
    void failedNestedCallWithHardcodedResult(final ContractCallNestedCallsTest.NestedEthCallContractFunctionsNegativeCases function) {
        final var callData = functionEncodeDecoder.functionHashFor(
                function.getName(), NESTED_CALLS_ABI_PATH, function.getFunctionParameters());
        final var params = serviceParametersForExecution(
                callData, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L, function.getBlock());

        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                function.getName(), NESTED_CALLS_ABI_PATH, function.getExpectedResultFields());

        final var expected = expectedOpcodeProcessingResult(params, OPTIONS);
        final var actual = contractCallService.processOpcodeCall(params, OPTIONS, null);

        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .matches(result -> result.getOutput().toHexString().equals(successfulResponse))
                .usingRecursiveComparison()
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }
}
