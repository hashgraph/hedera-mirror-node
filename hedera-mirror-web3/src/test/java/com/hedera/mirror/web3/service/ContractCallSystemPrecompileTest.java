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

import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ContractCallSystemPrecompileTest extends ContractCallTestSetup {

    private static final List<BlockType> BLOCK_NUMBERS_FOR_EVM_VERSION = List.of(
            BlockType.of(String.valueOf(EVM_V_46_BLOCK)),
            BlockType.of(String.valueOf(EVM_V_38_BLOCK)),
            BlockType.of(String.valueOf(EVM_V_34_BLOCK)),
            BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));

    private static Stream<Arguments> exchangeRateFunctionsProviderHistorical() {
        return Arrays.stream(ExchangeRateFunctions.values())
                .flatMap(exchangeRateFunction -> BLOCK_NUMBERS_FOR_EVM_VERSION.stream()
                        .map(blockNumber -> Arguments.of(exchangeRateFunction, blockNumber)));
    }

    private static Stream<Arguments> blockNumberForDifferentEvmVersionsProviderHistorical() {
        return BLOCK_NUMBERS_FOR_EVM_VERSION.stream().map(Arguments::of);
    }

    @ParameterizedTest
    @EnumSource(ExchangeRateFunctions.class)
    void exchangeRatePrecompileFunctionsTestEthCall(final ExchangeRateFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ExchangeRateFunctions.class)
    void exchangeRatePrecompileFunctionsTestEthCallWithValueRevertExecution(final ExchangeRateFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_CALL, 100L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @ParameterizedTest
    @MethodSource("exchangeRateFunctionsProviderHistorical")
    void exchangeRateFunctionsTestEthCallHistorical(final ExchangeRateFunctions contractFunc, BlockType blockNumber) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_CALL, 0L, blockNumber);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ExchangeRateFunctions.class)
    void exchangeRatePrecompileFunctionsTestEthEstimateGas(final ExchangeRateFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ExchangeRateFunctions.class)
    void exchangeRatePrecompileFunctionsTestEthEstimateGasWithValueRevertExecution(
            final ExchangeRateFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, EXCHANGE_RATE_PRECOMPILE_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 100L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGas() {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRNG_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGasWithValueRevertExecution() {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRNG_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 100L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCall() {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, PRNG_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        final var result = contractExecutionService.processCall(serviceParameters);

        // Length of "0x" + 64 hex characters (2 per byte * 32 bytes)
        assertEquals(66, result.length(), "The string should represent a 32-byte long array");
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCallWithValueRevertExecution() {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, PRNG_CONTRACT_ADDRESS, ETH_CALL, 100L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @ParameterizedTest
    @MethodSource("blockNumberForDifferentEvmVersionsProviderHistorical")
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCallHistorical(BlockType blockNumber) {
        final var functionName = "getPseudorandomSeed";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, PRNG_PRECOMPILE_ABI_PATH);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, PRNG_CONTRACT_ADDRESS, ETH_CALL, 0L, blockNumber);

        final var result = contractExecutionService.processCall(serviceParameters);

        // Length of "0x" + 64 hex characters (2 per byte * 32 bytes)
        assertEquals(66, result.length(), "The string should represent a 32-byte long array");
    }

    @Getter
    @RequiredArgsConstructor
    enum ExchangeRateFunctions implements ContractFunctionProviderEnum {
        TINYCENTS_TO_TINYBARS("tinycentsToTinybars", new Object[] {100L}, new Long[] {1550L}),
        TINYBARS_TO_TINYCENTS("tinybarsToTinycents", new Object[] {1550L}, new Object[] {100L});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }
}
