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

import static com.hedera.mirror.web3.convert.BytesDecoder.decodeEvmRevertReasonBytesToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@Named
public class ContractCallService {
    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade;
    private final Map<CallType, Counter> gasPerSecondMetricMap;

    public ContractCallService(final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade,
                               final MeterRegistry meterRegistry) {
        this.mirrorEvmTxProcessorFacade = mirrorEvmTxProcessorFacade;

        final var gasPerSecondMetricEnumMap = new EnumMap<CallType, Counter>(CallType.class);
        Arrays.stream(CallType.values()).forEach(type ->
                gasPerSecondMetricEnumMap.put(type, Counter.builder("hedera.mirror.web3.call.gas")
                        .description("The amount of gas consumed by the EVM")
                        .tag("type", type.toString())
                        .register(meterRegistry)));

        gasPerSecondMetricMap = Collections.unmodifiableMap(gasPerSecondMetricEnumMap);
    }

    public String processCall(final CallServiceParameters params) {
        if (params.isEstimate()) {
            return estimateGas(params);
        }

        final var ethCallTxnResult = processEthTxn(params);
        validateTxnResult(ethCallTxnResult, params.getCallType());

        final var callResult = ethCallTxnResult.getOutput() != null
                ? ethCallTxnResult.getOutput() : Bytes.EMPTY;

        return callResult.toHexString();
    }

    private String estimateGas(final CallServiceParameters params) {
        final var providedGasLimit = params.getGas();
        long lo = 0;
        long hi = providedGasLimit;

        int minDiffBetweenIterations = 1200;
        long prevGasLimit = providedGasLimit;
        HederaEvmTransactionProcessingResult txnResult;
        do {
            // Take a guess at the gas, and check transaction validity
            long mid = (hi + lo) / 2;
            params.setGas(mid);
            txnResult = processEthTxn(params);

            boolean err = !txnResult.isSuccessful() || txnResult.getGasUsed() < 0;
            long gasUsed = err ? providedGasLimit : txnResult.getGasUsed();

            if (err || gasUsed == 0) {
                lo = mid;
            } else {
                hi = mid;
                // Perf improvement: stop the binary search when the difference in gas between two iterations
                // is less then `minDiffBetweenIterations`. Doing this cuts the number of iterations from 23
                // to 12, with only a ~1000 gas loss in precision.
                if (Math.abs(prevGasLimit - mid) < minDiffBetweenIterations) {
                    lo = hi;
                }
            }
            prevGasLimit = mid;
        } while (lo + 1 < hi);
        validateTxnResult(txnResult, params.getCallType());

        return Long.toHexString(hi);
    }

    private HederaEvmTransactionProcessingResult processEthTxn(final CallServiceParameters params) {
        HederaEvmTransactionProcessingResult txnResult;

        try {
            txnResult =
                    mirrorEvmTxProcessorFacade.execute(
                            params.getSender(),
                            params.getReceiver(),
                            params.getGas(),
                            params.getValue(),
                            params.getCallData(),
                            params.isStatic());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage(), StringUtils.EMPTY);
        }
        return txnResult;
    }

    private void validateTxnResult(final HederaEvmTransactionProcessingResult txnResult,
                                   final CallType type) {
        if (!txnResult.isSuccessful()) {
            onComplete(CallType.ERROR, txnResult);

            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            throw new InvalidTransactionException(getStatusOrDefault(txnResult),
                    decodeEvmRevertReasonBytesToReadableMessage(revertReason));
        } else {
            onComplete(type, txnResult);
        }
    }

    private void onComplete(final CallType callType, final HederaEvmTransactionProcessingResult result) {
        final var counter = gasPerSecondMetricMap.get(callType);
        counter.increment(result.getGasUsed());
    }
}
