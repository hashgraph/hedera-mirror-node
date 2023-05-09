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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;

import com.hedera.mirror.web3.evm.properties.TracingProperties;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import java.nio.charset.StandardCharsets;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

@CustomLog
@Named
@RequiredArgsConstructor
public class MirrorOperationTracer implements HederaEvmOperationTracer {

    private final TracingProperties tracingProperties;

    @Override
    public void tracePostExecution(final MessageFrame currentFrame, final Operation.OperationResult operationResult) {
        if (tracingProperties.isEnabled()) {
            if (!tracingProperties.getStatus().isEmpty()
                    && !tracingProperties
                            .getStatus()
                            .contains(currentFrame.getState().name())) {
                return;
            }

            final var recipientAddress = currentFrame.getRecipientAddress();
            final var recipientNum = entityIdFromEvmAddress(recipientAddress);

            if (!tracingProperties.getContract().isEmpty()
                    && !tracingProperties.getContract().contains(recipientNum)) {
                return;
            }

            final var inputData = new String(
                    currentFrame.getInputData() != null
                            ? currentFrame.getInputData().toArray()
                            : new byte[0]);
            log.debug(
                    "MessageFrame {} has input data {}, call depth {}, remaining gas {}, sender address {}, recipient address {}, contract address {} and revert reason {}",
                    currentFrame.toString(),
                    inputData,
                    currentFrame.getMessageStackDepth(),
                    currentFrame.getRemainingGas(),
                    currentFrame.getSenderAddress(),
                    currentFrame.getRecipientAddress(),
                    currentFrame.getContractAddress(),
                    StringUtils.toEncodedString(
                            currentFrame.getRevertReason().orElse(Bytes.EMPTY).toArray(), StandardCharsets.UTF_16));
        }
    }
}
