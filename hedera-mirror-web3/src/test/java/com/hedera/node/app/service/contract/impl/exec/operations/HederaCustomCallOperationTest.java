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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaCustomCallOperationTest {

    @Mock
    private MessageFrame frame;

    @Mock
    private EVM evm;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private WorldUpdater worldUpdater;

    private HederaCustomCallOperation subject;

    @BeforeEach
    void setUp() {
        subject = new HederaCustomCallOperation(gasCalculator);
    }

    @Test
    void testHappyPath() {
        given(frame.getStackItem(anyInt())).willReturn(Bytes.wrap(new byte[] {1}));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(200L);
        given(frame.stackSize()).willReturn(7);
        given(subject.cost(frame, false)).willReturn(100L);

        final var expected = new Operation.OperationResult(100L, null);
        final var actual = subject.execute(frame, evm);

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void testInsufficientGas() {
        given(frame.getStackItem(anyInt())).willReturn(Bytes.wrap(new byte[] {1}));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(50L);
        given(subject.cost(frame, false)).willReturn(100L);

        final var expected = new Operation.OperationResult(100L, ExceptionalHaltReason.INSUFFICIENT_GAS);
        final var actual = subject.execute(frame, evm);

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void testUnderflowException() {
        given(frame.getStackItem(anyInt())).willThrow(UnderflowException.class);

        final var expected = new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        final var actual = subject.execute(frame, evm);

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
}
