package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.StorageEntry;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.ArrayList;
import java.util.List;
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
        opcodes.add(new Opcode(
                frame.getPC(),
                frame.getCurrentOperation().getName(),
                frame.getRemainingGas(),
                operationResult.getGasCost(),
                frame.getDepth(),
                IntStream.range(0, frame.stackSize())
                        .mapToObj(frame::getStackItem)
                        .toList(),
                frame.getMaybeUpdatedMemory()
                        .map(memoryEntry -> List.of(memoryEntry.getValue()))
                        .orElse(new ArrayList<>()),
                frame.getMaybeUpdatedStorage()
                        .stream()
                        .collect(Collectors.toMap(
                                storageEntry -> storageEntry.getOffset().toString(),
                                StorageEntry::getValue
                        )),
                frame.getRevertReason().toString()
        ));

    }

    public void tracePrecompileCall(
            final MessageFrame frame, final long gasRequirement, final Bytes output) {
        //To be implemented
    }
}
