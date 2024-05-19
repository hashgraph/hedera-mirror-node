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

import com.hedera.mirror.common.domain.transaction.Opcode;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpcodeTracerTest {

  private static final long INITIAL_GAS = 1000L;

  @Mock
  private WorldUpdater worldUpdater;

  private final Operation anOperation =
      new AbstractOperation(0x02, "MUL", 2, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
          return new OperationResult(20L, null);
        }
      };

  private final CallOperation callOperation = new CallOperation(new CancunGasCalculator());

  @Test
  void shouldRecordProgramCounter() {
    final MessageFrame frame = validMessageFrame();
    frame.setPC(10);
    final Opcode opcode = opcode(frame);
    assertThat(opcode.pc()).isEqualTo(10);
  }

  @Test
  void shouldRecordOpcode() {
    final MessageFrame frame = validMessageFrame();
    final Opcode opcode = opcode(frame);
    assertThat(opcode.op()).isNotEmpty();
    assertThat(opcode.op()).contains("MUL");
  }

  @Test
  void shouldRecordDepth() {
    final MessageFrame frame = validMessageFrame();
    // simulate 4 calls
    frame.getMessageFrameStack().add(frame);
    frame.getMessageFrameStack().add(frame);
    frame.getMessageFrameStack().add(frame);
    frame.getMessageFrameStack().add(frame);
    final Opcode opcode = opcode(frame);
    assertThat(opcode.depth()).isEqualTo(4);
  }

  @Test
  void shouldRecordRemainingGas() {
    final MessageFrame frame = validMessageFrame();
    final Opcode opcode = opcode(frame);
    assertThat(opcode.gas()).isEqualTo(INITIAL_GAS);
  }

  @Test
  void shouldRecordStackWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final UInt256 stackItem1 = UInt256.fromHexString("0x01");
    final UInt256 stackItem2 = UInt256.fromHexString("0x02");
    final UInt256 stackItem3 = UInt256.fromHexString("0x03");
    frame.pushStackItem(stackItem1);
    frame.pushStackItem(stackItem2);
    frame.pushStackItem(stackItem3);
    final Opcode opcode = opcode(frame, new OpcodeTracerOptions(true, false, false));
    assertThat(opcode.stack()).isNotEmpty();
    assertThat(opcode.stack().get()).containsExactly(stackItem1, stackItem2, stackItem3);
  }

  @Test
  void shouldNotRecordStackWhenDisabled() {
    final Opcode opcode = opcode(validMessageFrame(), new OpcodeTracerOptions(false, false, false));
    assertThat(opcode.stack()).isEmpty();
  }

  @Test
  void shouldRecordMemoryWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final Bytes word1 = Bytes.fromHexString("0x01", 32);
    final Bytes word2 = Bytes.fromHexString("0x02", 32);
    final Bytes word3 = Bytes.fromHexString("0x03", 32);
    frame.writeMemory(0, 32, word1);
    frame.writeMemory(32, 32, word2);
    frame.writeMemory(64, 32, word3);
    final Opcode opcode = opcode(frame, new OpcodeTracerOptions(false, true, false));
    assertThat(opcode.memory()).isNotEmpty();
    assertThat(opcode.memory().get()).containsExactly(word1, word2, word3);
  }

  @Test
  void shouldNotRecordMemoryWhenDisabled() {
    final Opcode opcode = opcode(validMessageFrame(), new OpcodeTracerOptions(false, false, false));
    assertThat(opcode.memory()).isEmpty();
  }

  @Test
  void shouldRecordStorageWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);
    final Opcode opcode = opcode(frame, new OpcodeTracerOptions(false, false, true));
    assertThat(opcode.storage()).isNotEmpty();
    assertThat(opcode.storage().get()).containsAllEntriesOf(updatedStorage);
  }

  @Test
  void shouldNotRecordStorageWhenDisabled() {
    final Opcode opcode = opcode(validMessageFrame(), new OpcodeTracerOptions(false, false, false));
    assertThat(opcode.storage()).isEmpty();
  }

  @Test
  void shouldNotAddGas() {
    final Opcode opcode = opcode(validCallFrame(), new OpcodeTracerOptions(false, false, false));
    assertThat(opcode.gasCost()).isNotEmpty();
    assertThat(opcode.gasCost().getAsLong()).isEqualTo(20L);
  }

  @Test
  void shouldCaptureFrameWhenExceptionalHaltOccurs() {
    final ExceptionalHaltReason haltReason = ExceptionalHaltReason.INSUFFICIENT_GAS;
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);

    final OpcodeTracer tracer = new OpcodeTracer();
    tracer.init(frame, new OpcodeTracerOptions(true, true, true));
    frame.setRevertReason(Bytes.of(haltReason.getDescription().getBytes()));
    tracer.tracePostExecution(frame, new OperationResult(50L, haltReason));

    final Opcode opcode = getOnlyOpcode(tracer);
    assertThat(opcode.reason()).contains(Bytes.of(haltReason.getDescription().getBytes()).toString());
    assertThat(opcode.storage()).isNotEmpty();
    assertThat(opcode.storage().get()).containsAllEntriesOf(updatedStorage);
  }

  private Opcode opcode(final MessageFrame frame) {
    return opcode(frame, new OpcodeTracerOptions());
  }

  private Opcode opcode(final MessageFrame frame, final OpcodeTracerOptions options) {
    final var tracer = new OpcodeTracer();
    tracer.init(frame, options);
    tracer.tracePreExecution(frame);
    OperationResult operationResult = anOperation.execute(frame, null);
    tracer.tracePostExecution(frame, operationResult);
    return getOnlyOpcode(tracer);
  }

  private MessageFrame validMessageFrame() {
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.setCurrentOperation(anOperation);
    frame.setPC(10);
    return frame;
  }

  private MessageFrame validCallFrame() {
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.setCurrentOperation(callOperation);
    frame.setPC(10);
    return frame;
  }

  private Opcode getOnlyOpcode(final OpcodeTracer tracer) {
    Assertions.assertThat(tracer.getOpcodes()).hasSize(1);
    return tracer.getOpcodes().getFirst();
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
            .gasPrice(Wei.of(25))
            .blockValues(mock(BlockValues.class))
            .blockHashLookup(_ -> Hash.wrap(Bytes32.ZERO));
  }

  private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame) {
    final MutableAccount account = mock(MutableAccount.class);
    when(worldUpdater.getAccount(frame.getRecipientAddress())).thenReturn(account);

    final Map<UInt256, UInt256> updatedStorage = new TreeMap<>();
    updatedStorage.put(UInt256.ZERO, UInt256.valueOf(233));
    updatedStorage.put(UInt256.ONE, UInt256.valueOf(2424));
    when(account.getUpdatedStorage()).thenReturn(updatedStorage);
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(0, 32, word1);
    frame.writeMemory(32, 32, word2);
    frame.writeMemory(64, 32, word3);
    return updatedStorage;
  }
}
