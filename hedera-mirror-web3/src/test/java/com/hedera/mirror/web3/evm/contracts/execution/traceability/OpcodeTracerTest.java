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

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractActionType;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("OpcodeTracer")
@ExtendWith(MockitoExtension.class)
class OpcodeTracerTest {

    private static final long INITIAL_GAS = 1000L;
    private static final long GAS_COST = 2L;
    private static final long GAS_PRICE = 200L;
    private static final long GAS_REQUIREMENT = 100L;
    private static final AtomicReference<Long> REMAINING_GAS = new AtomicReference<>();
    private static final Operation OPERATION = new AbstractOperation(0x02, "MUL", 2, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
            return new OperationResult(GAS_COST, null);
        }
    };

    @Mock
    private ContractCallContext contractCallContext;

    @Mock
    private WorldUpdater worldUpdater;

    @Mock
    private MutableAccount recipientAccount;

    // Transient test data
    private OpcodeTracerOptions tracerOptions;
    private MessageFrame frame;

    // EVM data for capture
    private UInt256[] stackItems;
    private Bytes[] wordsInMemory;
    private Map<UInt256, UInt256> updatedStorage;

    @BeforeEach
    void setUp() {
        REMAINING_GAS.set(INITIAL_GAS);
        tracerOptions = new OpcodeTracerOptions(false, false, false);
    }

    @AfterEach
    void tearDown() {
        verifyMocks();
        reset(contractCallContext);
        reset(worldUpdater);
        reset(recipientAccount);
    }

    private void verifyMocks() {
        verify(contractCallContext, times(1)).getOpcodeTracerOptions();
        verify(contractCallContext, times(1)).getContractActions();

        if (tracerOptions.isStorage()) {
            verify(worldUpdater, times(1)).getAccount(frame.getRecipientAddress());

            MutableAccount account;
            try {
                account = worldUpdater.getAccount(frame.getRecipientAddress());
            } catch (Exception e) {
                account = null;
            }

            if (account != null) {
                assertThat(account).isEqualTo(recipientAccount);
                verify(recipientAccount, times(1)).getUpdatedStorage();
            }
        } else {
            verify(worldUpdater, never()).getAccount(any());
            verify(recipientAccount, never()).getUpdatedStorage();
        }
    }

    @Test
    @DisplayName("should record program counter")
    void shouldRecordProgramCounter() {
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.pc()).isEqualTo(frame.getPC());
    }

    @Test
    @DisplayName("should record opcode")
    void shouldRecordOpcode() {
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.op()).isNotEmpty();
        assertThat(opcode.op()).contains(OPERATION.getName());
    }

    @Test
    @DisplayName("should record depth")
    void shouldRecordDepth() {
        frame = setupMessageFrame(tracerOptions);

        // simulate 4 calls
        final int expectedDepth = 4;
        for (int i = 0; i < expectedDepth; i++) {
            frame.getMessageFrameStack().add(validMessageFrame());
        }

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.depth()).isEqualTo(expectedDepth);
    }

    @Test
    @DisplayName("should record remaining gas")
    void shouldRecordRemainingGas() {
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.gas()).isEqualTo(REMAINING_GAS.get());
    }

    @Test
    @DisplayName("should record gas cost")
    void shouldRecordGasCost() {
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.gasCost()).isEqualTo(GAS_COST);
    }

    @Test
    @DisplayName("given stack is enabled in tracer options, should record stack")
    void shouldRecordStackWhenEnabled() {
        tracerOptions = tracerOptions.toBuilder().stack(true).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.stack()).isNotEmpty();
        assertThat(opcode.stack()).containsExactly(stackItems);
    }

    @Test
    @DisplayName("given stack is disabled in tracer options, should not record stack")
    void shouldNotRecordStackWhenDisabled() {
        tracerOptions = tracerOptions.toBuilder().stack(false).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.stack()).isEmpty();
    }

    @Test
    @DisplayName("given memory is enabled in tracer options, should record memory")
    void shouldRecordMemoryWhenEnabled() {
        tracerOptions = tracerOptions.toBuilder().memory(true).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.memory()).isNotEmpty();
        assertThat(opcode.memory()).containsExactly(wordsInMemory);
    }

    @Test
    @DisplayName("given memory is disabled in tracer options, should not record memory")
    void shouldNotRecordMemoryWhenDisabled() {
        tracerOptions = tracerOptions.toBuilder().memory(false).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.memory()).isEmpty();
    }

    @Test
    @DisplayName("given storage is enabled in tracer options, should record storage")
    void shouldRecordStorageWhenEnabled() {
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).isNotEmpty();
        assertThat(opcode.storage()).containsAllEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("given account is missing in the world updater, should only log a warning and return empty storage")
    void shouldNotThrowExceptionWhenAccountIsMissingInWorldUpdater() {
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupMessageFrame(tracerOptions);

        when(worldUpdater.getAccount(any())).thenReturn(null);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).containsExactlyEntriesOf(new TreeMap<>());
    }

    @Test
    @DisplayName("given ModificationNotAllowedException thrown when trying to retrieve account through WorldUpdater, "
            + "should only log a warning and return empty storage")
    void shouldNotThrowExceptionWhenWorldUpdaterThrowsModificationNotAllowedException() {
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupMessageFrame(tracerOptions);

        when(worldUpdater.getAccount(any())).thenThrow(new ModificationNotAllowedException());

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).containsExactlyEntriesOf(new TreeMap<>());
    }

    @Test
    @DisplayName("given storage is disabled in tracer options, should not record storage")
    void shouldNotRecordStorageWhenDisabled() {
        tracerOptions = tracerOptions.toBuilder().storage(false).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName("given exceptional halt occurs, should capture frame data and halt reason")
    void shouldCaptureFrameWhenExceptionalHaltOccurs() {
        tracerOptions =
                tracerOptions.toBuilder().stack(true).memory(true).storage(true).build();
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executeOperation(frame, ExceptionalHaltReason.INSUFFICIENT_GAS);
        assertThat(opcode.reason())
                .contains(Hex.encodeHexString(
                        ExceptionalHaltReason.INSUFFICIENT_GAS.getDescription().getBytes()));
        assertThat(opcode.stack()).contains(stackItems);
        assertThat(opcode.memory()).contains(wordsInMemory);
        assertThat(opcode.storage()).containsExactlyEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("should capture a precompile call")
    void shouldCaptureFrameWhenSuccessfulPrecompileCallOccurs() {
        frame = setupMessageFrame(tracerOptions);

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
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executePrecompileOperation(frame, null);
        assertThat(opcode.gasCost()).isZero();
    }

    @Test
    @DisplayName("should not record revert reason of precompile call with no revert reason")
    void shouldNotRecordRevertReasonWhenPrecompileCallHasNoRevertReason() {
        frame = setupMessageFrame(tracerOptions);

        final Opcode opcode = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcode.reason()).isNull();
    }

    @Test
    @DisplayName("should record revert reason of precompile call when frame has revert reason")
    void shouldRecordRevertReasonWhenPrecompileCallHasRevertReason() {
        frame = setupMessageFrame(tracerOptions, true);

        final Opcode opcode = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcode.reason())
                .isNotEmpty()
                .isEqualTo(frame.getRevertReason().map(Bytes::toString).orElseThrow());
    }

    @Test
    @DisplayName("should record revert reason of precompile call with revert reason")
    void shouldRecordRevertReasonWhenPrecompileCallHasContractActions() {
        final var contractActionNoRevert = contractAction(CallOperationType.OP_CREATE, OUTPUT.getNumber());
        final var contractActionWithRevert = contractAction(CallOperationType.OP_CALL, REVERT_REASON.getNumber());
        frame = setupMessageFrame(tracerOptions, false, contractActionNoRevert, contractActionWithRevert);

        final Opcode opcode = executePrecompileOperation(frame, Bytes.EMPTY);
        assertThat(opcode.reason())
                .isNotEmpty()
                .isEqualTo(Bytes.of(contractActionWithRevert.getResultData()).toString());
    }

    private Opcode executeOperation(final MessageFrame frame) {
        return executeOperation(frame, null);
    }

    private Opcode executeOperation(final MessageFrame frame, final ExceptionalHaltReason haltReason) {
        final var tracer = new OpcodeTracer();
        tracer.init(frame);
        tracer.tracePreExecution(frame);

        final OperationResult operationResult;
        if (haltReason != null) {
            frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
            frame.setRevertReason(Bytes.of(haltReason.getDescription().getBytes()));
            operationResult = new OperationResult(GAS_COST, haltReason);
        } else {
            operationResult = OPERATION.execute(frame, null);
        }

        tracer.tracePostExecution(frame, operationResult);

        assertThat(tracer.getOpcodes()).hasSize(1);
        return tracer.getOpcodes().getFirst();
    }

    private Opcode executePrecompileOperation(final MessageFrame frame, final Bytes output) {
        final var tracer = new OpcodeTracer();
        tracer.init(frame);
        tracer.tracePreExecution(frame);
        tracer.tracePrecompileCall(frame, GAS_REQUIREMENT, output);

        assertThat(tracer.getOptions()).isEqualTo(tracerOptions);
        assertThat(tracer.getOpcodes()).hasSize(1);
        assertThat(tracer.getContractActions()).isNotNull();
        return tracer.getOpcodes().getFirst();
    }

    private MessageFrame setupMessageFrame(final OpcodeTracerOptions options) {
        return setupMessageFrame(options, false);
    }

    private MessageFrame setupMessageFrame(
            final OpcodeTracerOptions options, final boolean revertReason, final ContractAction... contractActions) {
        when(contractCallContext.getOpcodeTracerOptions()).thenReturn(options);
        when(contractCallContext.getContractActions()).thenReturn(Lists.newArrayList(contractActions));

        final MessageFrame messageFrame = validMessageFrame(revertReason);
        stackItems = setupStackForCapture(messageFrame);
        wordsInMemory = setupMemoryForCapture(messageFrame);
        updatedStorage = setupStorageForCapture(messageFrame);

        return messageFrame;
    }

    private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame) {
        final Map<UInt256, UInt256> storage = ImmutableSortedMap.of(
                UInt256.ZERO, UInt256.valueOf(233),
                UInt256.ONE, UInt256.valueOf(2424));

        when(recipientAccount.getUpdatedStorage()).thenReturn(storage);
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

    private MessageFrame validMessageFrame() {
        return validMessageFrame(false);
    }

    private MessageFrame validMessageFrame(final boolean revertReason) {
        REMAINING_GAS.set(REMAINING_GAS.get() - GAS_COST);

        final MessageFrame messageFrame = validMessageFrameBuilder()
                .contextVariables(Map.of(ContractCallContext.CONTEXT_NAME, contractCallContext))
                .build();
        messageFrame.setCurrentOperation(OPERATION);
        messageFrame.setPC(10);
        messageFrame.setGasRemaining(REMAINING_GAS.get());
        if (revertReason) {
            messageFrame.setRevertReason(Bytes.of("Revert reason".getBytes()));
        }
        return messageFrame;
    }

    private MessageFrame.Builder validMessageFrameBuilder() {
        return new MessageFrame.Builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .code(CodeV0.EMPTY_CODE)
                .sender(Address.ZERO)
                .originator(Address.ZERO)
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .address(Address.ZERO)
                .contract(Address.ZERO)
                .inputData(Bytes.EMPTY)
                .initialGas(INITIAL_GAS)
                .value(Wei.ZERO)
                .apparentValue(Wei.ZERO)
                .worldUpdater(worldUpdater)
                .gasPrice(Wei.of(GAS_PRICE))
                .blockValues(mock(BlockValues.class))
                .blockHashLookup(ignored -> Hash.wrap(Bytes32.ZERO));
    }

    private ContractAction contractAction(final CallOperationType callOperationType, final int resultDataType) {
        return ContractAction.builder()
                .callDepth(1)
                .caller(EntityId.of("0.0.1"))
                .callerType(EntityType.ACCOUNT)
                .callOperationType(callOperationType.getNumber())
                .callType(ContractActionType.PRECOMPILE.getNumber())
                .consensusTimestamp(new SecureRandom().nextLong())
                .gas(REMAINING_GAS.get())
                .gasUsed(GAS_PRICE)
                .index(1)
                .input(new byte[0])
                .payerAccountId(EntityId.of("0.0.2"))
                .recipientAccount(EntityId.of("0.0.3"))
                .recipientAddress(new byte[0])
                .recipientContract(EntityId.of("0.0.4"))
                .resultData(resultDataType == REVERT_REASON.getNumber() ? "revert reason".getBytes() : new byte[0])
                .resultDataType(resultDataType)
                .value(1L)
                .build();
    }
}
