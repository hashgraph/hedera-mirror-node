package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import lombok.Getter;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

import java.lang.reflect.Array;
import java.util.ArrayList;

@Getter
public class OpcodeTracer implements HederaEvmOperationTracer {

    public ArrayList<Opcode> opcodes;
    @Override
    public void tracePostExecution(MessageFrame frame, Operation.OperationResult operationResult) {
        opcodes.add(new Opcode(
                    frame.getPC(),
                    frame.getCurrentOperation().getName(),
                    frame.getRemainingGas(),
                    operationResult.getGasCost(),
                    frame.getDepth()
//                    frame.peekReturnStack().toString(),
//                    frame.getMaybeUpdatedStorage().toString(),
//                    frame.getRevertReason().toString()
        ));

    }
}
