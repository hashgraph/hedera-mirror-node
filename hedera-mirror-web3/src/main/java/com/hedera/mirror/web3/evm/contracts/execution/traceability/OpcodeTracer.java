package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

@Getter
public class OpcodeTracer implements HederaEvmOperationTracer {

    private OpcodeTracerOptions options;
    private List<Opcode> opcodes;

    @Override
    public void init(MessageFrame initialFrame) {
        init(initialFrame, new OpcodeTracerOptions());
    }

    @SuppressWarnings("java:S1172")
    public void init(MessageFrame ignored, OpcodeTracerOptions opcodeTracerOptions) {
        opcodes = new ArrayList<>();
        options = opcodeTracerOptions;
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        final Optional<Bytes[]> memory = captureMemory(frame);
        final Optional<Bytes[]> stackPostExecution = captureStack(frame);
        final Optional<Map<UInt256, UInt256>> storage = captureStorage(frame);
        OptionalLong gasCostOptional = operationResult.getGasCost() == 0 ? OptionalLong.empty() : OptionalLong.of(operationResult.getGasCost());
        opcodes.add(new Opcode(
                frame.getPC(),
                Optional.of(frame.getCurrentOperation().getName()),
                frame.getRemainingGas(),
                gasCostOptional,
                frame.getDepth(),
                stackPostExecution,
                memory,
                storage,
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
            final Map<UInt256, UInt256> storageContents =
                    new TreeMap<>(
                            frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getUpdatedStorage());

            return Optional.of(storageContents);
        } catch (final ModificationNotAllowedException e) {
            return Optional.of(new TreeMap<>());
        }
    }

    @Override
    public void tracePrecompileCall(
            final MessageFrame frame, final long gasRequirement, final Bytes output) {
        //To be implemented
        opcodes.add(new Opcode(
                frame.getPC(),
                Optional.empty(),
                frame.getRemainingGas(),
                OptionalLong.empty(),
                frame.getDepth(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                frame.getRevertReason().map(Bytes::toString).orElse(null)
        ));
    }
}
