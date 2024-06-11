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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader.FEE_SCHEDULE_ENTITY_ID;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_USED_METRIC;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.TREASURY_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
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

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import io.github.bucket4j.Bucket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.data.Percentage;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
class ContractCallServiceTest extends Web3IntegrationTest {

    @Mock
    private Bucket gasLimitBucket;

    private final BinaryGasEstimator binaryGasEstimator;

    private final Store store;

    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;

    private final RecordFileService recordFileService;

    private final ThrottleProperties throttleProperties;

    private static final long EVM_V_34_BLOCK = 50L;

    private static final Address EVM_CODES_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1263));

    private static final Address ETH_CALL_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1260));

    private static final Address REVERTER_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1259));

    private static final Address ERC_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1258));

    @Value("classpath:contracts/EvmCodes/EvmCodes.bin")
    private Path EVM_CODES_BYTES_PATH;

    @Value("classpath:contracts/ERCTestContract/ERCTestContract.json")
    private Path ERC_ABI_PATH;

    @Value("classpath:contracts/InternalCaller/InternalCaller.json")
    private Path INTERNAL_CALLER_CONTRACT_ABI_PATH;

    @Value("classpath:contracts/Reverter/Reverter.bin")
    private Path REVERTER_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/TestContractAddress/TestAddressThis.bin")
    private Path ADDRESS_THIS_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/TestContractAddress/TestAddressThisInit.bin")
    private Path ADDRESS_THIS_CONTRACT_INIT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/State.bin")
    private Path STATE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/EthCallInit.bin")
    private Path ETH_CALL_INIT_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/EthCall.bin")
    private Path ETH_CALL_CONTRACT_BYTES_PATH;

    private static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();

    private final FunctionEncodeDecoder functionEncodeDecoder;

    private final MirrorEvmTxProcessor processor;

    private final ContractCallService contractCallService;

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

        return Arrays.stream(CallType.values())
                .filter(callType -> !callType.equals(ERROR))
                .flatMap(callType -> gasLimits.stream().map(gasLimit -> Arguments.of(callType, gasLimit)));
    }

    @BeforeEach
    void setup() {
        meterRegistry.clear();
        feeSchedulesPersist();
        fileDataPersist();
        persistContract(EVM_CODES_BYTES_PATH, EVM_CODES_CONTRACT_ADDRESS);
    }

    @Test
    void callWithoutDataToAddressWithNoBytecodeReturnsEmptyResult() {
        final var ethAddress = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.EMPTY, ethAddress, ETH_CALL, 0L, BlockType.LATEST, 15_000_000L, senderAddress);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void pureCall() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";

        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @ParameterizedTest
    @MethodSource("provideBlockTypes")
    void pureCallWithBlock(BlockType blockType) {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";

        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                blockType,
                15_000_000L,
                senderAddress);

        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
            assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
        }

        assertGasLimit(serviceParameters);
    }

    @ParameterizedTest
    @MethodSource("provideCustomBlockTypes")
    void pureCallWithCustomBlock(BlockType blockType, String expectedResponse, boolean checkGas) {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var pureFuncHash = "8070450f";

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                blockType,
                15_000_000L,
                senderAddress);

        // we need entities present before the block timestamp of the custom block because we won't find them
        // when searching against the custom block timestamp
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(expectedResponse);

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

        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.of("0x2540BE3FF"),
                15_000_000L,
                senderAddress);

        assertThrows(BlockNumberOutOfRangeException.class, () -> {
            contractCallService.processCall(serviceParameters);
        });
        assertGasLimit(serviceParameters);
    }

    @Test
    void estimateGasForPureCall() {
        final var pureFuncHash = "8070450f";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(pureFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasWithoutReceiver() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"),
                Address.ZERO,
                ETH_ESTIMATE_GAS,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
        assertGasLimit(serviceParameters);
    }

    @Test
    void viewCall() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var successfulReadResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(viewFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(viewFuncHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferFunds() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var receiverAddress = domainBuilder.entity().persist().getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"),
                Address.wrap(Bytes.wrap(receiverAddress)),
                ETH_CALL,
                7L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCall() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var senderAlias = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(ByteString.copyFrom(
                        Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"))
                .substring(2)
                .toByteArray())));
        final var senderEntityId = domainBuilder.entity().persist().getId();
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.num(senderEntityId.longValue())
                        .evmAddress(senderAlias.toArray())
                        .balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var balanceCall = "0x93423e9c000000000000000000000000" + senderAlias.toUnprefixedHexString();
        final var expectedBalance = "0x000000000000000000000000000000000000000000000000000000e8d4a51000";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var isSuccessful = contractCallService.processCall(serviceParameters);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountReturnsZero() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var systemAccountAddress = toAddress(EntityId.of(0, 0, 700));
        final var balanceCall = "0x93423e9c000000000000000000000000" + systemAccountAddress.toUnprefixedHexString();
        final var expectedBalance = "0x0000000000000000000000000000000000000000000000000000000000000000";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var result = contractCallService.processCall(serviceParameters);
        assertThat(result).isEqualTo(expectedBalance);
        assertGasLimit(serviceParameters);
    }

    @Test
    void balanceCallToNonSystemAccountReturnsBalance() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var balanceCall =
                "0x93423e9c000000000000000000000000" + EVM_CODES_CONTRACT_ADDRESS.toUnprefixedHexString();
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var result = contractCallService.processCall(serviceParameters);
        assertThat(Long.parseLong(result.substring(2), 16)).isNotZero();
        assertGasLimit(serviceParameters);
    }

    @Test
    void estimateGasForBalanceCall() {
        final var balanceCall =
                "0x93423e9c000000000000000000000000" + EVM_CODES_CONTRACT_ADDRESS.toUnprefixedHexString();
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(balanceCall),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
        assertGasLimit(serviceParameters);
    }

    @Test
    void testRevertDetailMessage() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var revertFunctionSignature = "0xa26388bb";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(revertFunctionSignature),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
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
        persistContract(REVERTER_CONTRACT_BYTES_PATH, REVERTER_CONTRACT_ADDRESS);
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(revertFunctions.functionSignature),
                REVERTER_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
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
                functionHash,
                evm46ValidationCalls.contractAddress,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                domainBuilder.entity().persist().getEvmAddress());

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(evm46ValidationCalls.data);
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
        final var internalCallsContractAddress = toAddress(EntityId.of(0, 0, 1270));
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                functionHash, internalCallsContractAddress, ETH_CALL, 0L, BlockType.LATEST, 15_000_000L, senderAddress);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(evm46ValidationCalls.data);
        assertGasLimit(serviceParameters);
    }

    @Test
    void nonExistingFunctionCall() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("1ab4f82c"),
                ERC_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
        assertGasLimit(serviceParameters);
    }

    @Test
    void invalidFunctionSig() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        final var wrongFunctionSignature = "0x542ec32e";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(wrongFunctionSignature),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED")
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var receiverAddress = domainBuilder.entity().persist().getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"),
                Address.wrap(Bytes.wrap(receiverAddress)),
                ETH_CALL,
                -5L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferExceedsBalance() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var receiverAddress = domainBuilder.entity().persist().getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString("0x"),
                Address.wrap(Bytes.wrap(receiverAddress)),
                ETH_CALL,
                1500000000000000000L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferThruContract() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var transferHbarsInput = "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e9";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(transferHbarsInput),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                90L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
        assertGasLimit(serviceParameters);
    }

    @Test
    void hollowAccountCreationWorks() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var transferHbarsInput = "0x80b9f03c00000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(transferHbarsInput),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                90L,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForStateChangeCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForCreate2ContractDeploy() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);

        final var deployViaCreate2Hash = "0xdbb6f04a";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(deployViaCreate2Hash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void estimateGasForDirectCreateContractDeploy() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForTopLevelContractCreate(
                ETH_CALL_INIT_CONTRACT_BYTES_PATH, ETH_ESTIMATE_GAS, Address.wrap(Bytes.wrap(senderAddress)));
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
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
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void ethCallForContractDeploy() {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForTopLevelContractCreate(
                ADDRESS_THIS_CONTRACT_INIT_BYTES_PATH, ETH_CALL, Address.wrap(Bytes.wrap(senderAddress)));

        String result = contractCallService.processCall(serviceParameters);

        assertGasLimit(serviceParameters);
        assertThat(result)
                .isEqualTo(Bytes.wrap(functionEncodeDecoder.getContractBytes(ADDRESS_THIS_CONTRACT_BYTES_PATH))
                        .toHexString());
    }

    @Test
    void nestedContractStateChangesWork() {
        final var stateContractAddress = toAddress(EntityId.of(0, 0, 1261));
        persistContract(STATE_CONTRACT_BYTES_PATH, stateContractAddress);
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var stateChangeHash =
                "0x51fecdca000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000004ed00000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000");
        assertGasLimit(serviceParameters);
    }

    @Test
    void contractCreationWork() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var deployHash =
                "0xc32723ed000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046976616e00000000000000000000000000000000000000000000000000000000";
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(deployHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000086976616e6976616e000000000000000000000000000000000000000000000000");
        assertGasLimit(serviceParameters);
    }

    @Test
    void stateChangeWorksWithDynamicEthCall() {
        persistContract(ETH_CALL_CONTRACT_BYTES_PATH, ETH_CALL_CONTRACT_ADDRESS);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // writeToStorageSlot(string)
        final var stateChange =
                "000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var stateChangeHash = "0x9ac27b62" + stateChange;
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(stateChangeHash),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_CALL,
                0,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x" + stateChange);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void ercPrecompileCallRevertsForEstimateGas() {
        final var tokenAddress = toAddress(EntityId.of(0, 0, 1046));
        final var tokenEntityId = fromEvmAddress(tokenAddress.toArrayUnsafe());
        final var autoRenewAddress = toAddress(EntityId.of(0, 0, 740));
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(tokenEntityId.getNum())
                        .evmAddress(tokenEvmAddress))
                .persist();
        final var tokenNameCall = "0x6f0fccab0000000000000000000000000000000000000000000000000000000000000416";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall),
                ETH_CALL_CONTRACT_ADDRESS,
                ETH_ESTIMATE_GAS,
                0,
                BlockType.LATEST,
                15_000_000L,
                senderAddress);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @ParameterizedTest
    @EnumSource(
            value = CallType.class,
            names = {"ETH_CALL", "ETH_ESTIMATE_GAS"},
            mode = INCLUDE)
    void ercPrecompileExceptionalHaltReturnsExpectedGasToBucket(final CallType callType) {
        final var spenderAlias = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(
                ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310")
                        .substring(2)
                        .toByteArray())));
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var fungibleTokenAddress = toAddress(EntityId.of(0, 0, 1046));
        final var functionHash =
                functionEncodeDecoder.functionHashFor("approve", ERC_ABI_PATH, fungibleTokenAddress, spenderAlias, 2L);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, Address.ZERO, callType, 100L, BlockType.LATEST, 15_000_000L, senderAddress);
        final var expectedUsedGasByThrottle =
                (long) (serviceParameters.getGas() * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractCallService(
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
    void ercPrecompileContractRevertReturnsExpectedGasToBucket(final CallType callType, final long gasLimit) {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var tokenNameCall = "0x6f0fccab0000000000000000000000000000000000000000000000000000000000000416";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall),
                ETH_CALL_CONTRACT_ADDRESS,
                callType,
                0,
                BlockType.LATEST,
                gasLimit,
                senderAddress);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline =
                (long) (serviceParameters.getGas() * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle = Math.min(gasLimit - expectedGasUsed, gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractCallService(
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
    void ercPrecompileSuccessReturnsExpectedGasToBucket(final CallType callType, final long gasLimit) {
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var tokenNameCall = "0x019848920000000000000000000000000000000000000000000000000000000000000416";
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(tokenNameCall),
                ERC_CONTRACT_ADDRESS,
                callType,
                0,
                BlockType.LATEST,
                gasLimit,
                senderAddress);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline =
                (long) (serviceParameters.getGas() * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle = Math.min(gasLimit - expectedGasUsed, gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractCallService(
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
        final var senderAddress = domainBuilder
                .entity()
                .customize(e -> e.balance(10000 * 100_000_000L))
                .persist()
                .getEvmAddress();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.EMPTY, address, ETH_CALL, 0L, BlockType.LATEST, 15_000_000L, senderAddress);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    private double getGasUsedBeforeExecution(final CallType callType) {
        final var callCounter = meterRegistry.find(GAS_USED_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst();

        var gasUsedBeforeExecution = 0d;
        if (callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private void assertGasUsedIsPositive(final double gasUsedBeforeExecution, final CallType callType) {
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

    private CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallType callType,
            final long value,
            final BlockType block,
            long gasLimit,
            byte[] senderAddress) {
        HederaEvmAccount sender;
        if (block != BlockType.LATEST) {
            final var senderAddressHistorical = toAddress(EntityId.of(0, 0, 1014));
            sender = new HederaEvmAccount(senderAddressHistorical);
        } else {
            sender = new HederaEvmAccount(Address.wrap(Bytes.wrap(senderAddress)));
        }

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(gasLimit)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    private CallServiceParameters serviceParametersForTopLevelContractCreate(
            final Path contractInitCodePath, final CallType callType, final Address senderAddress) {
        final var sender = new HederaEvmAccount(senderAddress);
        // in the end, this persist will be removed because every test
        // will be responsible to persist its own needed data

        final var callData = Bytes.wrap(functionEncodeDecoder.getContractBytes(contractInitCodePath));
        return CallServiceParameters.builder()
                .sender(sender)
                .callData(callData)
                .receiver(Address.ZERO)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(BlockType.LATEST)
                .build();
    }

    private void feeSchedulesPersist() {
        final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setNextFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(ContractCall)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build())))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray()).entityId(FEE_SCHEDULE_ENTITY_ID))
                .persist();
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

    private void fileDataPersist() {
        final long nanos = 1_234_567_890L;
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder()
                        .setCentEquiv(1)
                        .setHbarEquiv(12)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(nanos))
                        .build())
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(2)
                        .setHbarEquiv(31)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                        .build())
                .build();
        final var entityId = EntityId.of(0L, 0L, 112L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray()).entityId(entityId))
                .persist();
    }

    private void persistContract(Path contractBytesPath, Address evmAddress) {
        final var ethCallContractBytes = functionEncodeDecoder.getContractBytes(contractBytesPath);
        final var ethCallContractEntityId = fromEvmAddress(evmAddress.toArrayUnsafe());
        final var ethCallContractEvmAddress = toEvmAddress(ethCallContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(ethCallContractEntityId.getId())
                        .num(ethCallContractEntityId.getNum())
                        .evmAddress(ethCallContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(ethCallContractEntityId.getId()).runtimeBytecode(ethCallContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(ethCallContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(ethCallContractBytes)).persist();
    }

    /**
     * Checks if the *actual* gas usage is within 5-20% greater than the *expected* gas used from the initial call.
     *
     * @param actualGas   The actual gas used.
     * @param expectedGas The expected gas used from the initial call.
     * @return {@code true} if the actual gas usage is within the expected range, otherwise {@code false}.
     */
    private boolean isWithinExpectedGasRange(final long actualGas, final long expectedGas) {
        return actualGas >= (expectedGas * 1.05) && actualGas <= (expectedGas * 1.20);
    }

    private long gasUsedAfterExecution(final CallServiceParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }
}
