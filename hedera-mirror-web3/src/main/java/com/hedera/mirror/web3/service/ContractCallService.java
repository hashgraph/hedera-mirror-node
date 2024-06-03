package com.hedera.mirror.web3.service;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.BaseCallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.ContractCallDebugServiceParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;
import static com.hedera.mirror.web3.service.ContractExecutionService.GAS_METRIC;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static org.apache.logging.log4j.util.Strings.EMPTY;

@Named
@CustomLog
public class ContractCallService {
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final Bucket gasLimitBucket;
    private final ThrottleProperties throttleProperties;
    private final Meter.MeterProvider<Counter> gasCounter;

    public ContractCallService(
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry
    ) {
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.gasLimitBucket = gasLimitBucket;
        this.throttleProperties = throttleProperties;
        this.gasCounter = Counter.builder(GAS_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);;
    }
    protected HederaEvmTransactionProcessingResult doProcessCall(BaseCallServiceParameters params,
                                                              long estimatedGas,
                                                              boolean restoreGasToThrottleBucket,
                                                              TracerType tracerType,
                                                              ContractCallContext ctx) throws MirrorEvmTransactionException {
        try {
            var result = mirrorEvmTxProcessor.execute(params, estimatedGas, tracerType, ctx);
            if (!restoreGasToThrottleBucket) {
                return result;
            }

            restoreGasToBucket(result, params.getGas());
            return result;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new MirrorEvmTransactionException(e.getMessage(), EMPTY, EMPTY);
        }
    }

    private void restoreGasToBucket(HederaEvmTransactionProcessingResult result, long gasLimit) {
        // If the transaction fails, gasUsed is equal to gasLimit, so restore the configured refund percent
        // of the gasLimit value back in the bucket.
        final var gasLimitToRestoreBaseline = (long) (gasLimit * throttleProperties.getGasLimitRefundPercent() / 100f);
        if (!result.isSuccessful() && gasLimit == result.getGasUsed()) {
            gasLimitBucket.addTokens(gasLimitToRestoreBaseline);
        } else {
            // The transaction was successful or reverted, so restore the remaining gas back in the bucket or
            // the configured refund percent of the gasLimit value back in the bucket - whichever is lower.
            gasLimitBucket.addTokens(Math.min(gasLimit - result.getGasUsed(), gasLimitToRestoreBaseline));
        }
    }

    protected void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallServiceParameters.CallType type) {
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

    protected void updateGasMetric(final ContractCallDebugServiceParameters.CallType callType, final long gasUsed, final int iterations) {
        gasCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }
}
