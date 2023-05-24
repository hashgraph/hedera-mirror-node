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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacadeImpl;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

class ContractCallServiceTest extends ContractCallTestSetup {

    private static final String GAS_METRICS = "hedera.mirror.web3.call.gas";
    private static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MirrorEvmTxProcessorFacadeImpl processor;

    @Test
    void pureCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParameters(pureFuncHash, 0, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForPureCall() {
        final var pureFuncHash = "8070450f";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var serviceParameters =
                serviceParameters(pureFuncHash, 0, ETH_ESTIMATE_GAS, false, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasWithoutReceiver() {
        final var serviceParameters = serviceParameters("", 0, ETH_ESTIMATE_GAS, true, 0, Address.ZERO);

        persistEntities(false);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @Test
    void viewCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // returnStorageData()
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var successfulReadResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(viewFuncHash, 0, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters =
                serviceParameters(viewFuncHash, 0, ETH_ESTIMATE_GAS, false, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @Test
    void transferFunds() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters = serviceParameters("0x", 7L, ETH_CALL, true, 0, RECEIVER_ADDRESS);
        persistEntities(true);

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // getAccountBalance(address)
        final var balanceCall = "0x93423e9c00000000000000000000000000000000000000000000000000000000000002e6";
        final var expectedBalance = "0x0000000000000000000000000000000000000000000000000000000000004e20";
        final var params = serviceParameters(balanceCall, 0, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForBalanceCall() {
        final var balanceCall = "0x93423e9c00000000000000000000000000000000000000000000000000000000000003e6";
        final var serviceParameters =
                serviceParameters(balanceCall, 0, ETH_ESTIMATE_GAS, true, 15_000_000L, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @Test
    void testRevertDetailMessage() {
        final var revertFunctionSignature = "0xa26388bb";
        final var serviceParameters =
                serviceParameters(revertFunctionSignature, 0, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Custom revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000");
    }

    @ParameterizedTest
    @EnumSource(RevertFunctions.class)
    void testReverts(final RevertFunctions revertFunctions) {
        final var serviceParameters =
                serviceParameters(revertFunctions.functionSignature, 0, ETH_CALL, true, 0, REVERTER_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", revertFunctions.errorDetail)
                .hasFieldOrPropertyWithValue("data", revertFunctions.errorData);
    }

    @Test
    void invalidFunctionSig() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        final var wrongFunctionSignature = "0x542ec32e";
        final var serviceParameters =
                serviceParameters(wrongFunctionSignature, 0, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED")
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var serviceParameters = serviceParameters("0x", -5L, ETH_CALL, true, 0, RECEIVER_ADDRESS);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = serviceParameters("0x", 210000L, ETH_CALL, true, 0, RECEIVER_ADDRESS);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    /**
     * _to.transfer(msg.value) fails due to the static frame,{@link CallOperation} this will be
     * supported with future release with gas_estimate support.
     */
    @Test
    void transferThruContract() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        // transferHbarsToAddress(address)
        final var stateChangePayable = "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e6";
        final var params = serviceParameters(stateChangePayable, 90L, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(params))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage(LOCAL_CALL_MODIFICATION_EXCEPTION.toString())
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void estimateGasForStateChangeCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters =
                serviceParameters(stateChangeHash, 0, ETH_ESTIMATE_GAS, false, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void nestedContractStateChangesWork() {
        final var stateChangeHash =
                "0x51fecdca000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000004ed00000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var serviceParameters =
                serviceParameters(stateChangeHash, 0, ETH_CALL, false, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000");
    }

    @Test
    void contractCreationWork() {
        final var deployHash =
                "0xc32723ed000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(deployHash, 0, ETH_CALL, false, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000086976616e6976616e000000000000000000000000000000000000000000000000");
    }

    @Test
    void stateChangeFails() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        // writeToStorageSlot(string)
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters =
                serviceParameters(stateChangeHash, 0, ETH_CALL, true, 0, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void ercPrecompileCallRevertsForEstimateGas() {
        final var tokenNameCall = "0x6f0fccab0000000000000000000000000000000000000000000000000000000000000416";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var serviceParameters =
                serviceParameters(tokenNameCall, 0, ETH_ESTIMATE_GAS, false, 0L, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void precompileCallRevertsForEstimateGas() {
        final var freezeTokenCall = "0x7c93c87e00000000000000000000000000000000000000000000000000000000000003e4";
        final var serviceParameters =
                serviceParameters(freezeTokenCall, 0, ETH_ESTIMATE_GAS, false, 0L, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    private CallServiceParameters serviceParameters(
            String callData, long value, CallType callType, boolean isStatic, long estimatedGas, Address receiver) {
        final var isGasEstimate = callType == ETH_ESTIMATE_GAS;
        final var gas = (isGasEstimate && estimatedGas > 0) ? estimatedGas : 120000L;
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        final var data = callData.isEmpty()
                ? Bytes.wrap(functionEncodeDecoder.getContractBytes(ETH_CALL_CONTRACT_BYTES_PATH))
                : Bytes.fromHexString(callData);

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(receiver)
                .callData(data)
                .gas(gas)
                .isEstimate(isGasEstimate)
                .isStatic(isStatic)
                .callType(callType)
                .build();
    }

    private double getGasUsedBeforeExecution(final CallType callType) {
        final var callCounter = meterRegistry.find(GAS_METRICS).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst();

        var gasUsedBeforeExecution = 0d;
        if (callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private long gasUsedAfterExecution(CallServiceParameters serviceParameters) {
        return processor
                .execute(
                        serviceParameters.getSender(),
                        serviceParameters.getReceiver(),
                        serviceParameters.getGas(),
                        serviceParameters.getValue(),
                        serviceParameters.getCallData(),
                        Instant.now(),
                        serviceParameters.isStatic())
                .getGasUsed();
    }

    private void assertGasUsedIsPositive(final double gasUsedBeforeExecution, final CallType callType) {
        final var afterExecution = meterRegistry.find(GAS_METRICS).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        final var gasConsumed = afterExecution.count() - gasUsedBeforeExecution;
        assertThat(gasConsumed).isPositive();
    }

    @RequiredArgsConstructor
    private enum RevertFunctions {
        REVERT_WITH_CUSTOM_ERROR_PURE("revertWithCustomErrorPure", "35314694", "", "0x0bd3d39c"),
        REVERT_WITH_PANIC_PURE(
                "revertWithPanicPure",
                "83889056",
                "",
                "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
        REVERT_PAYABLE(
                "revertPayable",
                "d0efd7ef",
                "RevertReasonPayable",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013526576657274526561736f6e50617961626c6500000000000000000000000000"),
        REVERT_PURE(
                "revertPure",
                "b2e0100c",
                "RevertReasonPure",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010526576657274526561736f6e5075726500000000000000000000000000000000"),
        REVERT_VIEW(
                "revertView",
                "90e9b875",
                "RevertReasonView",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010526576657274526561736f6e5669657700000000000000000000000000000000"),
        REVERT_WITH_CUSTOM_ERROR("revertWithCustomError", "46fc4bb1", "", "0x0bd3d39c"),
        REVERT_WITH_NOTHING("revertWithNothing", "fe0a3dd7", "", "0x"),
        REVERT_WITH_NOTHING_PURE("revertWithNothingPure", "2dac842f", "", "0x"),
        REVERT_WITH_PANIC(
                "revertWithPanic",
                "33fe3fbd",
                "",
                "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
        REVERT_WITH_STRING(
                "revertWithString",
                "0323d234",
                "Some revert message",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000"),
        REVERT_WITH_STRING_PURE(
                "revertWithStringPure",
                "8b153371",
                "Some revert message",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000"),
        REVERT_WITH_CUSTOM_ERROR_WITH_PARAMETERS(
                "revertWithCustomErrorWithParameters",
                "86451c2b",
                "",
                "0xcc4263a0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000"),
        REVERT_WITH_CUSTOM_ERROR_WITH_PARAMETERS_PURE(
                "revertWithCustomErrorWithParameters",
                "b1c5ae51",
                "",
                "0xcc4263a0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000");

        private final String name;
        private final String functionSignature;
        private final String errorDetail;
        private final String errorData;
    }
}
