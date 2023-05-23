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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.frame.MessageFrame.Type;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.BeforeEach;
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

    private TraceProperties traceProperties;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    private MirrorOperationTracer mirrorOperationTracer;

    private static final Address contract = Address.fromHexString("0x2");
    private static final Address recipient = Address.fromHexString("0x3");
    private static final Address sender = Address.fromHexString("0x4");
    private static final long initialGas = 1000L;
    private static final Bytes input = Bytes.of("inputData".getBytes());

    @BeforeEach
    void setup() {
        traceProperties = new TraceProperties();
        mirrorOperationTracer = new MirrorOperationTracer(traceProperties, mirrorEvmContractAliases);
    }

    @Test
    void traceDisabled(CapturedOutput output) {
        traceProperties.setEnabled(false);
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);
        assertThat(output).isEmpty();
    }

    @Test
    void stateFilterMismatch(CapturedOutput output) {
        traceProperties.setEnabled(true);
        traceProperties.setStatus(Set.of(State.CODE_EXECUTING));
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);

        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(output).isEmpty();
    }

    @Test
    void stateFilter(CapturedOutput output) {
        traceProperties.setEnabled(true);
        traceProperties.setStatus(Set.of(State.CODE_SUSPENDED));
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(mirrorEvmContractAliases.resolveForEvm(any())).willReturn(recipient);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getMessageStackDepth()).willReturn(1);
        given(mirrorEvmContractAliases.resolveForEvm(recipient)).willReturn(recipient);

        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(output)
                .contains(
                        "MESSAGE_CALL",
                        "callDepth=1",
                        "recipient=0x0000000000000000000000000000000000000003",
                        "messageFrame=messageFrame",
                        "inputData=0x696e70757444617461",
                        "remainingGas=1000",
                        "sender=0x0000000000000000000000000000000000000004",
                        "revertReason=");
    }

    @Test
    void contractFilterMismatch(CapturedOutput output) {
        traceProperties.setEnabled(true);
        traceProperties.setContract(Set.of(contract.toHexString()));
        given(mirrorEvmContractAliases.resolveForEvm(any())).willReturn(recipient);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);

        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(output).isEmpty();
    }

    @Test
    void contractFilter(CapturedOutput output) {
        traceProperties.setEnabled(true);
        traceProperties.setContract(Set.of(recipient.toHexString()));
        given(mirrorEvmContractAliases.resolveForEvm(any())).willReturn(recipient);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getMessageStackDepth()).willReturn(1);
        given(mirrorEvmContractAliases.resolveForEvm(recipient)).willReturn(recipient);

        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(output)
                .contains(
                        "MESSAGE_CALL",
                        "callDepth=1",
                        "recipient=0x0000000000000000000000000000000000000003",
                        "messageFrame=messageFrame",
                        "inputData=0x696e70757444617461",
                        "remainingGas=1000",
                        "sender=0x0000000000000000000000000000000000000004",
                        "revertReason=");
    }

    @Test
    void tracePostExecution(CapturedOutput output) {
        traceProperties.setEnabled(true);

        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(mirrorEvmContractAliases.resolveForEvm(recipient)).willReturn(recipient);
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getMessageStackDepth()).willReturn(1);
        given(mirrorEvmContractAliases.resolveForEvm(recipient)).willReturn(recipient);

        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);
        assertThat(output)
                .contains(
                        "MESSAGE_CALL",
                        "callDepth=0",
                        "recipient=0x0000000000000000000000000000000000000003",
                        "messageFrame=messageFrame",
                        "inputData=0x696e70757444617461",
                        "remainingGas=1000",
                        "sender=0x0000000000000000000000000000000000000004",
                        "revertReason=")
                .contains(
                        "MESSAGE_CALL",
                        "callDepth=1",
                        "recipient=0x0000000000000000000000000000000000000003",
                        "messageFrame=messageFrame",
                        "inputData=0x696e70757444617461",
                        "remainingGas=1000",
                        "sender=0x0000000000000000000000000000000000000004",
                        "revertReason=");
    }
}
