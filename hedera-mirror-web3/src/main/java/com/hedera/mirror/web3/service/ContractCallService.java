package com.hedera.mirror.web3.service;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Named;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@CustomLog
@Named
public class ContractCallService {

    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade;
    private final Map<CallType, Counter> gasPerSecondMetricMap;
    private final MirrorNodeEvmProperties properties;

    public ContractCallService(final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade,
                               final MeterRegistry meterRegistry, MirrorNodeEvmProperties properties) {
        this.mirrorEvmTxProcessorFacade = mirrorEvmTxProcessorFacade;
        this.properties = properties;

        final var gasPerSecondMetricEnumMap = new EnumMap<CallType, Counter>(CallType.class);
        Arrays.stream(CallType.values()).forEach(type ->
                gasPerSecondMetricEnumMap.put(type, Counter.builder("hedera.mirror.web3.call.gas")
                        .description("The amount of gas consumed by the EVM")
                        .tag("type", type.toString())
                        .register(meterRegistry)));

        gasPerSecondMetricMap = Collections.unmodifiableMap(gasPerSecondMetricEnumMap);
    }

    public String processCall(final CallServiceParameters params) {
        var stopwatch = Stopwatch.createStarted();
        String stringResult = "";
        try {
            if (params.isEstimate()) {
                stringResult = estimateGas(params);
                return stringResult;
            }
            final var ethCallTxnResult = doProcessCall(params, params.getGas());
            validateTxnResult(ethCallTxnResult, params.getCallType());

            final var callResult = ethCallTxnResult.getOutput() != null
                    ? ethCallTxnResult.getOutput() : Bytes.EMPTY;
            stringResult = callResult.toHexString();
            return stringResult;
        } finally {
            log.debug("Processed request {} in {}: {}", params, stopwatch, stringResult);
        }
    }

    /**
     * This method estimates the amount of gas required to execute a smart contract function.
     * The estimation process involves three steps:
     * 1. Firstly, a call is made with the maximum gas value of 15 million to determine if the call
     *    estimation is possible. This step is intended to quickly identify any issues that would
     *    prevent the estimation from succeeding.
     *
     * 2. Finally, if the first step is successful, a binary search is initiated.
     *    The lower bound of the search is the gas used in the first step, while the upper bound
     *    is half of the maximum gas value .
     */
    private String estimateGas(final CallServiceParameters params) {
        HederaEvmTransactionProcessingResult processingResult = doProcessCall(params, properties.getMaxGasToUseLimit());
        validateTxnResult(processingResult, ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();
        final var estimatedGas = binarySearch(params, gasUsedByInitialCall, properties.getMaxGasToUseLimit() / 2);

        return Long.toHexString(estimatedGas);
    }

    /**
     * Future work: move to another class and compare performance with interpolation algo.
     */
    private long binarySearch(final CallServiceParameters params, long lo, long hi) {
        long prevGasLimit = lo;
       do {
           long mid = (hi + lo) / 2 ;
           HederaEvmTransactionProcessingResult transactionResult = doProcessCall(params, mid);

           boolean err = !transactionResult.isSuccessful() || transactionResult.getGasUsed() < 0;
            long gasUsed = err ? prevGasLimit : transactionResult.getGasUsed();
            if (err || gasUsed == 0) {
                lo = mid;
            } else {
                hi = mid;
                if (Math.abs(prevGasLimit - mid) < properties.getDiffBetweenIterations()) {
                    lo = hi;
                }
            }
            prevGasLimit = mid;
        } while (lo + 1 < hi);
        return hi;
    }

    private HederaEvmTransactionProcessingResult doProcessCall(final CallServiceParameters params,
                                                               final long estimatedGas) {
        HederaEvmTransactionProcessingResult transactionResult;
        try {
            transactionResult =
                    mirrorEvmTxProcessorFacade.execute(
                            params.getSender(),
                            params.getReceiver(),
                            params.isEstimate() ? estimatedGas : params.getGas(),
                            params.getValue(),
                            params.getCallData(),
                            params.isStatic());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage(), EMPTY, EMPTY);
        }
        return transactionResult;
    }

    private void validateTxnResult(final HederaEvmTransactionProcessingResult txnResult,
                                   final CallType type) {
        if (!txnResult.isSuccessful()) {
            updateGasMetric(ERROR, txnResult);

            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            throw new InvalidTransactionException(getStatusOrDefault(txnResult),
                    maybeDecodeSolidityErrorStringToReadableMessage(revertReason), revertReason.toHexString());
        } else {
            updateGasMetric(type, txnResult);
        }
    }

    private void updateGasMetric(final CallType callType, final HederaEvmTransactionProcessingResult result) {
        final var counter = gasPerSecondMetricMap.get(callType);
        counter.increment(result.getGasUsed());
    }
}
