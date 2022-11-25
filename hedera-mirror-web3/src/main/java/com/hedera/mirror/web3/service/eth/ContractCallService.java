package com.hedera.mirror.web3.service.eth;

import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.service.models.CallBody;
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
        return
                mirrorEvmTxProcessor.execute(
                        body.getSender(),
                        body.getReceiver(),
                        body.getProvidedGasLimit(),
                        body.getValue(),
                        body.getCallData());
    }
}
