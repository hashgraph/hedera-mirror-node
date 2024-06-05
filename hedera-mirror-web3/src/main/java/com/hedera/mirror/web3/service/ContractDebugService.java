package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.ContractCallDebugServiceParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
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
        super(mirrorEvmTxProcessor, gasLimitBucket, throttleProperties, recordFileService, store, meterRegistry);
        this.contractActionRepository = contractActionRepository;
    }

    @Override
    protected void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallServiceParameters.CallType type) {
        try {
            super.validateResult(txnResult, type);
        } catch (MirrorEvmTransactionException e) {
            log.warn("Transaction failed with status: {}, detail: {}, revertReason: {}",
                    getStatusOrDefault(txnResult), e.getDetail(), e.getData());
        }
    }

    public OpcodesProcessingResult processOpcodeCall(final ContractCallDebugServiceParameters params,
                                                     final OpcodeTracerOptions opcodeTracerOptions) {
        return ContractCallContext.run(ctx -> {
            ctx.setOpcodeTracerOptions(opcodeTracerOptions);
            ctx.setContractActions(contractActionRepository.findAllByConsensusTimestamp(params.getConsensusTimestamp()));
            final var ethCallTxnResult = callContract(params, TracerType.OPCODE, ctx);
            validateResult(ethCallTxnResult, params.getCallType());
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(ethCallTxnResult)
                    .opcodes(ctx.getOpcodes())
                    .build();
        });
    }

    /**
     * This method is responsible for calling a smart contract function. The method is divided into two main parts:
     * <p>
     *     1. If the call is historical, the method retrieves the corresponding record file and initializes
     *     the contract call context with the historical state. The method then proceeds to call the contract.
     * </p>
     * <p>
     *     2. If the call is not historical, the method initializes the contract call context with the current state
     *     and proceeds to call the contract.
     * </p>
     *
     * @param params the call service parameters
     * @param tracerType the type of tracer to use
     * @param ctx the contract call context
     * @return {@link HederaEvmTransactionProcessingResult} of the contract call
     * @throws MirrorEvmTransactionException if any pre-checks
     * fail with {@link IllegalStateException} or {@link IllegalArgumentException}
     */
    private HederaEvmTransactionProcessingResult callContract(ContractCallDebugServiceParameters params,
                                                              TracerType tracerType,
                                                              ContractCallContext ctx) throws MirrorEvmTransactionException {
        // if we have historical call, then set the corresponding record file in the context
        if (params.getBlock() != BlockType.LATEST) {
            ctx.setRecordFile(recordFileService
                    .findByBlockType(params.getBlock())
                    .orElseThrow(BlockNumberNotFoundException::new));
        }
        // initializes the stack frame with the current state or historical state (if the call is historical)
        ctx.initializeStackFrames(store.getStackedStateFrames());
        return doProcessCall(params, params.getGas(), true, tracerType, ctx);
    }
}
