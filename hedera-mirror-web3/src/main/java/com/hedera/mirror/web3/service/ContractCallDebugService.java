package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.service.model.ContractCallDebugServiceParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_METRIC;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;

@CustomLog
@Named
public class ContractCallDebugService extends BaseService {

    private final ContractActionService contractActionService;
    private final RecordFileService recordFileService;
    private final Store store;
    private final Meter.MeterProvider<Counter> gasCounter;

    public ContractCallDebugService(
            ContractActionService contractActionService,
            RecordFileService recordFileService,
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry
    ) {
        super(mirrorEvmTxProcessor, gasLimitBucket, throttleProperties);
        this.contractActionService = contractActionService;
        this.recordFileService = recordFileService;
        this.store = store;
        this.gasCounter = Counter.builder(GAS_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);;
    }


    public OpcodesProcessingResult processOpcodeCall(final ContractCallDebugServiceParameters params,
                                                     final OpcodeTracerOptions opcodeTracerOptions,
                                                     final TransactionIdOrHashParameter transactionIdOrHash) {
        return ContractCallContext.run(ctx -> {
            ctx.setOpcodeTracerOptions(opcodeTracerOptions);
            List<ContractAction> contractActions = contractActionService.findFromTransaction(transactionIdOrHash, params);
            ctx.setContractActions(contractActions);
            final var ethCallTxnResult = callContract(params, TracerType.OPCODE, ctx);
            validateResult(ethCallTxnResult, params.getCallType());
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(ethCallTxnResult)
                    .opcodes(ctx.getOpcodes())
                    .build();
        });
    }

    private void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallServiceParameters.CallType type) {
        if (!txnResult.isSuccessful()) {
            updateGasMetric(ERROR, txnResult.getGasUsed(), 1);
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            if (type == ETH_DEBUG_TRACE_TRANSACTION) {
                log.warn("Transaction failed with status: {}, detail: {}, revertReason: {}",
                        getStatusOrDefault(txnResult), detail, revertReason.toHexString());
            } else {
                throw new MirrorEvmTransactionException(getStatusOrDefault(txnResult), detail, revertReason.toHexString());
            }
        } else {
            updateGasMetric(type, txnResult.getGasUsed(), 1);
        }
    }

    private void updateGasMetric(final ContractCallDebugServiceParameters.CallType callType, final long gasUsed, final int iterations) {
        gasCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
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
