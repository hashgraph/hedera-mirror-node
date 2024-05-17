package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.StorageEntry;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
public class OpcodeTracer implements HederaEvmOperationTracer {

    private List<Opcode> opcodes;

    @Override
    public void init(MessageFrame initialFrame) {
        opcodes = new ArrayList<>();
    }

    @Override
    public void tracePostExecution(MessageFrame frame, Operation.OperationResult operationResult) {
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
                frame.getRevertReason().get().toString()
        ));

    }

    private Optional<Bytes[]> captureMemory(final MessageFrame frame) {
        final Bytes[] memoryContents = new Bytes[frame.memoryWordSize()];
        for (int i = 0; i < memoryContents.length; i++) {
            memoryContents[i] = frame.readMemory(i * 32L, 32);
        }
        return Optional.of(memoryContents);
    }

    private Optional<Bytes[]> captureStack(final MessageFrame frame) {
        final Bytes[] stackContents = new Bytes[frame.stackSize()];
        for (int i = 0; i < stackContents.length; i++) {
            // Record stack contents in reverse
            stackContents[i] = frame.getStackItem(stackContents.length - i - 1);
        }
        return Optional.of(stackContents);
    }

    private Optional<Map<UInt256, UInt256>> captureStorage(final MessageFrame frame) {
        try {
            final Map<UInt256, UInt256> storageContents =
                    new TreeMap<>(
                            frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getUpdatedStorage());

            return Optional.of(storageContents);
        } catch (final ModificationNotAllowedException e) {
            return Optional.of(new TreeMap<>());
        }
    }

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
                frame.getRevertReason().get().toString()
        ));
    }
}
