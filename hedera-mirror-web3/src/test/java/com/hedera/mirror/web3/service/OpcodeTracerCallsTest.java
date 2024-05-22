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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import java.util.Comparator;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OpcodeTracerCallsTest extends ContractCallTestSetup {

    private static final Long DEFAULT_CALL_VALUE = 0L;
    private static final OpcodeTracerOptions OPTIONS = new OpcodeTracerOptions(false, false, false);

    private static Comparator<Long> gasComparator() {
        return (d1, d2) -> {
            final var diff = Math.abs(d1 - d2);
            return Math.toIntExact(diff <= 64L ? 0 : d1 - d2);
        };
    }

    @ParameterizedTest
    @EnumSource(ContractCallServicePrecompileTest.SupportedContractModificationFunctions.class)
    void evmPrecompileSupportedModificationTokenFunctions(final ContractFunctionProviderEnum function) {
        final var params = serviceParametersForExecution(
                function,
                MODIFICATION_CONTRACT_ABI_PATH,
                MODIFICATION_CONTRACT_ADDRESS,
                ETH_DEBUG_TRACE_TRANSACTION,
                DEFAULT_CALL_VALUE
        );
        verifyOpcodeTracerCall(params, function);
    }

    @ParameterizedTest
    @EnumSource(ContractCallNestedCallsTest.NestedEthCallContractFunctionsNegativeCases.class)
    void failedNestedCallWithHardcodedResult(final ContractFunctionProviderEnum function) {
        final var params = serviceParametersForExecution(
                function,
                NESTED_CALLS_ABI_PATH,
                NESTED_ETH_CALLS_CONTRACT_ADDRESS,
                ETH_DEBUG_TRACE_TRANSACTION,
                DEFAULT_CALL_VALUE
        );
        verifyOpcodeTracerCall(params, function);
    }

    @ParameterizedTest
    @EnumSource(ContractCallDynamicCallsTest.DynamicCallsContractFunctions.class)
    void evmDynamicCallsTokenFunctions(final ContractFunctionProviderEnum function) {
        final var params = serviceParametersForExecution(
                function,
                DYNAMIC_ETH_CALLS_ABI_PATH,
                DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                ETH_DEBUG_TRACE_TRANSACTION,
                DEFAULT_CALL_VALUE
        );
        verifyOpcodeTracerCall(params, function);
    }

    private void verifyOpcodeTracerCall(final CallServiceParameters params,
                                        final ContractFunctionProviderEnum function) {
        if (function.getExpectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(params, function);
        } else {
            verifySuccessfulOpcodeTracerCall(params, function);
        }
    }

    @SneakyThrows
    private void verifyThrowingOpcodeTracerCall(final CallServiceParameters params,
                                                final ContractFunctionProviderEnum function) {
        final var actual = contractCallService.processOpcodeCall(params, OPTIONS, null);
        if (function.getExpectedErrorMessage() != null) {
            assertThat(actual.transactionProcessingResult().isSuccessful()).isFalse();
            assertThat(actual.transactionProcessingResult().getOutput()).isEqualTo(Bytes.EMPTY);
            assertThat(actual.transactionProcessingResult())
                    .satisfiesAnyOf(
                            result -> assertThat(result.getRevertReason())
                                    .isPresent()
                                    .map(BytesDecoder::maybeDecodeSolidityErrorStringToReadableMessage)
                                    .hasValue(function.getExpectedErrorMessage()),
                            result -> assertThat(result.getHaltReason())
                                    .isPresent()
                                    .map(ExceptionalHaltReason::getDescription)
                                    .hasValue(function.getExpectedErrorMessage())
                    );
            assertThat(actual.opcodes().size()).isNotZero();
            assertThat(toHumanReadableMessage(actual.opcodes().getLast().reason()))
                    .isEqualTo(function.getExpectedErrorMessage());
        }
    }

    private void verifySuccessfulOpcodeTracerCall(final CallServiceParameters params,
                                                  final ContractFunctionProviderEnum function) {
        final var expected = expectedOpcodeProcessingResult(params);
        final var actual = contractCallService.processOpcodeCall(params, OPTIONS, null);

        if (function.getExpectedResultFields() != null) {
            assertThat(actual.transactionProcessingResult().getOutput().toHexString())
                    .isEqualTo(functionEncodeDecoder.encodedResultFor(
                            function.getName(), NESTED_CALLS_ABI_PATH, function.getExpectedResultFields()));
        }

        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .usingRecursiveComparison()
                .ignoringFields("logs")
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }

    private OpcodesProcessingResult expectedOpcodeProcessingResult(final CallServiceParameters params) {
        return ContractCallContext.run(ctx -> {
            ctx.setOpcodeTracerOptions(OPTIONS);
            ctx.initializeStackFrames(store.getStackedStateFrames());
            final var result = processor.execute(params, params.getGas(), TracerType.OPCODE, ctx);
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(result)
                    .opcodes(ctx.getOpcodes())
                    .build();
        });
    }

    private static String toHumanReadableMessage(final String solidityError) {
        return BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(Bytes.fromHexString(solidityError));
    }
}
