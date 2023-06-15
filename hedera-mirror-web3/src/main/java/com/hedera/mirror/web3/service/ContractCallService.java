/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Objects;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

@CustomLog
@Named
@RequiredArgsConstructor
public class ContractCallService {

    private final Counter.Builder gasCounter =
            Counter.builder("hedera.mirror.web3.call.gas").description("The amount of gas consumed by the EVM");
    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade;
    private final MeterRegistry meterRegistry;
    private final BinaryGasEstimator binaryGasEstimator;

    public String processCall(final CallServiceParameters params) {
        var stopwatch = Stopwatch.createStarted();
        var stringResult = "";

        try {
            if (params.isEstimate()) {
                stringResult = estimateGas(params);
                return stringResult;
            }

            final var ethCallTxnResult = doProcessCall(params, params.getGas());
            validateResult(ethCallTxnResult, params.getCallType());

            final var callResult = Objects.requireNonNullElse(ethCallTxnResult.getOutput(), Bytes.EMPTY);
            stringResult = callResult.toHexString();
            return stringResult;
        } finally {
            log.debug("Processed request {} in {}: {}", params, stopwatch, stringResult);
        }
    }

    /**
     * This method estimates the amount of gas required to execute a smart contract function. The estimation process
     * involves two steps:
     * <p>
     * 1. Firstly, a call is made with user inputted gas value (default and maximum value for this parameter is 15
     * million) to determine if the call estimation is possible. This step is intended to quickly identify any issues
     * that would prevent the estimation from succeeding.
     * <p>
     * 2. Finally, if the first step is successful, a binary search is initiated. The lower bound of the search is the
     * gas used in the first step, while the upper bound is the inputted gas parameter.
     */
    private String estimateGas(final CallServiceParameters params) {
        HederaEvmTransactionProcessingResult processingResult = doProcessCall(params, params.getGas());
        validateResult(processingResult, ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall).toHexString();
        }

        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateGasMetric(ETH_ESTIMATE_GAS, totalGas, iterations),
                gas -> doProcessCall(params, gas),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas).toHexString();
    }

    private HederaEvmTransactionProcessingResult doProcessCall(
            final CallServiceParameters params, final long estimatedGas) {
        HederaEvmTransactionProcessingResult transactionResult;
        try {
            transactionResult = mirrorEvmTxProcessorFacade.execute(
                    params.getSender(),
                    params.getReceiver(),
                    params.isEstimate() ? estimatedGas : params.getGas(),
                    params.getValue(),
                    params.getCallData(),
                    Instant.now(),
                    params.isStatic());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage(), EMPTY, EMPTY);
        }
        return transactionResult;
    }

    private void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallType type) {
        if (!txnResult.isSuccessful()) {
            updateGasMetric(ERROR, txnResult.getGasUsed(), 1);
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            throw new InvalidTransactionException(getStatusOrDefault(txnResult), detail, revertReason.toHexString());
        } else {
            updateGasMetric(type, txnResult.getGasUsed(), 1);
        }
    }

    private void updateGasMetric(final CallType callType, final long gasUsed, final int iterations) {
        gasCounter
                .tag("type", callType.toString())
                .tag("iteration", String.valueOf(iterations))
                .register(meterRegistry)
                .increment(gasUsed);
    }
}
