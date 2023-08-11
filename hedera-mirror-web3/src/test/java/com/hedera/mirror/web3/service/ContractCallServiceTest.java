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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallServiceTest extends ContractCallTestSetup {

    private static final String GAS_METRICS = "hedera.mirror.web3.call.gas";

    @BeforeEach
    void setup() {
        // reset gas metrics
        meterRegistry.clear();
    }

    @Test
    void pureCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForPureCall() {
        final var pureFuncHash = "8070450f";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasWithoutReceiver() {
        final var serviceParameters =
                serviceParametersForExecution(Bytes.fromHexString("0x"), Address.ZERO, ETH_ESTIMATE_GAS, 0L);

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
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(viewFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(viewFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @Test
    void transferFunds() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters =
                serviceParametersForExecution(Bytes.fromHexString("0x"), RECEIVER_ADDRESS, ETH_CALL, 7L);
        receiverPersist();

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // getAccountBalance(address)
        // Use alias address when applicable as EVM checks alias with highest priority
        final var balanceCall = "0x93423e9c000000000000000000000000" + SENDER_ALIAS.toUnprefixedHexString();
        final var expectedBalance = "0x000000000000000000000000000000000000000000000000000000746a528800";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L);

        final var isSuccessful = contractCallService.processCall(serviceParameters);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForBalanceCall() {
        final var balanceCall = "0x93423e9c00000000000000000000000000000000000000000000000000000000000003e6";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @Test
    void testRevertDetailMessage() {
        final var revertFunctionSignature = "0xa26388bb";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(revertFunctionSignature), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L);

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
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(revertFunctions.functionSignature), REVERTER_CONTRACT_ADDRESS, ETH_CALL, 0L);

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
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(wrongFunctionSignature), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED")
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var serviceParameters =
                serviceParametersForExecution(Bytes.fromHexString("0x"), RECEIVER_ADDRESS, ETH_CALL, -5L);
        receiverPersist();

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters =
                serviceParametersForExecution(Bytes.fromHexString("0x"), RECEIVER_ADDRESS, ETH_CALL, 510000000000L);
        receiverPersist();

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferThruContract() {
        // transferHbarsToAddress(address)
        final var stateChangePayable = "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e6";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangePayable), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 90L);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @Test
    void hollowAccountCreationWorks() {
        // transferHbarsToAddress(address)
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var transferHbarsInput = "0x80b9f03c00000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(transferHbarsInput), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 90L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForStateChangeCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForCreate2ContractDeploy() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);

        // deployViaCreate2()
        final var deployViaCreate2Hash = "0xdbb6f04a";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(deployViaCreate2Hash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0);

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
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000");
    }

    @Test
    void contractCreationWork() {
        final var deployHash =
                "0xc32723ed000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var serviceParameters =
                serviceParametersForExecution(Bytes.fromHexString(deployHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000086976616e6976616e000000000000000000000000000000000000000000000000");
    }

    @Test
    void stateChangeWorksWithDynamicEthCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // writeToStorageSlot(string)
        final var stateChange =
                "000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var stateChangeHash = "0x9ac27b62" + stateChange;
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x" + stateChange);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void ercPrecompileCallRevertsForEstimateGas() {
        final var tokenNameCall = "0x6f0fccab0000000000000000000000000000000000000000000000000000000000000416";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
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
