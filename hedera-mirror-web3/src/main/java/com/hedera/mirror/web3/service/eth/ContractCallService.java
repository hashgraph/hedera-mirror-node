package com.hedera.mirror.web3.service.eth;

import static com.hedera.mirror.web3.util.ResponseCodeUtil.getStatusOrDefault;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallBody;
import com.hedera.services.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@Named
@RequiredArgsConstructor
public class ContractCallService {
    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessor;

    public String processCall(CallBody body) {
        final var processResult = doProcessCall(body);
        return processResult.getOutput().toHexString();
    }

    private HederaEvmTransactionProcessingResult doProcessCall(CallBody body) {
        final var processingResult =
                mirrorEvmTxProcessor.execute(
                        body.getSender(),
                        body.getReceiver(),
                        body.getProvidedGasLimit(),
                        body.getValue(),
                        body.getCallData());

        final var status = getStatusOrDefault(processingResult);
        if (status != SUCCESS) {
            throw new InvalidTransactionException(status);
        }

        return processingResult;
    }
}
