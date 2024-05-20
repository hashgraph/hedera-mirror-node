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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.common.domain.transaction.Opcode;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
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
    private static final long GAS_COST = 20L;
    private static final long GAS_PRICE = 25L;
    private static final AtomicReference<Long> REMAINING_GAS = new AtomicReference<>();
    private static final Operation OPERATION = new AbstractOperation(0x02, "MUL", 2, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
            return new OperationResult(GAS_COST, null);
        }
    };

    @Mock
    private WorldUpdater worldUpdater;

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
        frame = validMessageFrame();
    }

    @Test
    @DisplayName("should record program counter")
    void shouldRecordProgramCounter() {
        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.pc()).isEqualTo(frame.getPC());
    }

    @Test
    @DisplayName("should record opcode")
    void shouldRecordOpcode() {
        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.op()).isNotEmpty();
        assertThat(opcode.op()).contains(OPERATION.getName());
    }

    @Test
    @DisplayName("should record depth")
    void shouldRecordDepth() {
        // simulate 4 calls
        final int expectedDepth = 4;
        for (int i = 0; i < expectedDepth; i++) {
            frame.getMessageFrameStack().add(validMessageFrame());
        }

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.depth()).isEqualTo(expectedDepth);
    }

    @Test
    @DisplayName("should record remaining gas")
    void shouldRecordRemainingGas() {
        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.gas()).isEqualTo(REMAINING_GAS.get());
    }

    @Test
    @DisplayName("should record gas cost")
    void shouldRecordGasCost() {
        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.gasCost()).isNotEmpty();
        assertThat(opcode.gasCost().getAsLong()).isEqualTo(GAS_COST);
    }

    @Test
    @DisplayName("given stack is enabled in tracer options, should record stack")
    void shouldRecordStackWhenEnabled() {
        tracerOptions.setStack(true);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.stack()).isNotEmpty();
        assertThat(opcode.stack().get()).containsExactly(stackItems);
    }

    @Test
    @DisplayName("given stack is disabled in tracer options, should not record stack")
    void shouldNotRecordStackWhenDisabled() {
        tracerOptions.setStack(false);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.stack()).isEmpty();
    }

    @Test
    @DisplayName("given memory is enabled in tracer options, should record memory")
    void shouldRecordMemoryWhenEnabled() {
        tracerOptions.setMemory(true);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.memory()).isNotEmpty();
        assertThat(opcode.memory().get()).containsExactly(wordsInMemory);
    }

    @Test
    @DisplayName("given memory is disabled in tracer options, should not record memory")
    void shouldNotRecordMemoryWhenDisabled() {
        tracerOptions.setMemory(false);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.memory()).isEmpty();
    }

    @Test
    @DisplayName("given storage is enabled in tracer options, should record storage")
    void shouldRecordStorageWhenEnabled() {
        tracerOptions.setStorage(true);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.storage()).isNotEmpty();
        assertThat(opcode.storage().get()).containsAllEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("given storage is disabled in tracer options, should not record storage")
    void shouldNotRecordStorageWhenDisabled() {
        tracerOptions.setStorage(false);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions);
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName("given exceptional halt occurs, should capture frame data and halt reason")
    void shouldCaptureFrameWhenExceptionalHaltOccurs() {
        tracerOptions.setStack(true);
        tracerOptions.setMemory(true);
        tracerOptions.setStorage(true);
        setupDataForCapture(frame, tracerOptions);

        final Opcode opcode = executeOperation(frame, tracerOptions, ExceptionalHaltReason.INSUFFICIENT_GAS);
        assertThat(opcode.reason()).contains(Hex.encodeHexString(ExceptionalHaltReason.INSUFFICIENT_GAS.getDescription().getBytes()));
        assertThat(opcode.stack()).contains(stackItems);
        assertThat(opcode.memory()).contains(wordsInMemory);
        assertThat(opcode.storage()).contains(updatedStorage);
    }

    private Opcode executeOperation(final MessageFrame frame,
                                    final OpcodeTracerOptions options) {
        return executeOperation(frame, options, null);
    }

    private Opcode executeOperation(final MessageFrame frame,
                                    final OpcodeTracerOptions options,
                                    final ExceptionalHaltReason haltReason) {
        final var tracer = new OpcodeTracer();
        tracer.init(frame, options);
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

    private void setupDataForCapture(final MessageFrame messageFrame, final OpcodeTracerOptions options) {
        stackItems = setupStackForCapture(messageFrame, options);
        wordsInMemory = setupMemoryForCapture(messageFrame, options);
        updatedStorage = setupStorageForCapture(messageFrame, options);
    }

    private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStorage()) {
            return ImmutableSortedMap.of();
        }

        final Map<UInt256, UInt256> storage = ImmutableSortedMap.of(
                UInt256.ZERO, UInt256.valueOf(233),
                UInt256.ONE, UInt256.valueOf(2424)
        );

        final MutableAccount account = mock(MutableAccount.class);
        when(account.getUpdatedStorage()).thenReturn(storage);
        when(worldUpdater.getAccount(frame.getRecipientAddress())).thenReturn(account);

        return storage;
    }

    private UInt256[] setupStackForCapture(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStack()) {
            return new UInt256[0];
        }

        final UInt256[] stack = new UInt256[] {
                UInt256.fromHexString("0x01"),
                UInt256.fromHexString("0x02"),
                UInt256.fromHexString("0x03")
        };

        for (final UInt256 stackItem : stack) {
            frame.pushStackItem(stackItem);
        }

        return stack;
    }

    private Bytes[] setupMemoryForCapture(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isMemory()) {
            return new Bytes[0];
        }

        final Bytes[] words = new Bytes[] {
                Bytes.fromHexString("0x01", 32),
                Bytes.fromHexString("0x02", 32),
                Bytes.fromHexString("0x03", 32)
        };

        for (int i = 0; i < words.length; i++) {
            frame.writeMemory(i * 32, 32, words[i]);
        }

        return words;
    }

    private MessageFrame validMessageFrame() {
        REMAINING_GAS.set(REMAINING_GAS.get() - GAS_COST);

        final MessageFrame messageFrame = validMessageFrameBuilder().build();
        messageFrame.setCurrentOperation(OPERATION);
        messageFrame.setPC(10);
        messageFrame.setGasRemaining(REMAINING_GAS.get());
        return messageFrame;
    }

    private MessageFrame.Builder validMessageFrameBuilder() {
        return new MessageFrame.Builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .code(CodeV0.EMPTY_CODE)
                .sender(Address.ZERO)
                .originator(Address.ZERO)
                .completer(_ -> {})
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
                .blockHashLookup(_ -> Hash.wrap(Bytes32.ZERO));
    }
}
