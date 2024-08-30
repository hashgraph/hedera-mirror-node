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

import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.OPTIONS;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.gasComparator;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.toHumanReadableMessage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.web3j.tx.Contract;

@RequiredArgsConstructor
abstract class AbstractContractCallServiceOpcodeTracerTest extends AbstractContractCallServiceTest {

    private final ContractDebugService contractDebugService;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> paramsCaptor;

    @Captor
    private ArgumentCaptor<Long> gasCaptor;

    private HederaEvmTransactionProcessingResult resultCaptor;
    private ContractCallContext contextCaptor;

    @BeforeEach
    void setUpArgumentCaptors() {
        doAnswer(invocation -> {
                    final var transactionProcessingResult =
                            (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                    resultCaptor = transactionProcessingResult;
                    contextCaptor = ContractCallContext.get();
                    return transactionProcessingResult;
                })
                .when(processor)
                .execute(paramsCaptor.capture(), gasCaptor.capture());
    }

    protected void verifyOpcodeTracerCall(
            final String callData, final ContractFunctionProviderRecord functionProvider) {
        final var callDataBytes = Bytes.fromHexString(callData);
        final var debugParameters = getDebugParameters(functionProvider, callDataBytes);

        if (functionProvider.expectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(debugParameters, functionProvider);
        } else {
            verifySuccessfulOpcodeTracerCall(debugParameters);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(debugParameters);
        assertThat(gasCaptor.getValue()).isEqualTo(debugParameters.getGas());
    }

    protected void verifyOpcodeTracerCall(final String callData, final Contract contract) {
        ContractFunctionProviderRecord functionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .build();

        final var callDataBytes = Bytes.fromHexString(callData);
        final var debugParameters = getDebugParameters(functionProvider, callDataBytes);

        if (functionProvider.expectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(debugParameters, functionProvider);
        } else {
            verifySuccessfulOpcodeTracerCall(debugParameters);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(debugParameters);
        assertThat(gasCaptor.getValue()).isEqualTo(debugParameters.getGas());
    }

    @SneakyThrows
    protected void verifyThrowingOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderRecord function) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        assertThat(actual.transactionProcessingResult().isSuccessful()).isFalse();
        assertThat(actual.transactionProcessingResult().getOutput()).isEqualTo(Bytes.EMPTY);
        assertThat(actual.transactionProcessingResult())
                .satisfiesAnyOf(
                        result -> assertThat(result.getRevertReason())
                                .isPresent()
                                .map(BytesDecoder::maybeDecodeSolidityErrorStringToReadableMessage)
                                .hasValue(function.expectedErrorMessage()),
                        result -> assertThat(result.getHaltReason())
                                .isPresent()
                                .map(ExceptionalHaltReason::getDescription)
                                .hasValue(function.expectedErrorMessage()));
        assertThat(actual.opcodes().size()).isNotZero();
        assertThat(toHumanReadableMessage(actual.opcodes().getLast().reason()))
                .isEqualTo(function.expectedErrorMessage());
    }

    protected void verifySuccessfulOpcodeTracerCall(final ContractDebugParameters params) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        final var expected = new OpcodesProcessingResult(resultCaptor, contextCaptor.getOpcodes());
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
}
