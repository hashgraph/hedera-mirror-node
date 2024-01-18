/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.contracts.execution;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmWorldState;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class MirrorEvmTxProcessorTest {

    private static final int MAX_STACK_SIZE = 1024;
    private final HederaEvmAccount sender = new HederaEvmAccount(Address.ALTBN128_ADD);
    private final HederaEvmAccount receiver = new HederaEvmAccount(Address.ALTBN128_MUL);
    private final Address receiverAddress = receiver.canonicalAddress();

    @Mock
    private PricesAndFeesProvider pricesAndFeesProvider;

    @Mock
    private HederaEvmWorldState worldState;

    @Mock
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private Set<Operation> operations;

    @Mock
    private HederaEvmWorldState.Updater updater;

    @Mock
    private HederaEvmStackedWorldStateUpdater stackedUpdater;

    @Mock
    private MirrorEvmContractAliases hederaEvmContractAliases;

    @Mock
    private HederaBlockValues hederaBlockValues;

    @Mock
    private BlockMetaSource blockMetaSource;

    @Mock
    private MirrorOperationTracer mirrorOperationTracer;

    @Mock
    private StoreImpl store;

    private MirrorEvmTxProcessorImpl mirrorEvmTxProcessor;
    private Pair<ResponseCodeEnum, Long> result;

    private String mcpVersion;
    private String ccpVersion;
    private static final String FUNCTION_HASH = "0x8070450f";

    @BeforeEach
    void setup() {
        setupGasCalculator();
        final var operationRegistry = new OperationRegistry();
        MainnetEVMs.registerShanghaiOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        operations.forEach(operationRegistry::put);
        final String EVM_VERSION_0_34 = "v0.34";
        final var v30 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.LONDON);
        final var v34 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.PARIS);
        final var v38 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.SHANGHAI);
        final Map<String, Provider<MessageCallProcessor>> mcps = Map.of(
                EVM_VERSION_0_30,
                () -> {
                    mcpVersion = EVM_VERSION_0_30;
                    return new MessageCallProcessor(v30, new PrecompileContractRegistry());
                },
                EVM_VERSION_0_34,
                () -> {
                    mcpVersion = EVM_VERSION_0_34;
                    return new MessageCallProcessor(v34, new PrecompileContractRegistry());
                },
                EVM_VERSION_0_38,
                () -> {
                    mcpVersion = EVM_VERSION_0_38;
                    return new MessageCallProcessor(v38, new PrecompileContractRegistry());
                });
        Map<String, Provider<ContractCreationProcessor>> processorsMap = Map.of(
                EVM_VERSION_0_30, () -> new ContractCreationProcessor(gasCalculator, v30, true, List.of(), 1),
                EVM_VERSION_0_34, () -> new ContractCreationProcessor(gasCalculator, v34, true, List.of(), 1),
                EVM_VERSION_0_38, () -> new ContractCreationProcessor(gasCalculator, v38, true, List.of(), 1));

        mirrorEvmTxProcessor = new MirrorEvmTxProcessorImpl(
                worldState,
                pricesAndFeesProvider,
                evmProperties,
                gasCalculator,
                mcps,
                processorsMap,
                blockMetaSource,
                hederaEvmContractAliases,
                new AbstractCodeCache(10, hederaEvmEntityAccess),
                mirrorOperationTracer,
                store,
                new EntityAddressSequencer());

        Pair<ResponseCodeEnum, Long> result = Pair.of(ResponseCodeEnum.OK, 100L);
    }

    @ParameterizedTest
    @MethodSource("provideIsEstimateParameters")
    void assertSuccessExecution(boolean isEstimate) {
        givenValidMockWithoutGetOrCreate();
        when(evmProperties.evmVersion()).thenReturn(EVM_VERSION_0_34);
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(pricesAndFeesProvider.currentGasPrice(any(), any())).willReturn(10L);
        final var params = CallServiceParameters.builder()
                .sender(sender)
                .receiver(receiver.canonicalAddress())
                .gas(33_333L)
                .value(1234L)
                .callData(Bytes.EMPTY)
                .isStatic(true)
                .isEstimate(isEstimate)
                .build();
        var result = mirrorEvmTxProcessor.execute(params, params.getGas());

        assertThat(result)
                .isNotNull()
                .returns(true, HederaEvmTransactionProcessingResult::isSuccessful)
                .returns(receiver.canonicalAddress(), r -> r.getRecipient().get());
    }

    @Test
    void missingCodeThrowsException() {
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        final MessageFrame.Builder protoFrame = MessageFrame.builder()
                .worldUpdater(updater)
                .initialGas(1L)
                .originator(sender.canonicalAddress())
                .gasPrice(Wei.ZERO)
                .sender(sender.canonicalAddress())
                .value(Wei.ONE)
                .apparentValue(Wei.ONE)
                .blockValues(hederaBlockValues)
                .completer(frame -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup(hash -> null);

        assertThatExceptionOfType(MirrorEvmTransactionException.class)
                .isThrownBy(() -> mirrorEvmTxProcessor.buildInitialFrame(
                        protoFrame, receiverAddress, Bytes.fromHexString(FUNCTION_HASH), 0L));
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(hederaEvmEntityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.EMPTY);
        given(hederaEvmContractAliases.isMirror(receiverAddress)).willReturn(true);
        final long GAS_LIMIT = 300_000L;
        final Wei oneWei = Wei.of(1L);
        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(mock(WorldUpdater.class))
                .initialGas(GAS_LIMIT)
                .originator(sender.canonicalAddress())
                .gasPrice(Wei.ZERO)
                .sender(sender.canonicalAddress())
                .value(oneWei)
                .apparentValue(oneWei)
                .blockValues(mock(BlockValues.class))
                .completer(__ -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup(h -> null);
        // when:
        final MessageFrame buildMessageFrame = mirrorEvmTxProcessor.buildInitialFrame(
                commonInitialFrame, receiver.canonicalAddress(), Bytes.EMPTY, 0L);

        // expect:
        assertThat(sender.canonicalAddress()).isEqualTo(buildMessageFrame.getSenderAddress());
        assertThat(oneWei).isEqualTo(buildMessageFrame.getApparentValue());
    }

    private void givenValidMockWithoutGetOrCreate() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(evmProperties.fundingAccountAddress()).willReturn(Address.ALTBN128_PAIRING);

        final var mutableAccount = mock(MutableAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn((long) 0);

        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        given(stackedUpdater.getSenderAccount(any())).willReturn(mutableAccount);
        given(stackedUpdater.getSenderAccount(any())).willReturn(mutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(mutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(mutableAccount);

        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
    }

    private void setupGasCalculator() {
        given(gasCalculator.getVeryLowTierGasCost()).willReturn(3L);
        given(gasCalculator.getLowTierGasCost()).willReturn(5L);
        given(gasCalculator.getMidTierGasCost()).willReturn(8L);
        given(gasCalculator.getBaseTierGasCost()).willReturn(2L);
        given(gasCalculator.getBlockHashOperationGasCost()).willReturn(20L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(160L);
        given(gasCalculator.getColdSloadCost()).willReturn(2100L);
        given(gasCalculator.getSloadOperationGasCost()).willReturn(0L);
        given(gasCalculator.getHighTierGasCost()).willReturn(10L);
        given(gasCalculator.getJumpDestOperationGasCost()).willReturn(1L);
        given(gasCalculator.getZeroTierGasCost()).willReturn(0L);
    }

    static Stream<Arguments> provideIsEstimateParameters() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }
}
