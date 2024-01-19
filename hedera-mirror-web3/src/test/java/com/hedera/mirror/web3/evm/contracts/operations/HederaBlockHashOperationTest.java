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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.ContextExtension;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class HederaBlockHashOperationTest {

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private EVM evm;

    @Mock
    private GasCalculator gasCalculator;

    private HederaBlockHashOperation subject;

    @BeforeEach
    void setup() {
        subject = new HederaBlockHashOperation(gasCalculator);
    }

    @Test
    void testTooLargeValuePassedReturnsZero() {
        given(messageFrame.popStackItem()).willReturn(Bytes.of(0xa, 0xb, 0xa, 0xb, 0xa, 0xb, 0xa, 0xb, 0xa));

        var result = subject.executeFixedCostOperation(messageFrame, evm);

        verify(messageFrame, times(1)).pushStackItem(UInt256.ZERO);
    }
}
