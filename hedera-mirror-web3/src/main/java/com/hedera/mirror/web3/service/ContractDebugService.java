package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.CustomLog;

import java.util.List;

import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

@CustomLog
@Named
public class ContractDebugService extends ContractCallService {
    private final ContractActionRepository contractActionRepository;

    public ContractDebugService(
            ContractActionRepository contractActionRepository,
            RecordFileService recordFileService,
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry
    ) {
        super(mirrorEvmTxProcessor, gasLimitBucket, throttleProperties, meterRegistry, recordFileService, store);
        this.contractActionRepository = contractActionRepository;
    }

    public OpcodesProcessingResult processOpcodeCall(final ContractDebugParameters params,
                                                     final OpcodeTracerOptions opcodeTracerOptions) {
        return ContractCallContext.run(ctx -> {
            ctx.setOpcodeTracerOptions(opcodeTracerOptions);
            List<ContractAction> contractActions = contractActionRepository.findAllByConsensusTimestamp(params.getConsensusTimestamp());
            ctx.setContractActions(contractActions);
            final var ethCallTxnResult = callContract(params, TracerType.OPCODE, ctx);
            validateResult(ethCallTxnResult, params.getCallType());
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(ethCallTxnResult)
                    .opcodes(ctx.getOpcodes())
                    .build();
        });
    }


    @Override
    protected void validateResult() {
        try {
            super.validateResult();
        } catch (MirrorEvmTransactionException e) {
                log.warn("Transaction failed with status: {}, detail: {}, revertReason: {}",
                        getStatusOrDefault(txnResult), detail, revertReason.toHexString());
        }
    }
}
