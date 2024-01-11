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

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

@Named
@CustomLog
@RequiredArgsConstructor
public class MirrorOperationTracer implements HederaEvmOperationTracer {

    private final TraceProperties traceProperties;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    @Override
    public void tracePostExecution(final MessageFrame currentFrame, final Operation.OperationResult operationResult) {
        if (!traceProperties.isEnabled()) {
            return;
        }
        if (traceProperties.stateFilterCheck(currentFrame.getState())) {
            return;
        }

        final var recipientAddress = currentFrame.getRecipientAddress();
        final var recipientNum = mirrorEvmContractAliases.resolveForEvm(recipientAddress);

        if (traceProperties.contractFilterCheck(recipientNum.toHexString())) {
            return;
        }

        log.info(
                "type={} operation={}, callDepth={}, contract={}, sender={}, recipient={}, remainingGas={}, revertReason={}, input={}, output={}, return={}",
                currentFrame.getType(),
                currentFrame.getCurrentOperation().getName(),
                currentFrame.getDepth(),
                currentFrame.getContractAddress().toShortHexString(),
                currentFrame.getSenderAddress().toShortHexString(),
                currentFrame.getRecipientAddress().toShortHexString(),
                currentFrame.getRemainingGas(),
                currentFrame.getRevertReason().orElse(Bytes.EMPTY).toHexString(),
                currentFrame.getInputData().toShortHexString(),
                currentFrame.getOutputData().toShortHexString(),
                currentFrame.getReturnData().toShortHexString());
    }
}
