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

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.OPTIONS;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.gasComparator;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.toHumanReadableMessage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.web3j.tx.Contract;

abstract class AbstractContractCallServiceOpcodeTracerTest extends AbstractContractCallServiceHistoricalTest {

    @Resource
    protected ContractDebugService contractDebugService;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> paramsCaptor;

    @Captor
    private ArgumentCaptor<Long> gasCaptor;

    private HederaEvmTransactionProcessingResult resultCaptor;
    private ContractCallContext contextCaptor;

    @Resource
    private EntityDatabaseAccessor entityDatabaseAccessor;

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

    protected void verifyOpcodesResponse(final OpcodesResponse opcodesResponse, final OpcodeTracerOptions options) {
        assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(resultCaptor, contextCaptor.getOpcodes()));
        assertThat(gasCaptor.getValue()).isEqualTo(TRANSACTION_GAS_LIMIT);
        assertThat(contextCaptor.getOpcodeTracerOptions()).isEqualTo(options);
    }

    protected void verifyOpcodesResponseWithExpectedReturnValue(
            final OpcodesResponse opcodesResponse,
            final OpcodeTracerOptions options,
            final String expectedReturnValue) {
        assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(resultCaptor, contextCaptor.getOpcodes()));
        assertThat(gasCaptor.getValue()).isEqualTo(TRANSACTION_GAS_LIMIT);
        assertThat(contextCaptor.getOpcodeTracerOptions()).isEqualTo(options);

        assertThat(opcodesResponse.getFailed()).isFalse();
        assertThat(opcodesResponse.getReturnValue()).isEqualTo(expectedReturnValue);
    }

    private OpcodesResponse expectedOpcodesResponse(
            final HederaEvmTransactionProcessingResult result, final List<Opcode> opcodes) {
        return new OpcodesResponse()
                .address(result.getRecipient()
                        .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                        .map(this::entityAddress)
                        .map(Address::toHexString)
                        .orElse(Address.ZERO.toHexString()))
                .contractId(result.getRecipient()
                        .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                        .map(Entity::toEntityId)
                        .map(EntityId::toString)
                        .orElse(null))
                .failed(!result.isSuccessful())
                .gas(result.getGasUsed())
                .opcodes(opcodes.stream()
                        .map(opcode -> new com.hedera.mirror.rest.model.Opcode()
                                .depth(opcode.depth())
                                .gas(opcode.gas())
                                .gasCost(opcode.gasCost())
                                .op(opcode.op())
                                .pc(opcode.pc())
                                .reason(opcode.reason())
                                .stack(opcode.stack().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .memory(opcode.memory().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .storage(opcode.storage().entrySet().stream()
                                        .collect(Collectors.toMap(
                                                entry -> entry.getKey().toHexString(),
                                                entry -> entry.getValue().toHexString()))))
                        .toList())
                .returnValue(Optional.ofNullable(result.getOutput())
                        .map(Bytes::toHexString)
                        .orElse(Bytes.EMPTY.toHexString()));
    }

    private Address entityAddress(Entity entity) {
        if (entity == null) {
            return Address.ZERO;
        }
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return toAddress(entity.toEntityId());
    }
}
