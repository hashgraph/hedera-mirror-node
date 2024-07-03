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

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.stream.proto.ContractActionType.PRECOMPILE;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_FAILED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.ContractActionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.springframework.util.CollectionUtils;

@Named
@CustomLog
@Getter
public class OpcodeTracer implements HederaOperationTracer {

    @Override
    public void init(final MessageFrame frame) {
        getContext().incrementContractActionsCounter();
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        ContractCallContext context = getContext();
        if (frame.getState() == CODE_SUSPENDED) {
            context.incrementContractActionsCounter();
        }
        OpcodeTracerOptions options = context.getOpcodeTracerOptions();
        final List<Bytes> memory = captureMemory(frame, options);
        final List<Bytes> stack = captureStack(frame, options);
        final Map<Bytes, Bytes> storage = captureStorage(frame, options);
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
        Optional<Bytes> revertReason = isCallToHederaTokenService(frame)
                ? getRevertReasonFromContractActions(context)
                : frame.getRevertReason();
        Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(
                        frame.getCurrentOperation() != null
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

    @Override
    public void traceAccountCreationResult(MessageFrame frame, Optional<ExceptionalHaltReason> haltReason) {
        if (haltReason.isPresent() && existsSyntheticActionForFrame(frame)) {
            getContext().incrementContractActionsCounter();
        }
    }

    @Override
    public void tracePrecompileResult(MessageFrame frame, ContractActionType type) {
        if (type.equals(PRECOMPILE) && frame.getState().equals(EXCEPTIONAL_HALT)) {
            // if an ETH precompile call exceptional halted, the action is already finalized
            return;
        }
        if (existsSyntheticActionForFrame(frame)) {
            getContext().incrementContractActionsCounter();
        }
    }

    private List<Bytes> captureMemory(final MessageFrame frame, OpcodeTracerOptions options) {
        if (!options.isMemory()) {
            return Collections.emptyList();
        }

        int size = frame.memoryWordSize();
        var memory = new ArrayList<Bytes>(size);
        for (int i = 0; i < size; i++) {
            memory.add(frame.readMemory(i * 32L, 32));
        }

        return memory;
    }

    private List<Bytes> captureStack(final MessageFrame frame, OpcodeTracerOptions options) {
        if (!options.isStack()) {
            return Collections.emptyList();
        }

        int size = frame.stackSize();
        var stack = new ArrayList<Bytes>(size);
        for (int i = 0; i < size; ++i) {
            stack.add(frame.getStackItem(size - 1 - i));
        }

        return stack;
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame, OpcodeTracerOptions options) {
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
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    private Optional<Bytes> getRevertReasonFromContractActions(ContractCallContext context) {
        List<ContractAction> contractActions = context.getContractActions();
        int currentActionIndex = context.getContractActionIndexOfCurrentFrame();

        if (CollectionUtils.isEmpty(contractActions)) {
            return Optional.empty();
        }

        return contractActions.stream()
                .filter(action -> action.hasRevertReason() && action.getIndex() == currentActionIndex)
                .map(action -> Bytes.of(action.getResultData()))
                .map(this::maybeDecodeRevertReason)
                .findFirst();
    }

    public ContractCallContext getContext() {
        return ContractCallContext.get();
    }

    private boolean isCallToHederaTokenService(MessageFrame frame) {
        Address recipientAddress = frame.getRecipientAddress();
        return recipientAddress.equals(Address.fromHexString(SyntheticTxnFactory.HTS_PRECOMPILED_CONTRACT_ADDRESS));
    }

    /**
     * When a contract tries to call a non-existing address
     * (resulting in a {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} failure),
     * a synthetic action is created to record this, otherwise the details of the intended call
     * (e.g. the targeted invalid address) and sequence of events leading to the failure are lost
     */
    private boolean existsSyntheticActionForFrame(MessageFrame frame) {
        return (frame.getState() == EXCEPTIONAL_HALT || frame.getState() == COMPLETED_FAILED)
                && frame.getType().equals(MESSAGE_CALL)
                && frame.getExceptionalHaltReason().isPresent()
                && frame.getExceptionalHaltReason().get().equals(INVALID_SOLIDITY_ADDRESS);
    }

    private Bytes maybeDecodeRevertReason(final Bytes revertReason) {
        boolean isNullOrEmpty = revertReason == null || revertReason.isEmpty();

        if (isNullOrEmpty) {
            return Bytes.EMPTY;
        }

        final var trimmedReason = revertReason.trimLeadingZeros();
        ResponseCodeEnum responseCode;
        if (trimmedReason.size() <= 4 && (responseCode = ResponseCodeEnum.forNumber(trimmedReason.toInt())) != null) {
            return Bytes.of(responseCode.name().getBytes());
        }

        String decodedReason = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
        if (StringUtils.isNotEmpty(decodedReason)) {
            return Bytes.of(decodedReason.getBytes());
        }

        return revertReason;
    }
}
