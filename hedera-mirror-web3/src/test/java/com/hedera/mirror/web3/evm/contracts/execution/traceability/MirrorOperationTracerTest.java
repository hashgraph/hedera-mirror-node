/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.mirror.web3.evm.properties.TracingProperties;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MirrorOperationTracerTest {

    @Mock
    private OperationResult operationResult;

    @Mock
    private TracingProperties tracingProperties;

    private MirrorOperationTracer mirrorOperationTracer;

    private static final Address contract = Address.fromHexString("0x2");
    private static final Address recipient = Address.fromHexString("0x3");
    private static final Address sender = Address.fromHexString("0x4");
    private static final long initialGas = 1000L;
    private static final Bytes input = Bytes.of("inputData".getBytes());

    @Test
    void tracePostExecution(CapturedOutput output) {
        mirrorOperationTracer = new MirrorOperationTracer(tracingProperties);
        given(tracingProperties.isEnabled()).willReturn(true);

        final var topLevelMessageFrame = mock(MessageFrame.class);
        given(topLevelMessageFrame.getContractAddress()).willReturn(contract);
        given(topLevelMessageFrame.getRemainingGas()).willReturn(initialGas);
        given(topLevelMessageFrame.getInputData()).willReturn(input);
        given(topLevelMessageFrame.getRecipientAddress()).willReturn(recipient);
        given(topLevelMessageFrame.getSenderAddress()).willReturn(sender);

        mirrorOperationTracer.tracePostExecution(topLevelMessageFrame, operationResult);
        assertThat(output)
                .contains(
                        "recipientAddress=0x0000000000000000000000000000000000000003",
                        "messageFrame=Mock for MessageFrame",
                        "inputData=inputData",
                        "callDepth=0",
                        "remainingGas=1000",
                        "senderAddress=0x0000000000000000000000000000000000000004,",
                        "revertReason=");
    }
}
