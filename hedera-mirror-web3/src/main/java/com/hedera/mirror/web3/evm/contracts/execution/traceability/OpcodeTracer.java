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
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import jakarta.inject.Named;
import java.util.ArrayList;
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
    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        final List<Bytes> memory = captureMemory(frame);
        final List<Bytes> stack = captureStack(frame);
        final Map<Bytes, Bytes> storage = captureStorage(frame);
        ContractCallContext context = getContext();
        Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(frame.getCurrentOperation().getName())
                .gas(frame.getRemainingGas())
                .gasCost(operationResult.getGasCost())
                .depth(frame.getDepth())
                .stack(stack)
                .memory(memory)
                .storage(storage)
                .reason(frame.getRevertReason().map(Bytes::toString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    @Override
    public void tracePrecompileCall(final MessageFrame frame, final long gasRequirement, final Bytes output) {
        ContractCallContext context = getContext();
        Optional<Bytes> revertReason = isCallToHederaTokenService(frame) ? getRevertReasonFromContractActions(context.getContractActions()) : frame.getRevertReason();
        Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(frame.getCurrentOperation() != null
                        ? frame.getCurrentOperation().getName()
                        : StringUtils.EMPTY)
                .gas(frame.getRemainingGas())
                .gasCost(output != null ? gasRequirement : 0L)
                .depth(frame.getDepth())
                .stack(Collections.emptyList())
                .memory(Collections.emptyList())
                .storage(Collections.emptyMap())
                .reason(revertReason.map(Bytes::toString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    private List<Bytes> captureMemory(final MessageFrame frame) {
        if (!getOptions().isMemory()) {
            return Collections.emptyList();
        }

        int size = frame.memoryWordSize();
        var memory = new ArrayList<Bytes>(size);
        for (int i = 0; i < size; i++) {
            memory.add(frame.readMemory(i * 32L, 32));
        }

        return memory;
    }

    private List<Bytes> captureStack(final MessageFrame frame) {
        if (!getOptions().isStack()) {
            return Collections.emptyList();
        }

        int size = frame.stackSize();
        var stack = new ArrayList<Bytes>(size);
        for (int i = 0; i < size; ++i) {
            stack.add(frame.getStackItem(size - 1 - i));
        }

        return stack;
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame) {
        if (!getOptions().isStorage()) {
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
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    private Optional<Bytes> getRevertReasonFromContractActions(List<ContractAction> contractActions) {
        if (CollectionUtils.isEmpty(contractActions)) {
            return Optional.empty();
        }
        int currentActionIndex = getContext().getContractActionsCounter();

        return contractActions.stream()
                .filter(action -> action.hasRevertReason() && action.getIndex() == currentActionIndex)
                .map(action -> Bytes.of(action.getResultData()))
                .findFirst();
    }

    private ContractCallContext getContext() {
        return ContractCallContext.get();
    }

    public OpcodeTracerOptions getOptions() {
        ContractCallContext context = getContext();
        return context.getOpcodeTracerOptions();
    }

    private boolean isCallToHederaTokenService(MessageFrame frame) {
        Address recipientAddress = frame.getRecipientAddress();
        return recipientAddress.equals(Address.fromHexString(SyntheticTxnFactory.HTS_PRECOMPILED_CONTRACT_ADDRESS));
    }

    public void traceContextEnter(final MessageFrame frame) {
        getContext().incrementContractActionsCounter();
    }

    public void traceContextReEnter(final MessageFrame frame) {
        getContext().decrementContractActionsCounter();
    }

    public void traceContextExit(final MessageFrame frame) {
        getContext().incrementContractActionsCounter();
    }
}
