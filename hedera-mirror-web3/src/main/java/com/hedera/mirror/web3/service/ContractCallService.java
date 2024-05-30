/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;

import java.util.Objects;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

@CustomLog
@Named
public class ContractCallService extends BaseService {

    static final String GAS_METRIC = "hedera.mirror.web3.call.gas";

    private final BinaryGasEstimator binaryGasEstimator;
    private final MeterProvider<Counter> gasCounter;
    private final Store store;
    private final RecordFileService recordFileService;

    public ContractCallService(
            MeterRegistry meterRegistry,
            BinaryGasEstimator binaryGasEstimator,
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            RecordFileService recordFileService,
            ThrottleProperties throttleProperties,
            Bucket gasLimitBucket) {
        super(mirrorEvmTxProcessor, gasLimitBucket, throttleProperties);
        this.binaryGasEstimator = binaryGasEstimator;
        this.gasCounter = Counter.builder(GAS_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
        this.store = store;
        this.recordFileService = recordFileService;
    }

    public String processCall(final CallServiceParameters params) {
        return ContractCallContext.run(ctx -> {
            var stopwatch = Stopwatch.createStarted();
            var stringResult = "";

            try {
                Bytes result;
                if (params.isEstimate()) {
                    // eth_estimateGas initialization - historical timestamp is Optional.empty()
                    ctx.initializeStackFrames(store.getStackedStateFrames());
                    result = estimateGas(params, ctx);
                } else {
                    final var ethCallTxnResult = callContract(params, TracerType.OPERATION, ctx);

                    validateResult(ethCallTxnResult, params.getCallType());

                    result = Objects.requireNonNullElse(ethCallTxnResult.getOutput(), Bytes.EMPTY);
                }

                stringResult = result.toHexString();
            } finally {
                log.debug("Processed request {} in {}: {}", params, stopwatch, stringResult);
            }

            return stringResult;
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
    private HederaEvmTransactionProcessingResult callContract(CallServiceParameters params,
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
    private Bytes estimateGas(final CallServiceParameters params, final ContractCallContext ctx) {
        final var processingResult = doProcessCall(params, params.getGas(), true, TracerType.OPERATION, ctx);
        validateResult(processingResult, CallType.ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall);
        }

        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateGasMetric(CallType.ETH_ESTIMATE_GAS, totalGas, iterations),
                gas -> doProcessCall(params, gas, false, TracerType.OPERATION, ctx),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas);
    }



    private void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallType type) {
        if (!txnResult.isSuccessful()) {
            updateGasMetric(CallType.ERROR, txnResult.getGasUsed(), 1);
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            if (type == CallType.ETH_DEBUG_TRACE_TRANSACTION) {
                log.warn("Transaction failed with status: {}, detail: {}, revertReason: {}",
                        getStatusOrDefault(txnResult), detail, revertReason.toHexString());
            } else {
                throw new MirrorEvmTransactionException(getStatusOrDefault(txnResult), detail, revertReason.toHexString());
            }
        } else {
            updateGasMetric(type, txnResult.getGasUsed(), 1);
        }
    }

    private void updateGasMetric(final CallType callType, final long gasUsed, final int iterations) {
        gasCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }
}
