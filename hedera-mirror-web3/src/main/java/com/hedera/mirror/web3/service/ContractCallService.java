package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ERROR;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.BaseCallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

@Named
@CustomLog
public class ContractCallService {

    static final String GAS_LIMIT_METRIC = "hedera.mirror.web3.call.gas.limit";
    static final String GAS_USED_METRIC = "hedera.mirror.web3.call.gas.used";

    protected final RecordFileService recordFileService;
    protected final Store store;

    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final ThrottleProperties throttleProperties;
    private final Bucket gasLimitBucket;
    private final Meter.MeterProvider<Counter> gasLimitCounter;
    private final Meter.MeterProvider<Counter> gasUsedCounter;

    public ContractCallService(
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            RecordFileService recordFileService,
            Store store,
            MeterRegistry meterRegistry
    ) {
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.gasLimitBucket = gasLimitBucket;
        this.throttleProperties = throttleProperties;
        this.recordFileService = recordFileService;
        this.store = store;
        this.gasLimitCounter = Counter.builder(GAS_LIMIT_METRIC)
                .description("The amount of gas limit sent in the request")
                .withRegistry(meterRegistry);
        this.gasUsedCounter = Counter.builder(GAS_USED_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
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
            updateGasUsedMetric(ERROR, txnResult.getGasUsed(), 1);
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            throw new MirrorEvmTransactionException(getStatusOrDefault(txnResult), detail, revertReason.toHexString());
        } else {
            updateGasUsedMetric(type, txnResult.getGasUsed(), 1);
        }
    }

    protected void updateGasUsedMetric(final BaseCallServiceParameters.CallType callType, final long gasUsed, final int iterations) {
        gasUsedCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }

    protected void updateGasLimitMetric(final BaseCallServiceParameters.CallType callType, final long gasLimit) {
        gasLimitCounter
                .withTags("type", callType.toString())
                .increment(gasLimit);
    }
}
