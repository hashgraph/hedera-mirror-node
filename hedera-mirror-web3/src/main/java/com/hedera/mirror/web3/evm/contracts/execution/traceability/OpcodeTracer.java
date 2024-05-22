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
import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

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
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        final Optional<Bytes[]> memory = captureMemory(frame);
        final Optional<Bytes[]> stack = captureStack(frame);
        final Optional<Map<UInt256, UInt256>> storage = captureStorage(frame);
        opcodes.add(new Opcode(
                frame.getPC(),
                Optional.of(frame.getCurrentOperation().getName()),
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
        opcodes.add(new Opcode(
                frame.getPC(),
                Optional.empty(),
                frame.getRemainingGas(),
                output != null ? gasRequirement : 0L,
                frame.getDepth(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                frame.getRevertReason().map(Bytes::toString).orElse(null)
        ));
    }

    private Optional<Bytes[]> captureMemory(final MessageFrame frame) {
        if (!options.isMemory()) {
            return Optional.empty();
        }

        final Bytes[] memoryContents = new Bytes[frame.memoryWordSize()];
        for (int i = 0; i < memoryContents.length; i++) {
            memoryContents[i] = frame.readMemory(i * 32L, 32);
        }
        return Optional.of(memoryContents);
    }

    private Optional<Bytes[]> captureStack(final MessageFrame frame) {
        if (!options.isStack()) {
            return Optional.empty();
        }

        final Bytes[] stackContents = new Bytes[frame.stackSize()];
        for (int i = 0; i < stackContents.length; i++) {
            // Record stack contents in reverse
            stackContents[i] = frame.getStackItem(stackContents.length - i - 1);
        }
        return Optional.of(stackContents);
    }

    private Optional<Map<UInt256, UInt256>> captureStorage(final MessageFrame frame) {
        if (!options.isStorage()) {
            return Optional.empty();
        }

        try {
            final Address address = frame.getRecipientAddress();
            final MutableAccount account = frame.getWorldUpdater().getAccount(address);

            if (account == null) {
                log.warn("Failed to retrieve storage contents. Account not found in WorldUpdater");
                return Optional.of(new TreeMap<>());
            }

            return Optional.of(new TreeMap<>(account.getUpdatedStorage()));
        } catch (final ModificationNotAllowedException e) {
            log.warn(e.getMessage(), e);
            return Optional.of(new TreeMap<>());
        }
    }
}
