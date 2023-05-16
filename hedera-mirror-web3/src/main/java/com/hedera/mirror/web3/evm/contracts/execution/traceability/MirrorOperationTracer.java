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

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.TracingProperties;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import java.nio.charset.StandardCharsets;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.operation.Operation;

@CustomLog
@Named
@RequiredArgsConstructor
public class MirrorOperationTracer implements HederaEvmOperationTracer {

    private final TracingProperties tracingProperties;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    @Override
    public void init(final MessageFrame initialFrame) {
        if (tracingProperties.isEnabled()) {
            final String parentIndex = "0.0.1.0 " + initialFrame.getType();
            trace(initialFrame, parentIndex);
        }
    }

    @Override
    public void tracePostExecution(final MessageFrame currentFrame, final Operation.OperationResult operationResult) {
        if (!tracingProperties.isEnabled()) {
            return;
        }
        if (tracingProperties.stateFilterCheck(currentFrame.getState().name())) {
            return;
        }

        final var recipientAddress = currentFrame.getRecipientAddress();
        final var recipientNum = mirrorEvmContractAliases.resolveForEvm(recipientAddress);

        if (tracingProperties.contractFilterCheck(recipientNum)) {
            return;
        }

        final var frameState = currentFrame.getState();
        if (frameState == State.CODE_SUSPENDED) {
            final String childIndex = "0.0.1." + currentFrame.getMessageStackDepth() + " " + currentFrame.getType();
            trace(currentFrame, childIndex);
        }
    }

    public void trace(final MessageFrame currentFrame, String index) {
        final var inputData = currentFrame.getInputData() != null
                ? currentFrame.getInputData().toHexString()
                : "0x";

        log.info(
                index
                        + " messageFrame={}, callDepth={}, remainingGas={}, sender={}, recipient={}, contract={}, revertReason={}, inputData={}",
                currentFrame.toString(),
                currentFrame.getMessageStackDepth(),
                currentFrame.getRemainingGas(),
                currentFrame.getSenderAddress(),
                currentFrame.getRecipientAddress(),
                currentFrame.getContractAddress(),
                StringUtils.toEncodedString(
                        currentFrame.getRevertReason().orElse(Bytes.EMPTY).toArray(), StandardCharsets.UTF_16),
                inputData);
    }
}
