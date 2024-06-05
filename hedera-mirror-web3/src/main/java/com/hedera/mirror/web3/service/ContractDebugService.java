package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;

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

    @Override
    protected void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallType type) {
        try {
            super.validateResult(txnResult, type);
        } catch (MirrorEvmTransactionException e) {
            log.warn("Transaction failed with status: {}, detail: {}, revertReason: {}",
                    getStatusOrDefault(txnResult), e.getDetail(), e.getData());
        }
    }

    public OpcodesProcessingResult processOpcodeCall(final ContractDebugParameters params,
                                                     final OpcodeTracerOptions opcodeTracerOptions) {
        return ContractCallContext.run(ctx -> {
            ctx.setOpcodeTracerOptions(opcodeTracerOptions);
            List<ContractAction> contractActions = contractActionRepository.findAllByConsensusTimestamp(params.getConsensusTimestamp());
            ctx.setContractActions(contractActions);
            final var ethCallTxnResult = callContract(params, ctx);
            validateResult(ethCallTxnResult, params.getCallType());
            return new OpcodesProcessingResult(ethCallTxnResult, ctx.getOpcodes());
        });
    }
}
