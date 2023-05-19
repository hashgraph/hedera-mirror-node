/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.ccps;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.mcps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmWorldState;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorEvmTxProcessorTest {

    private static final int MAX_STACK_SIZE = 1024;
    private final HederaEvmAccount sender = new HederaEvmAccount(Address.ALTBN128_ADD);
    private final HederaEvmAccount receiver = new HederaEvmAccount(Address.ALTBN128_MUL);
    private final Address receiverAddress = receiver.canonicalAddress();
    private final Instant consensusTime = Instant.now();

    @Mock
    private PricesAndFeesProvider pricesAndFeesProvider;

    @Mock
    private HederaEvmWorldState worldState;

    @Mock
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private Set<Operation> operations;

    @Mock
    private Transaction transaction;

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
    private StackedStateFrames<Object> stackedStateFrames;

    private MirrorEvmTxProcessor mirrorEvmTxProcessor;

    @BeforeEach
    void setup() {
        setupGasCalculator();
        var operationRegistry = new OperationRegistry();
        MainnetEVMs.registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        operations.forEach(operationRegistry::put);
        String EVM_VERSION_0_30 = "v0.30";
        when(evmProperties.evmVersion()).thenReturn(EVM_VERSION_0_30);

        mirrorEvmTxProcessor = new MirrorEvmTxProcessor(
                worldState,
                pricesAndFeesProvider,
                evmProperties,
                gasCalculator,
                mcps(gasCalculator, stackedStateFrames),
                ccps(gasCalculator),
                blockMetaSource,
                hederaEvmContractAliases,
                new AbstractCodeCache(10, hederaEvmEntityAccess));

        DefaultHederaTracer hederaEvmOperationTracer = new DefaultHederaTracer();
        mirrorEvmTxProcessor.setOperationTracer(hederaEvmOperationTracer);
    }

    @Test
    void assertSuccessExecution() {
        givenValidMockWithoutGetOrCreate();
        given(hederaEvmEntityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.EMPTY);
        given(evmProperties.fundingAccountAddress()).willReturn(Address.ALTBN128_PAIRING);
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        var result =
                mirrorEvmTxProcessor.execute(sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime, true);

        assertThat(result)
                .isNotNull()
                .returns(true, HederaEvmTransactionProcessingResult::isSuccessful)
                .returns(receiver.canonicalAddress(), r -> r.getRecipient().get());
    }

    @Test
    void missingCodeThrowsException() {
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        MessageFrame.Builder protoFrame = MessageFrame.builder()
                .messageFrameStack(new ArrayDeque<>())
                .worldUpdater(updater)
                .initialGas(1L)
                .originator(sender.canonicalAddress())
                .gasPrice(Wei.ZERO)
                .sender(sender.canonicalAddress())
                .value(Wei.ONE)
                .apparentValue(Wei.ONE)
                .blockValues(hederaBlockValues)
                .depth(1)
                .completer(frame -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup(hash -> null);

        assertThatExceptionOfType(InvalidTransactionException.class)
                .isThrownBy(
                        () -> mirrorEvmTxProcessor.buildInitialFrame(protoFrame, receiverAddress, Bytes.EMPTY, 33L));
    }

    @Test
    void assertIsContractCallFunctionality() {
        assertThat(mirrorEvmTxProcessor.getFunctionType()).isEqualTo(HederaFunctionality.ContractCall);
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        doReturn(Optional.of(receiver.canonicalAddress())).when(transaction).getTo();
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(hederaEvmEntityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.EMPTY);
        given(transaction.getSender()).willReturn(sender.canonicalAddress());
        given(transaction.getValue()).willReturn(Wei.of(1L));
        long GAS_LIMIT = 300_000L;
        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .messageFrameStack(new ArrayDeque<>())
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(mock(WorldUpdater.class))
                .initialGas(GAS_LIMIT)
                .originator(sender.canonicalAddress())
                .gasPrice(Wei.ZERO)
                .sender(sender.canonicalAddress())
                .value(Wei.of(transaction.getValue().getAsBigInteger()))
                .apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
                .blockValues(mock(BlockValues.class))
                .depth(0)
                .completer(__ -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup(h -> null);
        // when:
        MessageFrame buildMessageFrame = mirrorEvmTxProcessor.buildInitialFrame(
                commonInitialFrame, (Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

        // expect:
        assertThat(transaction)
                .isNotNull()
                .returns(buildMessageFrame.getSenderAddress(), Transaction::getSender)
                .returns(buildMessageFrame.getApparentValue(), Transaction::getValue);
    }

    private void givenValidMockWithoutGetOrCreate() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(evmProperties.fundingAccountAddress()).willReturn(Address.ALTBN128_PAIRING);

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn((long) 0);

        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
        given(evmAccount.getMutable()).willReturn(senderMutableAccount);

        given(stackedUpdater.getSenderAccount(any())).willReturn(evmAccount);
        given(stackedUpdater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(evmAccount);
        given(stackedUpdater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);

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
}
