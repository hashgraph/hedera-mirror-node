package com.hedera.mirror.web3.evm;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.EvmAccount;
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
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.web3.evm.store.models.MirrorEvmAccount;
import com.hedera.services.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.evm.contracts.execution.BlockMetaSource;
import com.hedera.services.evm.contracts.execution.EvmProperties;
import com.hedera.services.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.services.evm.store.contracts.HederaEvmWorldState;
import com.hedera.services.evm.store.contracts.HederaEvmWorldUpdater;

@ExtendWith(MockitoExtension.class)
class MirrorEvmTxProcessorTest {

    private static final int MAX_STACK_SIZE = 1024;

    @Mock private PricesAndFeesProvider pricesAndFeesProvider;
    @Mock private HederaEvmWorldState worldState;
    @Mock private HederaEvmEntityAccess hederaEvmEntityAccess;
    @Mock private EvmProperties evmProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private Set<Operation> operations;
    @Mock private Transaction transaction;
    @Mock private HederaEvmWorldState.Updater updater;
    @Mock private HederaEvmWorldUpdater stackedUpdater;
    @Mock private HederaEvmContractAliases hederaEvmContractAliases;
    @Mock private HederaBlockValues hederaBlockValues;
    @Mock private BlockMetaSource blockMetaSource;

    private final MirrorEvmAccount sender = new MirrorEvmAccount(Address.ALTBN128_ADD);
    private final MirrorEvmAccount receiver = new MirrorEvmAccount(Address.ALTBN128_MUL);
    private final Address receiverAddress = receiver.getAddress();
    private final Instant consensusTime = Instant.now();
    private MirrorEvmTxProcessor mirrorEvmTxProcessor;

    @BeforeEach
    void setup() {
        setupGasCalculator();
        var operationRegistry = new OperationRegistry();
        MainnetEVMs.registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        operations.forEach(operationRegistry::put);
        String EVM_VERSION_0_30 = "v0.30";
        when(evmProperties.evmVersion()).thenReturn(EVM_VERSION_0_30);
        var evm30 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
        String EVM_VERSION_0_32 = "v0.32";
        Map<String, Provider<MessageCallProcessor>> mcps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new MessageCallProcessor(
                                evm30, new PrecompileContractRegistry()),
                        EVM_VERSION_0_32,
                        () -> new MessageCallProcessor(
                                evm30, new PrecompileContractRegistry()));
        Map<String, Provider<ContractCreationProcessor>> ccps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new ContractCreationProcessor(
                                gasCalculator, evm30, true, List.of(), 1),
                        EVM_VERSION_0_32,
                        () -> new ContractCreationProcessor(
                                gasCalculator, evm30, true, List.of(), 1));

        mirrorEvmTxProcessor =
                new MirrorEvmTxProcessor(
                        worldState,
                        pricesAndFeesProvider,
                        evmProperties,
                        gasCalculator,
                        mcps,
                        ccps,
                        blockMetaSource,
                        hederaEvmContractAliases,
                        hederaEvmEntityAccess);

        MirrorOperationTracer hederaEvmOperationTracer = new MirrorOperationTracer();
        mirrorEvmTxProcessor.setOperationTracer(hederaEvmOperationTracer);
    }

    @Test
    void assertSuccessExecution() {
        givenValidMockWithoutGetOrCreate();
        given(evmProperties.fundingAccountAddress())
                .willReturn(Address.ALTBN128_PAIRING);
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        var result =
                mirrorEvmTxProcessor.execute(
                        sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime, true);
        assertTrue(result.isSuccessful());
        assertEquals(receiver.getAddress(), result.getRecipient().get());
    }

    @Test
    void missingCodeBecomesEmptyInInitialFrame() {
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        MessageFrame.Builder protoFrame =
                MessageFrame.builder()
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

        var messageFrame =
                mirrorEvmTxProcessor.buildInitialFrame(protoFrame, receiverAddress, Bytes.EMPTY, 33L);

        assertEquals(Code.EMPTY, messageFrame.getCode());
    }

    @Test
    void assertIsContractCallFunctionality() {
        assertEquals(HederaFunctionality.ContractCall, mirrorEvmTxProcessor.getFunctionType());
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        doReturn(Optional.of(receiver.getAddress())).when(transaction).getTo();
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(hederaEvmEntityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.EMPTY);
        given(transaction.getSender()).willReturn(sender.getAddress());
        given(transaction.getValue()).willReturn(Wei.of(1L));
        long GAS_LIMIT = 300_000L;
        final MessageFrame.Builder commonInitialFrame =
                MessageFrame.builder()
                        .messageFrameStack(mock(Deque.class))
                        .maxStackSize(MAX_STACK_SIZE)
                        .worldUpdater(mock(WorldUpdater.class))
                        .initialGas(GAS_LIMIT)
                        .originator(sender.getAddress())
                        .gasPrice(Wei.ZERO)
                        .sender(sender.getAddress())
                        .value(Wei.of(transaction.getValue().getAsBigInteger()))
                        .apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
                        .blockValues(mock(BlockValues.class))
                        .depth(0)
                        .completer(__ -> {})
                        .miningBeneficiary(Address.ZERO)
                        .blockHashLookup(h -> null);
        // when:
        MessageFrame buildMessageFrame =
                mirrorEvmTxProcessor.buildInitialFrame(
                        commonInitialFrame, (Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

        // expect:
        assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
        assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
    }

    private void givenValidMockWithoutGetOrCreate() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(evmProperties.fundingAccountAddress())
                .willReturn(Address.ALTBN128_PAIRING);

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false))
                .willReturn((long) 0);

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
