package com.hedera.mirror.web3.service;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.BaseCallServiceParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import jakarta.inject.Named;

import static org.apache.logging.log4j.util.Strings.EMPTY;

@Named
public class BaseService {
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final Bucket gasLimitBucket;
    private final ThrottleProperties throttleProperties;

    public BaseService(
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties
    ) {
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.gasLimitBucket = gasLimitBucket;
        this.throttleProperties = throttleProperties;
    }
    public HederaEvmTransactionProcessingResult doProcessCall(BaseCallServiceParameters params,
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
}
