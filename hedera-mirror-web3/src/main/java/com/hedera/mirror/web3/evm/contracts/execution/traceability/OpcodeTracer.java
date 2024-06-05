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

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.springframework.util.CollectionUtils;

@Named
@CustomLog
@Getter
public class OpcodeTracer implements HederaEvmOperationTracer {

    private OpcodeTracerOptions options;
    private List<Opcode> opcodes;
    private List<ContractAction> contractActions;

    @Override
    public void init(MessageFrame initialFrame) {
        opcodes = new ArrayList<>();
        ContractCallContext ctx = initialFrame.getContextVariable(ContractCallContext.CONTEXT_NAME);
        options = ctx.getOpcodeTracerOptions();
        contractActions = ctx.getContractActions();
        if (CollectionUtils.isEmpty(contractActions)) {
            log.warn("No contract actions found in context!");
        }
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        final List<Bytes> memory = captureMemory(frame);
        final List<Bytes> stack = captureStack(frame);
        final Map<Bytes, Bytes> storage = captureStorage(frame);
        opcodes.add(new Opcode(
                frame.getPC(),
                frame.getCurrentOperation().getName(),
                frame.getRemainingGas(),
                operationResult.getGasCost(),
                frame.getDepth(),
                stack,
                memory,
                storage,
                frame.getRevertReason().map(Bytes::toString).orElse(null)
        ));
    }

    @Override
    public void tracePrecompileCall(final MessageFrame frame, final long gasRequirement, final Bytes output) {
        final Optional<Bytes> revertReason = frame.getRevertReason().isPresent() ?
                frame.getRevertReason() : getRevertReason(contractActions);

        revertReason.ifPresent(bytes -> log.trace("Revert reason: {}", bytes.toHexString()));

        opcodes.add(new Opcode(
                frame.getPC(),
                frame.getCurrentOperation() != null ? frame.getCurrentOperation().getName() : StringUtils.EMPTY,
                frame.getRemainingGas(),
                output != null ? gasRequirement : 0L,
                frame.getDepth(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                revertReason.map(Bytes::toString).orElse(null)
        ));
    }

    private List<Bytes> captureMemory(final MessageFrame frame) {
        if (!options.isMemory()) {
            return Collections.emptyList();
        }

        final Bytes[] memoryContents = new Bytes[frame.memoryWordSize()];
        for (int i = 0; i < memoryContents.length; i++) {
            memoryContents[i] = frame.readMemory(i * 32L, 32);
        }
        return Arrays.asList(memoryContents);
    }

    private List<Bytes> captureStack(final MessageFrame frame) {
        if (!options.isStack()) {
            return Collections.emptyList();
        }

        final Bytes[] stackContents = new Bytes[frame.stackSize()];
        for (int i = 0; i < stackContents.length; i++) {
            // Record stack contents in reverse
            stackContents[i] = frame.getStackItem(stackContents.length - i - 1);
        }
        return Arrays.asList(stackContents);
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
            final Address address = frame.getRecipientAddress();
            final MutableAccount account = frame.getWorldUpdater().getAccount(address);

            if (account == null) {
                log.warn("Failed to retrieve storage contents. Account not found in WorldUpdater");
                return Collections.emptyMap();
            }

            return new TreeMap<>(account.getUpdatedStorage());
        } catch (final ModificationNotAllowedException e) {
            log.warn(e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private static Optional<Bytes> getRevertReason(List<ContractAction> contractActions) {
        if (CollectionUtils.isEmpty(contractActions)) {
            return Optional.empty();
        }
        return contractActions.stream()
                .filter(ContractAction::hasRevertReason)
                .map(action -> Bytes.of(action.getResultData()))
                .findFirst();
    }
}
