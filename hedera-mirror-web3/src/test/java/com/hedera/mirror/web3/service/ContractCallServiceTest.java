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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_USED_METRIC;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.EthCall;
import com.hedera.mirror.web3.web3j.generated.State;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import io.github.bucket4j.Bucket;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

class ContractCallServiceTest extends AbstractContractCallServiceTest {

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
    private ThrottleProperties throttleProperties;

    @Autowired
    private ContractExecutionService contractExecutionService;

    @BeforeEach
    void setup() {
        // reset gas metrics
        super.setup();
        meterRegistry.clear();
    }

    @Test
    void callWithoutDataToAddressWithNoBytecodeReturnsEmptyResult() {
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAddressFromEntity(receiverEntity);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, receiverAddress);

        final var result = contractExecutionService.processCall(serviceParameters);

        assertThat(result).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void pureCall() throws Exception {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

        final var result = contract.call_multiplySimpleNumbers().send();

        assertThat(result).isEqualTo(BigInteger.valueOf(4L));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @ParameterizedTest
    @MethodSource("provideBlockTypes")
    void pureCallWithBlock(BlockType blockType) throws Exception {
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockType.number())));
            testWeb3jService.setBlockType(blockType);
            assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(functionCall.send()).isEqualTo(BigInteger.valueOf(4L));
            assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
        }

        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @ParameterizedTest
    @MethodSource("provideCustomBlockTypes")
    void pureCallWithCustomBlock(BlockType blockType, String expectedResponse, boolean checkGas) throws Exception {
        // we need entities present before the block timestamp of the custom block because we won't find them
        // when searching against the custom block timestamp
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockType.number())));
            testWeb3jService.setBlockType(blockType);
            assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(functionCall.send()).isEqualTo(new BigInteger(expectedResponse.substring(2), 16));

            if (checkGas) {
                assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
            }
        }

        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void pureCallWithOutOfRangeCustomBlockThrowsException() {
        final var invalidBlock = BlockType.of("0x2540BE3FF");
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric
        contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(invalidBlock.number())));
        testWeb3jService.setBlockType(invalidBlock);

        assertThatThrownBy(functionCall::send).isInstanceOf(BlockNumberOutOfRangeException.class);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void estimateGasForPureCall() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.send_multiplySimpleNumbers();

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void estimateGasWithoutReceiver() {
        final var serviceParametersEthCall =
                getContractExecutionParameters(Bytes.fromHexString(HEX_PREFIX), Address.ZERO, ETH_CALL);
        final var actualGasUsed = gasUsedAfterExecution(serviceParametersEthCall);
        final var serviceParametersEstimateGas =
                getContractExecutionParameters(Bytes.fromHexString(HEX_PREFIX), Address.ZERO, ETH_ESTIMATE_GAS);
        final var estimatedGasUsed =
                longValueOf.applyAsLong(contractExecutionService.processCall(serviceParametersEstimateGas));

        assertThat(isWithinExpectedGasRange(estimatedGasUsed, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimatedGasUsed, actualGasUsed)
                .isTrue();
    }

    @Test
    void viewCall() throws Exception {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var result = contract.call_returnStorageData().send();

        assertThat(result).isEqualTo("test");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.send_returnStorageData();

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFunds() {
        final var senderEntity = accountPersist();
        final var receiverEntity = accountPersist();
        final var senderAddress = getAddressFromEntity(senderEntity);
        final var receiverAddress = getAddressFromEntity(receiverEntity);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, receiverAddress, senderAddress, 7L);

        assertDoesNotThrow(() -> contractExecutionService.processCall(serviceParameters));

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToNonSystemAccount() throws Exception {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var accountEntity = accountPersist();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var result = contract.call_getAccountBalance(
                        getAddressFromEntity(accountEntity).toHexString())
                .send();

        assertThat(result).isEqualTo(BigInteger.valueOf(accountEntity.getBalance()));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountReturnsZero() throws Exception {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var systemAccountEntity = systemAccountPersist();
        final var systemAccountAddress = EntityIdUtils.asHexedEvmAddress(
                new Id(systemAccountEntity.getShard(), systemAccountEntity.getRealm(), systemAccountEntity.getNum()));
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var result = contract.call_getAccountBalance(systemAccountAddress).send();

        assertThat(result).isEqualTo(BigInteger.ZERO);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountViaAliasReturnsBalance() throws Exception {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var systemAccountEntity = systemAccountPersist();
        final var systemAccountAddress =
                Bytes.wrap(systemAccountEntity.getEvmAddress()).toHexString();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var result = contract.call_getAccountBalance(systemAccountAddress).send();

        assertThat(result).isEqualTo(BigInteger.valueOf(systemAccountEntity.getBalance()));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToContractReturnsBalance() throws Exception {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        final var result =
                contract.call_getAccountBalance(contract.getContractAddress()).send();

        assertThat(result.longValue()).isNotZero();
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void estimateGasForBalanceCallToContract() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        final var functionCall = contract.send_getAccountBalance(contract.getContractAddress());

        verifyEthCallAndEstimateGas(functionCall, contract);
        assertGasLimit(ETH_ESTIMATE_GAS, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertDetailMessage() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        final var functionCall = contract.send_testRevert();

        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Custom revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Nested
    class EVM46Validation {

        private static final Address NON_EXISTING_ADDRESS = toAddress(123456789);

        @Test
        void callToNonExistingContract() {
            final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, NON_EXISTING_ADDRESS);

            final var result = contractExecutionService.processCall(serviceParameters);

            assertThat(result).isEqualTo(HEX_PREFIX);
            assertGasLimit(serviceParameters);
        }

        @Test
        void transferToNonExistingContract() {
            final var serviceParameters =
                    getContractExecutionParametersWithValue(Bytes.EMPTY, NON_EXISTING_ADDRESS, 1L);

            final var result = contractExecutionService.processCall(serviceParameters);

            assertThat(result).isEqualTo(HEX_PREFIX);
            assertGasLimit(serviceParameters);
        }
    }

    @Test
    void nonExistingFunctionCallWithFallback() {
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        meterRegistry.clear();
        final var serviceParameters = getContractExecutionParameters(
                Bytes.fromHexString("0x12345678"), Address.fromHexString(contract.getContractAddress()));

        assertThat(contractExecutionService.processCall(serviceParameters)).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
    }

    @Test
    void invalidFunctionSig() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var wrongFunctionSignature = "0x12345678";
        final var serviceParameters = getContractExecutionParameters(
                Bytes.fromHexString(wrongFunctionSignature), Address.fromHexString(contract.getContractAddress()));

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("data", HEX_PREFIX);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAddressFromEntity(receiverEntity);
        final var serviceParameters = getContractExecutionParametersWithValue(Bytes.EMPTY, receiverAddress, -5L);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferExceedsBalance() {
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAddressFromEntity(receiverEntity);
        final var senderEntity = accountPersist();
        final var senderAddress = getAddressFromEntity(senderEntity);
        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.EMPTY, senderAddress, receiverAddress, senderEntity.getBalance() + 5L);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferThruContract() throws Exception {
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAddressFromEntity(receiverEntity);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        contract.send_transferHbarsToAddress(receiverAddress.toHexString(), BigInteger.TEN)
                .send();

        assertThat(testWeb3jService.getTransactionResult()).isEqualTo(HEX_PREFIX);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void hollowAccountCreationWorks() {
        final var value = 10L;
        final var hollowAccountAlias = domainBuilder.evmAddress();
        final var senderEntity = accountPersist();
        final var senderAddress = getAddressFromEntity(senderEntity);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        testWeb3jService.setSender(senderAddress);

        final var functionCall = contract.send_transferHbarsToAddress(
                Bytes.wrap(hollowAccountAlias).toHexString(), BigInteger.valueOf(value));

        verifyEthCallAndEstimateGasWithValue(functionCall, contract, senderAddress, value);
    }

    @Test
    void estimateGasForStateChangeCall() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.send_writeToStorageSlot("test2", BigInteger.ZERO);

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void estimateGasForCreate2ContractDeploy() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.send_deployViaCreate2();

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void estimateGasForDirectCreateContractDeploy() {
        final var senderEntity = accountPersist();
        final var senderAddress = getAddressFromEntity(senderEntity);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, senderAddress);
        final var actualGas = 183552L;
        final var estimatedGas = longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters));

        assertThat(isWithinExpectedGasRange(estimatedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimatedGas, actualGas)
                .isTrue();
    }

    @Test
    void estimateGasForDirectCreateContractDeployWithMissingSender() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final var actualGas = 183552L;
        final var estimatedGas = longValueOf.applyAsLong(contractExecutionService.processCall(serviceParameters));

        assertThat(isWithinExpectedGasRange(estimatedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimatedGas, actualGas)
                .isTrue();
    }

    @Test
    void ethCallForContractDeploy() {
        final var contract = testWeb3jService.deployWithoutPersist(EthCall::deploy);
        meterRegistry.clear();
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_CALL, Address.ZERO);

        final var result = contractExecutionService.processCall(serviceParameters);

        assertGasLimit(serviceParameters);
        assertThat(result)
                .isEqualTo(Bytes.wrap(testWeb3jService.getContractRuntime()).toHexString());
    }

    @Test
    void nestedContractStateChangesWork() throws Exception {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var stateContract = testWeb3jService.deploy(State::deploy);
        meterRegistry.clear();
        final var result = contract.call_nestedCall("testState", stateContract.getContractAddress())
                .send();

        assertThat(result).isEqualTo("testState");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void contractCreationWork() throws Exception {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        final var result = contract.call_deployContract("state").send();

        // "state" is set in the State contract and the State contract updates the state of the parent to the
        // concatenation of the string "state" twice, resulting in this value
        assertThat(result).isEqualTo("statestate");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void stateChangeWorksWithDynamicEthCall() throws Exception {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        final var newState = "newState";
        final var result = contract.call_writeToStorageSlot(newState).send();
        assertThat(result).isEqualTo(newState);

        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void ercPrecompileCallForEstimateGas() {
        final var token = tokenPersist();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var result = contract.send_getTokenName(toAddress(token.getId()).toHexString());

        verifyEthCallAndEstimateGas(result, contract);
    }

    @ParameterizedTest
    @EnumSource(
            value = CallType.class,
            names = {"ETH_CALL", "ETH_ESTIMATE_GAS"},
            mode = INCLUDE)
    void ercPrecompileExceptionalHaltReturnsExpectedGasToBucket(final CallType callType) {
        final var token = tokenPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.send_approve(
                toAddress(token.getId()).toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(2));

        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.fromHexString(functionCall.encodeFunctionCall()), Address.ZERO, Address.ZERO, callType, 100L);
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
    void ercPrecompileContractRevertReturnsExpectedGasToBucket(final CallType callType, final long gasLimit) {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_getTokenName(Address.ZERO.toHexString());

        final var serviceParameters = getContractExecutionParameters(functionCall, contract, callType, gasLimit);
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
    void ercPrecompileSuccessReturnsExpectedGasToBucket(final CallType callType, final long gasLimit) {
        final var token = tokenPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.call_name(toAddress(token.getId()).toHexString());

        final var serviceParameters = getContractExecutionParameters(functionCall, contract, callType, gasLimit);
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
    void callSystemPrecompileWithEmptyData(final String addressHex) {
        final var address = Address.fromHexString(addressHex);
        final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, address);

        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
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

    private void assertGasLimit(ContractExecutionParameters parameters) {
        assertGasLimit(parameters.getCallType(), parameters.getGas());
    }

    private void assertGasLimit(final CallType callType, final long gasLimit) {
        final var counter = meterRegistry.find(GAS_LIMIT_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        assertThat(counter.count()).isEqualTo(gasLimit);
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiverAddress) {
        return getContractExecutionParameters(data, receiverAddress, ETH_CALL);
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiverAddress, final CallType callType) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(callType)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(callType == ETH_ESTIMATE_GAS)
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(new HederaEvmAccount(Address.ZERO))
                .value(0L)
                .build();
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall,
            final Contract contract,
            final CallType callType,
            final long gasLimit) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(callType)
                .gas(gasLimit)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(new HederaEvmAccount(Address.ZERO))
                .value(0L)
                .build();
    }

    private ContractExecutionParameters getContractExecutionParametersWithValue(
            final Bytes data, final Address receiverAddress, final long value) {
        return getContractExecutionParametersWithValue(data, Address.ZERO, receiverAddress, value);
    }

    private ContractExecutionParameters getContractExecutionParametersWithValue(
            final Bytes data, final Address senderAddress, final Address receiverAddress, final long value) {
        return getContractExecutionParametersWithValue(data, senderAddress, receiverAddress, ETH_CALL, value);
    }

    private ContractExecutionParameters getContractExecutionParametersWithValue(
            final Bytes data,
            final Address senderAddress,
            final Address receiverAddress,
            final CallType callType,
            final long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(callType)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(new HederaEvmAccount(senderAddress))
                .value(value)
                .build();
    }

    private Entity accountPersist() {
        return domainBuilder.entity().persist();
    }

    private Entity systemAccountPersist() {
        final var systemAccountEntityId = EntityId.of(700);

        return domainBuilder
                .entity()
                .customize(e -> e.id(systemAccountEntityId.getId())
                        .num(systemAccountEntityId.getNum())
                        .alias(toEvmAddress(systemAccountEntityId))
                        .balance(20000L))
                .persist();
    }

    private Entity tokenPersist() {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        domainBuilder.token().customize(t -> t.tokenId(tokenEntity.getId())).persist();

        return tokenEntity;
    }

    private Address getAddressFromEntity(final Entity entity) {
        return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
    }

    private static Stream<BlockType> provideBlockTypes() {
        return Stream.of(
                BlockType.EARLIEST,
                BlockType.of("safe"),
                BlockType.of("pending"),
                BlockType.of("finalized"),
                BlockType.LATEST);
    }

    private static Stream<Arguments> provideCustomBlockTypes() {
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
}
