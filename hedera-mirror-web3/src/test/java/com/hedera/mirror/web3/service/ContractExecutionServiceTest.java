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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_USED_METRIC;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.BaseCallServiceParameters;
import com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import io.github.bucket4j.Bucket;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

class ContractExecutionServiceTest extends ContractCallTestSetup {

    @Mock
    private Bucket gasLimitBucket;

    @Autowired
    private BinaryGasEstimator binaryGasEstimator;

    @Autowired
    private Store store;

    @Autowired
    private MirrorEvmTxProcessor mirrorEvmTxProcessor;

    @Autowired
    private RecordFileService recordFileService;

    @Autowired
    private ContractActionService contractActionService;

    @Autowired
    private ThrottleProperties throttleProperties;

    static Stream<BlockType> provideBlockTypes() {
        return Stream.of(
                BlockType.EARLIEST,
                BlockType.of("safe"),
                BlockType.of("pending"),
                BlockType.of("finalized"),
                BlockType.LATEST);
    }

    static Stream<Arguments> provideCustomBlockTypes() {
        return Stream.of(
                Arguments.of(BlockType.of("0x1"), "0x", false),
                Arguments.of(
                        BlockType.of("0x100"),
                        "0x0000000000000000000000000000000000000000000000000000000000000004",
                        true));
    }

    private static Stream<Arguments> ercPrecompileCallTypeArgumentsProvider() {
        List<Long> gasLimits = List.of(15_000_000L, 30_000L);

        return Arrays.stream(BaseCallServiceParameters.CallType.values())
                .filter(callType -> !callType.equals(ERROR))
                .flatMap(callType -> gasLimits.stream().map(gasLimit -> Arguments.of(callType, gasLimit)));
    }

    @BeforeEach
    void setup() {
        // reset gas metrics
        meterRegistry.clear();
    }

    @Test
    void callWithoutDataToAddressWithNoBytecodeReturnsEmptyResult() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters =
                serviceParametersForExecution(Bytes.EMPTY, ETH_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo("0x");

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void pureCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @ParameterizedTest
    @MethodSource("provideBlockTypes")
    void pureCallWithBlock(BlockType blockType) {
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, blockType);

        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
            assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
        }

        assertGasLimit(serviceParameters);
    }

    @ParameterizedTest
    @MethodSource("provideCustomBlockTypes")
    void pureCallWithCustomBlock(BlockType blockType, String expectedResponse, boolean checkGas) {
        final var pureFuncHash = "8070450f";

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, blockType);

        // we need entities present before the block timestamp of the custom block because we won't find them
        // when searching against the custom block timestamp
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(expectedResponse);

            if (checkGas) {
                assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
            }
        }

        assertGasLimit(serviceParameters);
    }

    @Test
    void pureCallWithOutOfRangeCustomBlockThrowsException() {
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(1L))
                .persist();

        final var pureFuncHash = "8070450f";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.of("0x2540BE3FF"));

        assertThrows(BlockNumberOutOfRangeException.class, () -> {
            contractExecutionService.processCall(serviceParameters);
        });
        assertGasLimit(serviceParameters);
    }

    @Test
    void estimateGasForPureCall() {
        final var pureFuncHash = "8070450f";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasWithoutReceiver() {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"), Address.ZERO, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
        assertGasLimit(serviceParameters);
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
                Bytes.fromHexString(viewFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(viewFuncHash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferFunds() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"), RECEIVER_ADDRESS, ETH_CALL, 7L, BlockType.LATEST);

        assertThatCode(() -> contractExecutionService.processCall(serviceParameters)).doesNotThrowAnyException();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // getAccountBalance(address)
        // Use alias address when applicable as EVM checks alias with highest priority
        final var balanceCall = "0x93423e9c000000000000000000000000" + SENDER_ALIAS.toUnprefixedHexString();
        final var expectedBalance = "0x000000000000000000000000000000000000000000000000000000e8d4a51000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        final var isSuccessful = contractExecutionService.processCall(serviceParameters);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountReturnsZero() {
        // getAccountBalance(address)
        final var balanceCall = "0x93423e9c000000000000000000000000" + SYSTEM_ACCOUNT_ADDRESS.toUnprefixedHexString();
        final var expectedBalance = "0x0000000000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        final var result = contractExecutionService.processCall(serviceParameters);
        assertThat(result).isEqualTo(expectedBalance);
        assertGasLimit(serviceParameters);
    }

    @Test
    void balanceCallToNonSystemAccountReturnsBalance() {
        // getAccountBalance(address)
        final var balanceCall =
                "0x93423e9c000000000000000000000000" + EVM_CODES_CONTRACT_ADDRESS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        final var result = contractExecutionService.processCall(serviceParameters);
        assertThat(Long.parseLong(result.substring(2), 16)).isNotZero();
        assertGasLimit(serviceParameters);
    }

    @Test
    void estimateGasForBalanceCall() {
        final var balanceCall =
                "0x93423e9c000000000000000000000000" + EVM_CODES_CONTRACT_ADDRESS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
        assertGasLimit(serviceParameters);
    }

    @Test
    void testRevertDetailMessage() {
        final var revertFunctionSignature = "0xa26388bb";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(revertFunctionSignature),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Custom revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000");
        assertGasLimit(serviceParameters);
    }

    @ParameterizedTest
    @EnumSource(RevertFunctions.class)
    void testReverts(final RevertFunctions revertFunctions) {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(revertFunctions.functionSignature),
                REVERTER_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", revertFunctions.errorDetail)
                .hasFieldOrPropertyWithValue("data", revertFunctions.errorData);
        assertGasLimit(serviceParameters);
    }

    @ParameterizedTest
    @EnumSource(EVM46ValidationCalls.class)
    void testEVM46ValidationCalls(final EVM46ValidationCalls evm46ValidationCalls) {
        final var functionHash = !evm46ValidationCalls.function.isEmpty()
                ? functionEncodeDecoder.functionHashFor(
                        evm46ValidationCalls.function, ERC_ABI_PATH, evm46ValidationCalls.functionParams)
                : Bytes.EMPTY;
        final var serviceParameters = serviceParametersForExecution(
                functionHash, evm46ValidationCalls.contractAddress, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(evm46ValidationCalls.data);
        assertGasLimit(serviceParameters);
    }

    @ParameterizedTest
    @EnumSource(EVM46ValidationInternalCalls.class)
    void testEVM46ValidationInternalCalls(final EVM46ValidationInternalCalls evm46ValidationCalls) {
        final var functionHash = !evm46ValidationCalls.function.isEmpty()
                ? functionEncodeDecoder.functionHashFor(
                        evm46ValidationCalls.function,
                        INTERNAL_CALLER_CONTRACT_ABI_PATH,
                        evm46ValidationCalls.functionParams)
                : Bytes.EMPTY;
        final var serviceParameters = serviceParametersForExecution(
                functionHash, INTERNAL_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(evm46ValidationCalls.data);
        assertGasLimit(serviceParameters);
    }

    @Test
    void nonExistingFunctionCall() {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("1ab4f82c"), ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo("0x");
        assertGasLimit(serviceParameters);
    }

    @Test
    void invalidFunctionSig() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        final var wrongFunctionSignature = "0x542ec32e";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(wrongFunctionSignature), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED")
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"), RECEIVER_ADDRESS, ETH_CALL, -5L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"), RECEIVER_ADDRESS, ETH_CALL, 1500000000000000000L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferThruContract() {
        // transferHbarsToAddress(address)
        final var transferHbarsInput = "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e9";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(transferHbarsInput), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 90L, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo("0x");
        assertGasLimit(serviceParameters);
    }

    @Test
    void hollowAccountCreationWorks() {
        // transferHbarsToAddress(address)
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var transferHbarsInput = "0x80b9f03c00000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(transferHbarsInput),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                90L,
                BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForStateChangeCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForCreate2ContractDeploy() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);

        // deployViaCreate2()
        final var deployViaCreate2Hash = "0xdbb6f04a";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(deployViaCreate2Hash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0,
                BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForDirectCreateContractDeploy() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);

        final var serviceParameters = serviceParametersForTopLevelContractCreate(
                ETH_CALL_INIT_CONTRACT_BYTES_PATH, ETH_ESTIMATE_GAS, SENDER_ADDRESS);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForDirectCreateContractDeployWithMissingSender() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);

        final var serviceParameters = serviceParametersForTopLevelContractCreate(
                ETH_CALL_INIT_CONTRACT_BYTES_PATH, ETH_ESTIMATE_GAS, Address.ZERO);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void ethCallForContractDeploy() {
        final var serviceParameters = serviceParametersForTopLevelContractCreate(
                ADDRESS_THIS_CONTRACT_INIT_BYTES_PATH, ETH_CALL, SENDER_ADDRESS);

        String result = contractExecutionService.processCall(serviceParameters);

        assertGasLimit(serviceParameters);
        assertThat(result)
                .isEqualTo(Bytes.wrap(functionEncodeDecoder.getContractBytes(ADDRESS_THIS_CONTRACT_BYTES_PATH))
                        .toHexString());
    }

    @Test
    void nestedContractStateChangesWork() {
        final var stateChangeHash =
                "0x51fecdca000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000004ed00000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000");
        assertGasLimit(serviceParameters);
    }

    @Test
    void contractCreationWork() {
        final var deployHash =
                "0xc32723ed000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(deployHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000086976616e6976616e000000000000000000000000000000000000000000000000");
        assertGasLimit(serviceParameters);
    }

    @Test
    void stateChangeWorksWithDynamicEthCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // writeToStorageSlot(string)
        final var stateChange =
                "000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var stateChangeHash = "0x9ac27b62" + stateChange;
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash), ETH_CALL_CONTRACT_ADDRESS, ETH_CALL, 0, BlockType.LATEST);

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo("0x" + stateChange);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void ercPrecompileCallRevertsForEstimateGas() {
        final var tokenNameCall = "0x6f0fccab0000000000000000000000000000000000000000000000000000000000000416";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall), ETH_CALL_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @ParameterizedTest
    @EnumSource(
            value = BaseCallServiceParameters.CallType.class,
            names = {"ETH_CALL", "ETH_ESTIMATE_GAS"},
            mode = INCLUDE)
    void ercPrecompileExceptionalHaltReturnsExpectedGasToBucket(final BaseCallServiceParameters.CallType callType) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "approve", ERC_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS, 2L);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, Address.ZERO, callType, 100L, BlockType.LATEST);
        final var expectedUsedGasByThrottle =
                (long) (serviceParameters.getGas() * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                gasLimitBucket);

        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }
        verify(gasLimitBucket).addTokens(expectedUsedGasByThrottle);
        verify(gasLimitBucket, times(1)).addTokens(anyLong());
    }

    @ParameterizedTest
    @MethodSource("ercPrecompileCallTypeArgumentsProvider")
    void ercPrecompileContractRevertReturnsExpectedGasToBucket(final BaseCallServiceParameters.CallType callType, final long gasLimit) {
        final var tokenNameCall = "0x6f0fccab0000000000000000000000000000000000000000000000000000000000000416";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall), ETH_CALL_CONTRACT_ADDRESS, callType, 0, BlockType.LATEST, gasLimit);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline =
                (long) (serviceParameters.getGas() * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle = Math.min(gasLimit - expectedGasUsed, gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                gasLimitBucket);

        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }
        verify(gasLimitBucket).addTokens(expectedUsedGasByThrottle);
        verify(gasLimitBucket, times(1)).addTokens(anyLong());
    }

    @ParameterizedTest
    @MethodSource("ercPrecompileCallTypeArgumentsProvider")
    void ercPrecompileSuccessReturnsExpectedGasToBucket(final BaseCallServiceParameters.CallType callType, final long gasLimit) {
        final var tokenNameCall = "0x019848920000000000000000000000000000000000000000000000000000000000000416";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall), ERC_CONTRACT_ADDRESS, callType, 0, BlockType.LATEST, gasLimit);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline =
                (long) (serviceParameters.getGas() * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle = Math.min(gasLimit - expectedGasUsed, gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                gasLimitBucket);

        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }
        verify(gasLimitBucket).addTokens(expectedUsedGasByThrottle);
        verify(gasLimitBucket, times(1)).addTokens(anyLong());
    }

    @ParameterizedTest
    @CsvSource({
        "0000000000000000000000000000000000000167",
        "0000000000000000000000000000000000000168",
        "0000000000000000000000000000000000000169"
    })
    void callSystemPrecompileWithEmptyData(String addressHex) {
        final var address = Address.fromHexString(addressHex);
        final var serviceParameters =
                serviceParametersForExecution(Bytes.EMPTY, address, ETH_CALL, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    private double getGasUsedBeforeExecution(final BaseCallServiceParameters.CallType callType) {
        final var callCounter = meterRegistry.find(GAS_USED_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst();

        var gasUsedBeforeExecution = 0d;
        if (callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private void assertGasUsedIsPositive(final double gasUsedBeforeExecution, final BaseCallServiceParameters.CallType callType) {
        final var counter = meterRegistry.find(GAS_USED_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        final var gasConsumed = counter.count() - gasUsedBeforeExecution;
        assertThat(gasConsumed).isPositive();
    }

    private void assertGasLimit(CallServiceParameters parameters) {
        final var counter = meterRegistry.find(GAS_LIMIT_METRIC).counters().stream()
                .filter(c -> parameters.getCallType().name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        assertThat(counter.count()).isEqualTo(parameters.getGas());
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

    @RequiredArgsConstructor
    private enum EVM46ValidationCalls {
        CALL_TO_NON_EXISTING_CONTRACT(
                "callToNonExistingContract", toAddress(EntityId.of(0, 0, 123456789)), "", new Object[] {0}, "0x"),
        TRANSFER_TO_NON_EXISTING_CONTRACT(
                "transferToNonExistingContractName",
                toAddress(EntityId.of(0, 0, 123456789)),
                "transfer",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, 1L},
                "0x");

        private final String name;
        private final Address contractAddress;
        private final String function;
        private final Object[] functionParams;
        private final String data;
    }

    @RequiredArgsConstructor
    private enum EVM46ValidationInternalCalls {
        CALL_TO_INTERNAL_NON_EXISTING_CONTRACT(
                "callToInternalNonExistingContract",
                "callNonExisting",
                new Object[] {toAddress(EntityId.of(0, 0, 123456789))},
                "0x"),
        CALL_TO_INTERNAL_NON_EXISTING_FUNCTION(
                "callToInternalNonExistingContract", "callNonExisting", new Object[] {ERC_CONTRACT_ADDRESS}, "0x"),
        CALL_TO_INTERNAL_WITH_VALUE_TO_NON_EXISTING_FUNCTION(
                "callToInternalWithValueToNonExistingContract",
                "callWithValueTo",
                new Object[] {ERC_CONTRACT_ADDRESS},
                "0x"),
        SEND_TO_INTERNAL_NON_EXISTING_ACCOUNT(
                "sendToInternalNonExistingContract",
                "sendTo",
                new Object[] {toAddress(EntityId.of(0, 0, 123456789))},
                "0x"),
        TRANSFER_TO_INTERNAL_NON_EXISTING_ACCOUNT(
                "transferToInternalNonExistingContract",
                "transferTo",
                new Object[] {toAddress(EntityId.of(0, 0, 123456789))},
                "0x");

        private final String name;
        private final String function;
        private final Object[] functionParams;
        private final String data;
    }
}
