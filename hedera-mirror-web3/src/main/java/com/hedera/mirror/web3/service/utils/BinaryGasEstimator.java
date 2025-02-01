/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service.utils;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import jakarta.inject.Named;
import java.util.function.LongFunction;
import java.util.function.ObjIntConsumer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@RequiredArgsConstructor
@Named
public class BinaryGasEstimator {
    private final MirrorNodeEvmProperties properties;

    public long search(
            final ObjIntConsumer<Long> metricUpdater,
            final LongFunction<HederaEvmTransactionProcessingResult> call,
            long lo,
            long hi) {
        long prevGasLimit = lo;
        int iterationsMade = 0;
        long totalGasUsed = 0;

        // Now that we also support gas estimates for precompile calls, the default threshold is too low, since
        // it does not take into account the minimum threshold of 5% higher estimate than the actual gas used.
        // The default value is working with some calls but that is not the case for precompile calls which have higher
        // gas consumption.
        // Configurable tolerance of 10% over 5% is used, since the algorithm fails when using 5%, producing too narrow
        // threshold. Adjust via estimateGasIterationThresholdPercent value.
        final long estimateIterationThreshold = (long) (lo * properties.getEstimateGasIterationThresholdPercent());

        ContractCallContext contractCallContext = ContractCallContext.get();
        while (lo + 1 < hi && iterationsMade < properties.getMaxGasEstimateRetriesCount()) {
            contractCallContext.reset();

            long mid = (hi + lo) / 2;

            // If modularizedServices is true - we call the safeCall function that handles if an exception is thrown
            HederaEvmTransactionProcessingResult transactionResult =
                    properties.isModularizedServices() ? safeCall(mid, call) : call.apply(mid);

            iterationsMade++;

            boolean err = transactionResult == null
                    || !transactionResult.isSuccessful()
                    || transactionResult.getGasUsed() < 0;
            long gasUsed = err ? prevGasLimit : transactionResult.getGasUsed();
            totalGasUsed += gasUsed;
            if (err || gasUsed == 0) {
                lo = mid;
            } else {
                hi = mid;
                if (Math.abs(prevGasLimit - mid) < estimateIterationThreshold) {
                    lo = hi;
                }
            }
            prevGasLimit = mid;
        }

        metricUpdater.accept(totalGasUsed, iterationsMade);
        return hi;
    }

    // This method is needed because within the modularized services if the contract call fails an exception is thrown
    // instead of transaction result with 'failed' status which will result in a failing test. This way we handle the
    // exception and return estimated gas
    private HederaEvmTransactionProcessingResult safeCall(
            long mid, LongFunction<HederaEvmTransactionProcessingResult> call) {
        try {
            return call.apply(mid);
        } catch (Exception ignored) {
            log.info("Exception while calling contract for gas estimation");
            return null;
        }
    }
}
