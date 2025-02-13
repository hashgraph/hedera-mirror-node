/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.mirror.web3.convert.BytesDecoder.getAbiEncodedRevertReason;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_FAILED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.NOT_STARTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.config.PrecompilesHolder;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.hedera.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.state.State;
import com.swirlds.state.spi.WritableKVState;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.CustomLog;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.util.Lists;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@CustomLog
@DisplayName("OpcodeTracer")
@ExtendWith(MockitoExtension.class)
class OpcodeTracerTest {

    private static final String CONTRACT_SERVICE = ContractService.NAME;
    private static final Address CONTRACT_ADDRESS = Address.fromHexString("0x123");
    private static final Address ETH_PRECOMPILE_ADDRESS = Address.fromHexString("0x01");
    private static final Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);
    private static final long INITIAL_GAS = 1000L;
    private static final long GAS_COST = 2L;
    private static final long GAS_PRICE = 200L;
    private static final long GAS_REQUIREMENT = 100L;
    private static final AtomicReference<Long> REMAINING_GAS = new AtomicReference<>();
    private static final AtomicReference<Integer> EXECUTED_FRAMES = new AtomicReference<>(0);
    private static final Operation OPERATION = new AbstractOperation(0x02, "MUL", 2, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
            return new OperationResult(GAS_COST, null);
        }
    };
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock
    private WorldUpdater worldUpdater;

    @Mock
    private MutableAccount recipientAccount;

    @Mock
    private PrecompilesHolder precompilesHolder;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private State mirrorNodeState;

    // Transient test data
    private OpcodeTracer tracer;
    private OpcodeTracerOptions tracerOptions;
    private MessageFrame frame;

    // EVM data for capture
    private UInt256[] stackItems;
    private Bytes[] wordsInMemory;
    private Map<UInt256, UInt256> updatedStorage;

    static Stream<Arguments> allMessageFrameTypesAndStates() {
        return Stream.of(MessageFrame.Type.values())
                .flatMap(type -> Stream.of(MessageFrame.State.values()).map(state -> Arguments.of(type, state)));
    }

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setUp() {
        when(precompilesHolder.getHederaPrecompiles())
                .thenReturn(Map.of(
                        HTS_PRECOMPILE_ADDRESS.toString(),
                        mock(PrecompiledContract.class),
                        PRNG_PRECOMPILE_ADDRESS,
                        mock(PrecompiledContract.class),
                        EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS,
                        mock(PrecompiledContract.class)));
        REMAINING_GAS.set(INITIAL_GAS);
        tracer = new OpcodeTracer(precompilesHolder, mirrorNodeEvmProperties, mirrorNodeState);
        tracerOptions = new OpcodeTracerOptions(false, false, false);
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @AfterEach
    void tearDown() {
        verifyMocks();
        reset(contractCallContext);
        reset(worldUpdater);
        reset(recipientAccount);
    }

    private void verifyMocks() {
        if (tracerOptions.isStorage()) {
            verify(worldUpdater, atLeastOnce()).getAccount(frame.getRecipientAddress());

            try {
                MutableAccount account = worldUpdater.getAccount(frame.getRecipientAddress());
                if (account != null) {
                    assertThat(account).isEqualTo(recipientAccount);
                    if (!mirrorNodeEvmProperties.isModularizedServices()) {
                        verify(recipientAccount, times(1)).getUpdatedStorage();
                    }
                }
            } catch (final ModificationNotAllowedException e) {
                if (!mirrorNodeEvmProperties.isModularizedServices()) {
                    verify(recipientAccount, never()).getUpdatedStorage();
                }
            }
        } else {
            verify(worldUpdater, never()).getAccount(any());
            if (!mirrorNodeEvmProperties.isModularizedServices()) {
                verify(recipientAccount, never()).getUpdatedStorage();
            }
        }
    }

    @Test
    @DisplayName("should increment contract action index on init()")
    void shouldIncrementContractActionIndexOnInit() {
        frame = setupInitialFrame(tracerOptions);
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.init(frame);

        verify(contractCallContext, times(1)).incrementContractActionsCounter();
        assertThat(contractCallContext.getContractActionIndexOfCurrentFrame()).isZero();
    }

    @ParameterizedTest
    @MethodSource("allMessageFrameTypesAndStates")
    @DisplayName("should increment contract action index on tracePostExecution() for SUSPENDED frame")
    void shouldIncrementContractActionIndexOnTraceContextReEnter(
            final MessageFrame.Type type, final MessageFrame.State state) {
        frame = setupInitialFrame(tracerOptions, type);
        frame.setState(state);
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.tracePostExecution(frame, OPERATION.execute(frame, null));

        if (state == CODE_SUSPENDED) {
            verify(contractCallContext, times(1)).incrementContractActionsCounter();
            assertThat(contractCallContext.getContractActionIndexOfCurrentFrame())
                    .isZero();
        } else {
            verify(contractCallContext, never()).incrementContractActionsCounter();
            assertThat(contractCallContext.getContractActionIndexOfCurrentFrame())
                    .isEqualTo(-1);
        }
    }

    @ParameterizedTest
    @MethodSource("allMessageFrameTypesAndStates")
    @DisplayName("should increment contract action index for synthetic actions on traceAccountCreationResult()")
    void shouldIncrementContractActionIndexForSyntheticActionsOnAccountCreationResult(
            final MessageFrame.Type type, final MessageFrame.State state) {
        frame = setupInitialFrame(tracerOptions, type);
        frame.setState(state);
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        if (type == MESSAGE_CALL && (state == EXCEPTIONAL_HALT || state == COMPLETED_FAILED)) {
            frame.setExceptionalHaltReason(Optional.of(INVALID_SOLIDITY_ADDRESS));

            tracer.traceAccountCreationResult(frame, frame.getExceptionalHaltReason());

            verify(contractCallContext, times(1)).incrementContractActionsCounter();
            assertThat(contractCallContext.getContractActionIndexOfCurrentFrame())
                    .isZero();
        } else {
            tracer.traceAccountCreationResult(frame, frame.getExceptionalHaltReason());

            verify(contractCallContext, never()).incrementContractActionsCounter();
            assertThat(contractCallContext.getContractActionIndexOfCurrentFrame())
                    .isEqualTo(-1);
        }
    }

    @ParameterizedTest
    @MethodSource("allMessageFrameTypesAndStates")
    @DisplayName("should not increment contract action index when halt reason is empty on traceAccountCreationResult()")
    void shouldNotIncrementContractActionIndexForEmptyHaltReasonOnTraceAccountCreationResult(
            final MessageFrame.Type type, final MessageFrame.State state) {
        frame = setupInitialFrame(tracerOptions, type);
        frame.setState(state);
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.traceAccountCreationResult(frame, Optional.empty());

        verify(contractCallContext, never()).incrementContractActionsCounter();
        assertThat(contractCallContext.getContractActionIndexOfCurrentFrame()).isEqualTo(-1);
    }

    @ParameterizedTest
    @MethodSource("allMessageFrameTypesAndStates")
    @DisplayName("should not increment contract action index when halt reason is not INVALID_SOLIDITY_ADDRESS "
            + "on traceAccountCreationResult()")
    void shouldNotIncrementContractActionIndexForHaltReasonNotOfSyntheticActionOnTraceAccountCreationResult(
            final MessageFrame.Type type, final MessageFrame.State state) {
        frame = setupInitialFrame(tracerOptions, type);
        frame.setState(state);
        frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.traceAccountCreationResult(frame, Optional.of(INSUFFICIENT_GAS));

        verify(contractCallContext, never()).incrementContractActionsCounter();
        assertThat(contractCallContext.getContractActionIndexOfCurrentFrame()).isEqualTo(-1);
    }

    @ParameterizedTest
    @MethodSource("allMessageFrameTypesAndStates")
    @DisplayName("should increment contract action index for synthetic actions on tracePrecompileResult()")
    void shouldIncrementContractActionIndexForSyntheticActionsOnTracePrecompileResult(
            final MessageFrame.Type type, final MessageFrame.State state) {
        frame = setupInitialFrame(tracerOptions, type);
        frame.setState(state);
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        if (type == MESSAGE_CALL && (state == EXCEPTIONAL_HALT || state == COMPLETED_FAILED)) {
            frame.setExceptionalHaltReason(Optional.of(INVALID_SOLIDITY_ADDRESS));

            tracer.tracePrecompileResult(frame, ContractActionType.SYSTEM);

            verify(contractCallContext, times(1)).incrementContractActionsCounter();
            assertThat(contractCallContext.getContractActionIndexOfCurrentFrame())
                    .isZero();
        } else {
            tracer.tracePrecompileResult(frame, ContractActionType.SYSTEM);

            verify(contractCallContext, never()).incrementContractActionsCounter();
            assertThat(contractCallContext.getContractActionIndexOfCurrentFrame())
                    .isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("should not increment contract action index for halted precompile frames on tracePrecompileCall()")
    void shouldNotIncrementContractActionIndexForHaltedPrecompileFrameOnTracePrecompileResult() {
        frame = setupInitialFrame(tracerOptions, ETH_PRECOMPILE_ADDRESS, MESSAGE_CALL);
        frame.setState(EXCEPTIONAL_HALT);
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.tracePrecompileResult(frame, ContractActionType.PRECOMPILE);

        verify(contractCallContext, never()).incrementContractActionsCounter();
        assertThat(contractCallContext.getContractActionIndexOfCurrentFrame()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should not increment contract action index when halt reason is empty on tracePrecompileCall()")
    void shouldNotIncrementContractActionIndexForEmptyHaltReasonOnTracePrecompileResult() {
        frame = setupInitialFrame(tracerOptions, ETH_PRECOMPILE_ADDRESS, MESSAGE_CALL);
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.empty());
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.tracePrecompileResult(frame, ContractActionType.SYSTEM);

        verify(contractCallContext, never()).incrementContractActionsCounter();
        assertThat(contractCallContext.getContractActionIndexOfCurrentFrame()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should not increment contract action index when halt reason is not INVALID_SOLIDITY_ADDRESS "
            + "on tracePrecompileCall()")
    void shouldNotIncrementContractActionIndexForHaltReasonNotOfSynthenticActionOnTracePrecompileResult() {
        frame = setupInitialFrame(tracerOptions, ETH_PRECOMPILE_ADDRESS, MESSAGE_CALL);
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);

        tracer.tracePrecompileResult(frame, ContractActionType.SYSTEM);

        verify(contractCallContext, never()).incrementContractActionsCounter();
        assertThat(contractCallContext.getContractActionIndexOfCurrentFrame()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should record program counter")
    void shouldRecordProgramCounter() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.pc()).isEqualTo(frame.getPC());
    }

    @Test
    @DisplayName("should record opcode")
    void shouldRecordOpcode() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.op()).isNotEmpty();
        assertThat(opcode.op()).contains(OPERATION.getName());
    }

    @Test
    @DisplayName("should record depth")
    void shouldRecordDepth() {
        frame = setupInitialFrame(tracerOptions);

        // simulate 4 calls
        final int expectedDepth = 4;
        for (int i = 0; i < expectedDepth; i++) {
            frame.getMessageFrameStack()
                    .add(buildMessageFrame(Address.fromHexString("0x10%d".formatted(i)), MESSAGE_CALL));
        }

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.depth()).isEqualTo(expectedDepth);
    }

    @Test
    @DisplayName("should record remaining gas")
    void shouldRecordRemainingGas() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.gas()).isEqualTo(REMAINING_GAS.get());
    }

    @Test
    @DisplayName("should record gas cost")
    void shouldRecordGasCost() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.gasCost()).isEqualTo(GAS_COST);
    }

    @Test
    @DisplayName("given stack is enabled in tracer options, should record stack")
    void shouldRecordStackWhenEnabled() {
        tracerOptions = tracerOptions.toBuilder().stack(true).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.stack()).isNotEmpty();
        assertThat(opcode.stack()).containsExactly(stackItems);
    }

    @Test
    @DisplayName("given stack is disabled in tracer options, should not record stack")
    void shouldNotRecordStackWhenDisabled() {
        tracerOptions = tracerOptions.toBuilder().stack(false).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.stack()).isEmpty();
    }

    @Test
    @DisplayName("given memory is enabled in tracer options, should record memory")
    void shouldRecordMemoryWhenEnabled() {
        tracerOptions = tracerOptions.toBuilder().memory(true).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.memory()).isNotEmpty();
        assertThat(opcode.memory()).containsExactly(wordsInMemory);
    }

    @Test
    @DisplayName("given memory is disabled in tracer options, should not record memory")
    void shouldNotRecordMemoryWhenDisabled() {
        tracerOptions = tracerOptions.toBuilder().memory(false).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.memory()).isEmpty();
    }

    @Test
    @DisplayName("given storage is enabled in tracer options, should record storage")
    void shouldRecordStorageWhenEnabled() {
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).isNotEmpty();
        assertThat(opcode.storage()).containsAllEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("given storage is enabled in tracer options, should record storage for modularized services")
    void shouldRecordStorageWhenEnabledModularized() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        when(mirrorNodeEvmProperties.isModularizedServices()).thenReturn(true);
        frame = setupInitialFrame(tracerOptions);

        // Mock writable states
        MapWritableStates mockStates = mock(MapWritableStates.class);
        when(mirrorNodeState.getWritableStates(CONTRACT_SERVICE)).thenReturn(mockStates);
        WritableKVState<SlotKey, SlotValue> mockStorageState = mock(WritableKVState.class);
        doReturn(mockStorageState).when(mockStates).get(ContractStorageReadableKVState.KEY);

        // Mock SlotKey and SlotValue
        SlotKey slotKey = createMockSlotKey();
        SlotValue slotValue = createMockSlotValue(UInt256.valueOf(233));

        // Mock modified keys retrieval
        when(mockStorageState.modifiedKeys()).thenReturn(Set.of(slotKey));
        when(mockStorageState.get(slotKey)).thenReturn(slotValue);

        // Expected storage map
        final Map<UInt256, UInt256> expectedStorage = ImmutableSortedMap.of(UInt256.ZERO, UInt256.valueOf(233));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isNotEmpty().containsAllEntriesOf(expectedStorage);
    }

    @Test
    @DisplayName(
            "given storage is enabled in tracer options, should return empty storage when there are no updates for modularized services")
    void shouldReturnEmptyStorageWhenThereAreNoUpdates() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        when(mirrorNodeEvmProperties.isModularizedServices()).thenReturn(true);
        frame = setupInitialFrame(tracerOptions);

        // Mock writable states
        MapWritableStates mockStates = mock(MapWritableStates.class);
        when(mirrorNodeState.getWritableStates(CONTRACT_SERVICE)).thenReturn(mockStates);
        WritableKVState<SlotKey, SlotValue> mockStorageState = mock(WritableKVState.class);
        doReturn(mockStorageState).when(mockStates).get(ContractStorageReadableKVState.KEY);

        // Mock empty modified keys retrieval
        when(mockStorageState.modifiedKeys()).thenReturn(Collections.emptySet());

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName(
            "given storage is enabled in tracer options, should skip slotKey when contract address does not match for modularized services")
    void shouldSkipSlotKeyWhenContractAddressDoesNotMatch() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        when(mirrorNodeEvmProperties.isModularizedServices()).thenReturn(true);
        frame = setupInitialFrame(tracerOptions);

        MapWritableStates mockStates = mock(MapWritableStates.class);
        when(mirrorNodeState.getWritableStates(CONTRACT_SERVICE)).thenReturn(mockStates);
        WritableKVState<SlotKey, SlotValue> mockStorageState = mock(WritableKVState.class);
        doReturn(mockStorageState).when(mockStates).get(ContractStorageReadableKVState.KEY);

        SlotKey mismatchedSlotKey = createMockSlotKey(Address.fromHexString("0xDEADBEEF"));
        when(mockStorageState.modifiedKeys()).thenReturn(Set.of(mismatchedSlotKey));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName(
            "given storage is enabled in tracer options, should skip slotKey when slotValue is null for modularized services")
    void shouldSkipSlotKeyWhenSlotValueIsNull() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        when(mirrorNodeEvmProperties.isModularizedServices()).thenReturn(true);
        frame = setupInitialFrame(tracerOptions);

        MapWritableStates mockStates = mock(MapWritableStates.class);
        when(mirrorNodeState.getWritableStates(CONTRACT_SERVICE)).thenReturn(mockStates);
        WritableKVState<SlotKey, SlotValue> mockStorageState = mock(WritableKVState.class);
        doReturn(mockStorageState).when(mockStates).get(ContractStorageReadableKVState.KEY);

        SlotKey slotKey = createMockSlotKey(CONTRACT_ADDRESS);

        when(mockStorageState.modifiedKeys()).thenReturn(Set.of(slotKey));
        when(mockStorageState.get(slotKey)).thenReturn(null); // SlotValue is null

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName(
            "given storage is enabled in tracer options, should return empty storage when STORAGE_KEY retrieval fails for modularized services")
    void shouldReturnEmptyStorageWhenStorageKeyRetrievalFails() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        when(mirrorNodeEvmProperties.isModularizedServices()).thenReturn(true);
        frame = setupInitialFrame(tracerOptions);

        MapWritableStates mockStates = mock(MapWritableStates.class);
        when(mirrorNodeState.getWritableStates(CONTRACT_SERVICE)).thenReturn(mockStates);

        // Mock storage retrieval to throw IllegalArgumentException
        when(mockStates.get(ContractStorageReadableKVState.KEY))
                .thenThrow(new IllegalArgumentException("Storage retrieval failed"));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isEmpty(); // Ensures empty storage response instead of exception
    }

    @Test
    @DisplayName("given account is missing in the world updater, should only log a warning and return empty storage")
    void shouldNotThrowExceptionWhenAccountIsMissingInWorldUpdater() {
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupInitialFrame(tracerOptions);

        when(worldUpdater.getAccount(any())).thenReturn(null);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).containsExactlyEntriesOf(new TreeMap<>());
    }

    @Test
    @DisplayName("given ModificationNotAllowedException thrown when trying to retrieve account through WorldUpdater, "
            + "should only log a warning and return empty storage")
    void shouldNotThrowExceptionWhenWorldUpdaterThrowsModificationNotAllowedException() {
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupInitialFrame(tracerOptions);

        when(worldUpdater.getAccount(any())).thenThrow(new ModificationNotAllowedException());

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).containsExactlyEntriesOf(new TreeMap<>());
    }

    @Test
    @DisplayName("given storage is disabled in tracer options, should not record storage")
    void shouldNotRecordStorageWhenDisabled() {
        tracerOptions = tracerOptions.toBuilder().storage(false).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName("given exceptional halt occurs, should capture frame data and halt reason")
    void shouldCaptureFrameWhenExceptionalHaltOccurs() {
        tracerOptions =
                tracerOptions.toBuilder().stack(true).memory(true).storage(true).build();
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame, INSUFFICIENT_GAS);
        assertThat(opcode.reason())
                .contains(Hex.encodeHexString(INSUFFICIENT_GAS.getDescription().getBytes()));
        assertThat(opcode.stack()).contains(stackItems);
        assertThat(opcode.memory()).contains(wordsInMemory);
        assertThat(opcode.storage()).containsExactlyEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("should capture a precompile call")
    void shouldCaptureFrameWhenSuccessfulPrecompileCallOccurs() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executePrecompileOperation(frame, Bytes.fromHexString("0x01"));
        assertThat(opcode.pc()).isEqualTo(frame.getPC());
        assertThat(opcode.op()).isNotEmpty().isEqualTo(OPERATION.getName());
        assertThat(opcode.gas()).isEqualTo(REMAINING_GAS.get());
        assertThat(opcode.gasCost()).isEqualTo(GAS_REQUIREMENT);
        assertThat(opcode.depth()).isEqualTo(frame.getDepth());
        assertThat(opcode.stack()).isEmpty();
        assertThat(opcode.memory()).isEmpty();
        assertThat(opcode.storage()).isEmpty();
        assertThat(opcode.reason())
                .isEqualTo(frame.getRevertReason().map(Bytes::toString).orElse(null));
    }

    @Test
    @DisplayName("should not record gas requirement of precompile call with null output")
    void shouldNotRecordGasRequirementWhenPrecompileCallHasNullOutput() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executePrecompileOperation(frame, null);
        assertThat(opcode.gasCost()).isZero();
    }

    @Test
    @DisplayName("should not record revert reason of precompile call with no revert reason")
    void shouldNotRecordRevertReasonWhenPrecompileCallHasNoRevertReason() {
        frame = setupInitialFrame(tracerOptions);

        final Opcode opcode = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcode.reason()).isNull();
    }

    @Test
    @DisplayName("should record revert reason of precompile call when frame has revert reason")
    void shouldRecordRevertReasonWhenEthPrecompileCallHasRevertReason() {
        frame = setupInitialFrame(tracerOptions, ETH_PRECOMPILE_ADDRESS, MESSAGE_CALL);
        frame.setRevertReason(Bytes.of("revert reason".getBytes()));

        final Opcode opcode = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcode.reason())
                .isNotEmpty()
                .isEqualTo(frame.getRevertReason().map(Bytes::toString).orElseThrow());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with plaintext revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithPlaintextRevertReason() {
        final var contractActionNoRevert =
                contractAction(0, 0, CallOperationType.OP_CREATE, OUTPUT.getNumber(), CONTRACT_ADDRESS);
        final var contractActionWithRevert =
                contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
        contractActionWithRevert.setResultData("revert reason".getBytes());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcodeForPrecompileCall.reason())
                .isNotEmpty()
                .isEqualTo(getAbiEncodedRevertReason(Bytes.of(contractActionWithRevert.getResultData()))
                        .toHexString());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with response code for revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithResponseCodeNumberRevertReason() {
        final var contractActionNoRevert =
                contractAction(0, 0, CallOperationType.OP_CREATE, OUTPUT.getNumber(), CONTRACT_ADDRESS);
        final var contractActionWithRevert =
                contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
        contractActionWithRevert.setResultData(ByteBuffer.allocate(32)
                .putInt(28, ResponseCodeEnum.INVALID_ACCOUNT_ID.getNumber())
                .array());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcodeForPrecompileCall.reason())
                .isNotEmpty()
                .isEqualTo(getAbiEncodedRevertReason(Bytes.of(
                                ResponseCodeEnum.INVALID_ACCOUNT_ID.name().getBytes()))
                        .toHexString());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with ABI-encoded revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithAbiEncodedRevertReason() {
        final var contractActionNoRevert =
                contractAction(0, 0, CallOperationType.OP_CREATE, OUTPUT.getNumber(), CONTRACT_ADDRESS);
        final var contractActionWithRevert =
                contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
        contractActionWithRevert.setResultData(
                getAbiEncodedRevertReason(Bytes.of(INVALID_OPERATION.name().getBytes()))
                        .toArray());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcodeForPrecompileCall.reason())
                .isNotEmpty()
                .isEqualTo(Bytes.of(contractActionWithRevert.getResultData()).toHexString());
    }

    @Test
    @DisplayName("should return empty revert reason of precompile call with empty revert reason")
    void shouldReturnEmptyReasonWhenPrecompileCallHasContractActionWithEmptyRevertReason() {
        final var contractActionNoRevert =
                contractAction(0, 0, CallOperationType.OP_CREATE, OUTPUT.getNumber(), CONTRACT_ADDRESS);
        final var contractActionWithRevert =
                contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
        contractActionWithRevert.setResultData(Bytes.EMPTY.toArray());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcodeForPrecompileCall.reason()).isNotNull().isEqualTo(Bytes.EMPTY.toHexString());
    }

    private Opcode executeOperation(final MessageFrame frame) {
        return executeOperation(frame, null);
    }

    private Opcode executeOperation(final MessageFrame frame, final ExceptionalHaltReason haltReason) {
        tracer.init(frame);
        if (frame.getState() == NOT_STARTED) {
            tracer.traceContextEnter(frame);
        } else {
            tracer.traceContextReEnter(frame);
        }

        final OperationResult operationResult;
        if (haltReason != null) {
            frame.setState(EXCEPTIONAL_HALT);
            frame.setRevertReason(Bytes.of(haltReason.getDescription().getBytes()));
            operationResult = new OperationResult(GAS_COST, haltReason);
        } else {
            operationResult = OPERATION.execute(frame, null);
        }

        tracer.tracePostExecution(frame, operationResult);
        if (frame.getState() == COMPLETED_SUCCESS || frame.getState() == COMPLETED_FAILED) {
            tracer.traceContextExit(frame);
        }
        tracer.finalizeOperation(frame);

        EXECUTED_FRAMES.set(EXECUTED_FRAMES.get() + 1);
        Opcode expectedOpcode = contractCallContext.getOpcodes().get(EXECUTED_FRAMES.get() - 1);

        verify(contractCallContext, times(1)).addOpcodes(expectedOpcode);
        assertThat(tracer.getContext().getOpcodeTracerOptions()).isEqualTo(tracerOptions);
        assertThat(contractCallContext.getOpcodes()).hasSize(1);
        assertThat(contractCallContext.getContractActions()).isNotNull();
        return expectedOpcode;
    }

    private Opcode executePrecompileOperation(final MessageFrame frame, final Bytes output) {
        tracer.init(frame);
        if (frame.getState() == NOT_STARTED) {
            tracer.traceContextEnter(frame);
        } else {
            tracer.traceContextReEnter(frame);
        }
        tracer.tracePrecompileCall(frame, GAS_REQUIREMENT, output);
        if (frame.getState() == COMPLETED_SUCCESS || frame.getState() == COMPLETED_FAILED) {
            tracer.traceContextExit(frame);
        }
        tracer.finalizeOperation(frame);

        EXECUTED_FRAMES.set(EXECUTED_FRAMES.get() + 1);
        Opcode expectedOpcode = contractCallContext.getOpcodes().get(EXECUTED_FRAMES.get() - 1);

        verify(contractCallContext, times(1)).addOpcodes(expectedOpcode);
        assertThat(tracer.getContext().getOpcodeTracerOptions()).isEqualTo(tracerOptions);
        assertThat(contractCallContext.getOpcodes()).hasSize(EXECUTED_FRAMES.get());
        assertThat(contractCallContext.getContractActions()).isNotNull();
        return expectedOpcode;
    }

    private MessageFrame setupInitialFrame(final OpcodeTracerOptions options) {
        return setupInitialFrame(options, CONTRACT_ADDRESS, MESSAGE_CALL);
    }

    private MessageFrame setupInitialFrame(final OpcodeTracerOptions options, final MessageFrame.Type type) {
        return setupInitialFrame(options, CONTRACT_ADDRESS, type);
    }

    private MessageFrame setupInitialFrame(
            final OpcodeTracerOptions options,
            final Address recipientAddress,
            final MessageFrame.Type type,
            final ContractAction... contractActions) {
        contractCallContext.setOpcodeTracerOptions(options);
        contractCallContext.setContractActions(Lists.newArrayList(contractActions));
        contractCallContext.setContractActionIndexOfCurrentFrame(-1);
        EXECUTED_FRAMES.set(0);

        final MessageFrame messageFrame = buildMessageFrame(recipientAddress, type);
        messageFrame.setState(NOT_STARTED);
        return setupFrame(messageFrame);
    }

    private MessageFrame setupFrame(final MessageFrame messageFrame) {
        reset(contractCallContext);
        stackItems = setupStackForCapture(messageFrame);
        wordsInMemory = setupMemoryForCapture(messageFrame);
        updatedStorage = setupStorageForCapture(messageFrame);
        return messageFrame;
    }

    private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame) {
        final Map<UInt256, UInt256> storage = ImmutableSortedMap.of(
                UInt256.ZERO, UInt256.valueOf(233),
                UInt256.ONE, UInt256.valueOf(2424));

        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            when(recipientAccount.getUpdatedStorage()).thenReturn(storage);
        }
        when(worldUpdater.getAccount(frame.getRecipientAddress())).thenReturn(recipientAccount);

        return storage;
    }

    private UInt256[] setupStackForCapture(final MessageFrame frame) {
        final UInt256[] stack = new UInt256[] {
            UInt256.fromHexString("0x01"), UInt256.fromHexString("0x02"), UInt256.fromHexString("0x03")
        };

        for (final UInt256 stackItem : stack) {
            frame.pushStackItem(stackItem);
        }

        return stack;
    }

    private Bytes[] setupMemoryForCapture(final MessageFrame frame) {
        final Bytes[] words = new Bytes[] {
            Bytes.fromHexString("0x01", 32), Bytes.fromHexString("0x02", 32), Bytes.fromHexString("0x03", 32)
        };

        for (int i = 0; i < words.length; i++) {
            frame.writeMemory(i * 32, 32, words[i]);
        }

        return words;
    }

    private MessageFrame buildMessageFrameFromAction(ContractAction action) {
        final var recipientAddress = Address.wrap(Bytes.of(action.getRecipientAddress()));
        final var senderAddress = toAddress(action.getCaller());
        final var value = Wei.of(action.getValue());
        final var type = action.getCallType() == ContractActionType.CREATE_VALUE ? CONTRACT_CREATION : MESSAGE_CALL;
        final var messageFrame = messageFrameBuilder(recipientAddress, type)
                .sender(senderAddress)
                .originator(senderAddress)
                .address(recipientAddress)
                .contract(recipientAddress)
                .inputData(Bytes.of(action.getInput()))
                .initialGas(REMAINING_GAS.get())
                .value(value)
                .apparentValue(value)
                .build();
        messageFrame.setState(NOT_STARTED);
        return messageFrame;
    }

    private MessageFrame buildMessageFrame(final Address recipientAddress, final MessageFrame.Type type) {
        REMAINING_GAS.set(REMAINING_GAS.get() - GAS_COST);

        final MessageFrame messageFrame =
                messageFrameBuilder(recipientAddress, type).build();
        messageFrame.setCurrentOperation(OPERATION);
        messageFrame.setPC(0);
        messageFrame.setGasRemaining(REMAINING_GAS.get());
        return messageFrame;
    }

    private MessageFrame.Builder messageFrameBuilder(final Address recipientAddress, final MessageFrame.Type type) {
        return new MessageFrame.Builder()
                .type(type)
                .code(CodeV0.EMPTY_CODE)
                .sender(Address.ZERO)
                .originator(Address.ZERO)
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .address(recipientAddress)
                .contract(recipientAddress)
                .inputData(Bytes.EMPTY)
                .initialGas(INITIAL_GAS)
                .value(Wei.ZERO)
                .apparentValue(Wei.ZERO)
                .worldUpdater(worldUpdater)
                .gasPrice(Wei.of(GAS_PRICE))
                .blockValues(mock(BlockValues.class))
                .blockHashLookup(ignored -> Hash.wrap(Bytes32.ZERO))
                .contextVariables(Map.of(ContractCallContext.CONTEXT_NAME, contractCallContext));
    }

    private ContractAction contractAction(
            final int index,
            final int depth,
            final CallOperationType callOperationType,
            final int resultDataType,
            final Address recipientAddress) {
        return ContractAction.builder()
                .callDepth(depth)
                .caller(EntityId.of("0.0.1"))
                .callerType(EntityType.ACCOUNT)
                .callOperationType(callOperationType.getNumber())
                .callType(ContractActionType.PRECOMPILE.getNumber())
                .consensusTimestamp(new SecureRandom().nextLong())
                .gas(REMAINING_GAS.get())
                .gasUsed(GAS_PRICE)
                .index(index)
                .input(new byte[0])
                .payerAccountId(EntityId.of("0.0.2"))
                .recipientAccount(EntityId.of("0.0.3"))
                .recipientAddress(recipientAddress.toArray())
                .recipientContract(EntityId.of("0.0.4"))
                .resultData(resultDataType == REVERT_REASON.getNumber() ? "revert reason".getBytes() : new byte[0])
                .resultDataType(resultDataType)
                .value(1L)
                .build();
    }

    /**
     * Helper method to create a mocked SlotKey with a specified contract address. Uses lenient stubbing to prevent
     * UnnecessaryStubbingException in certain tests.
     */
    private SlotKey createMockSlotKey(Address contractAddress) {
        SlotKey slotKey = mock(SlotKey.class);

        ContractID testContractId = com.hedera.hapi.node.base.ContractID.newBuilder()
                .contractNum(EntityIdUtils.numFromEvmAddress(contractAddress.toArray()))
                .build();

        lenient().when(slotKey.contractID()).thenReturn(testContractId);
        lenient().when(slotKey.key()).thenReturn(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(UInt256.ZERO.toArray()));

        return slotKey;
    }

    /**
     * Overloaded method to create a SlotKey with the default contract address.
     */
    private SlotKey createMockSlotKey() {
        return createMockSlotKey(OpcodeTracerTest.CONTRACT_ADDRESS);
    }

    /**
     * Helper method to create a mocked SlotValue.
     */
    private SlotValue createMockSlotValue(UInt256 value) {
        SlotValue slotValue = mock(SlotValue.class);
        when(slotValue.value()).thenReturn(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(value.toArray()));
        return slotValue;
    }
}
