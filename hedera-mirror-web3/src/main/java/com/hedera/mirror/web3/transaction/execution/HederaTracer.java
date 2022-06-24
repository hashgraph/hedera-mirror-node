package com.hedera.mirror.web3.transaction.execution;

import java.util.Optional;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Custom {@link OperationTracer} that populates exceptional halt reasons in the {@link MessageFrame}
 */
public class HederaTracer implements OperationTracer {

    @Override
    public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
        executeOperation.execute();
    }

    @Override
    public void traceAccountCreationResult(
            final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
        frame.setExceptionalHaltReason(haltReason);
    }
}
