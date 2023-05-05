package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.mirror.web3.evm.properties.TracingProperties;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

import javax.inject.Named;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;

@CustomLog
@Named
@RequiredArgsConstructor
public class MirrorOperationTracer implements HederaEvmOperationTracer {

    private final TracingProperties tracingProperties;

    @Override
    public void tracePostExecution(final MessageFrame currentFrame, final Operation.OperationResult operationResult) {
        if(tracingProperties.isEnabled()) {
            if(!tracingProperties.getStatus().isEmpty() && !tracingProperties.getStatus().contains(currentFrame.getState().name())) {
                return;
            }

            final var recipientAddress = currentFrame.getRecipientAddress();
            final var recipientNum = entityIdFromEvmAddress(recipientAddress);

            if(!tracingProperties.getContract().isEmpty() && !tracingProperties.getContract().contains(recipientNum)) {
                return;
            }

            //TODO FINISH FULL LOG
            final var inputData = new String(currentFrame.getInputData() != null ? currentFrame.getInputData().toArray() : new byte[0]);
            log.debug("MessageFrame {} has input data {}, ...", currentFrame.toString(), inputData);

        }
    }

}
