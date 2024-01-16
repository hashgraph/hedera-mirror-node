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

package com.hedera.mirror.web3.evm.contracts.operations;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomExtCodeHashOperationTest {
    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private WorldUpdater worldUpdater;

    @Mock
    private EVM evm;

    private CustomExtCodeHashOperation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomExtCodeHashOperation(gasCalculator);
    }

    @Test
    void catchesUnderflowWhenStackIsEmpty() {
        given(frame.getStackItem(0)).willThrow(FixedStack.UnderflowException.class);
        final var expected = new Operation.OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void rejectsMissingNonSystemAddress() {
        givenWellKnownFrameWith(Address.fromHexString("0xfff"));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(null);
        final var expected = new Operation.OperationResult(123L, INVALID_SOLIDITY_ADDRESS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void systemAccount() {
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        final var expected = new Operation.OperationResult(123L, null);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void hasSpecialBehaviorForNonUserAccount() {
        given(frame.getStackItem(anyInt())).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1)));
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        final var expected = new Operation.OperationResult(123L, null);
        assertSameResult(expected, subject.execute(frame, evm));
        verify(frame).popStackItem();
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void delegatesForPresentAddress() {
        givenWellKnownFrameWith(Address.fromHexString("0xfff"));
        given(frame.popStackItem()).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1)));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(new SimpleAccount(null, 0L, null));
        final var expected = new Operation.OperationResult(123L, INSUFFICIENT_GAS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    private void givenWellKnownFrameWith(final Address to) {
        given(frame.getStackItem(0)).willReturn(to);
        given(gasCalculator.extCodeHashOperationGasCost()).willReturn(123L);
    }

    public static void assertSameResult(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
        assertEquals(expected.getGasCost(), actual.getGasCost());
    }
}
