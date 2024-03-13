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
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

@CustomLog
@Named
public class ContractCallService {

    static final String GAS_METRIC = "hedera.mirror.web3.call.gas";
    private static final String UNKNOWN_BLOCK_NUMBER = "Unknown block number";

    private final BinaryGasEstimator binaryGasEstimator;
    private final MeterProvider<Counter> gasCounter;
    private final Store store;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileRepository recordFileRepository;

    public ContractCallService(
            MeterRegistry meterRegistry,
            BinaryGasEstimator binaryGasEstimator,
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            RecordFileRepository recordFileRepository) {
        this.binaryGasEstimator = binaryGasEstimator;
        this.gasCounter = Counter.builder(GAS_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
        this.store = store;
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.recordFileRepository = recordFileRepository;
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
                    result = estimateGas(params);
                } else {
                    BlockType block = params.getBlock();
                    // if we have historical call then set corresponding file record
                    if (block != BlockType.LATEST) {
                        var recordFileOptional =
                                findRecordFileByBlock(block).orElseThrow(BlockNumberNotFoundException::new);
                        ctx.setRecordFile(recordFileOptional);
                    }
                    // eth_call initialization - historical timestamp is Optional.of(recordFile.getConsensusEnd())
                    // if the call is historical
                    ctx.initializeStackFrames(store.getStackedStateFrames());
                    final var ethCallTxnResult = doProcessCall(params, params.getGas());

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
    private Bytes estimateGas(final CallServiceParameters params) {
        final var processingResult = doProcessCall(params, params.getGas());
        validateResult(processingResult, ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall);
        }

        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateGasMetric(ETH_ESTIMATE_GAS, totalGas, iterations),
                gas -> doProcessCall(params, gas),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas);
    }

    private HederaEvmTransactionProcessingResult doProcessCall(CallServiceParameters params, long estimatedGas) {
        try {
            return mirrorEvmTxProcessor.execute(params, estimatedGas);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new MirrorEvmTransactionException(e.getMessage(), EMPTY, EMPTY);
        }
    }

    private void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallType type) {
        if (!txnResult.isSuccessful()) {
            updateGasMetric(ERROR, txnResult.getGasUsed(), 1);
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            throw new MirrorEvmTransactionException(getStatusOrDefault(txnResult), detail, revertReason.toHexString());
        } else {
            updateGasMetric(type, txnResult.getGasUsed(), 1);
        }
    }

    private void updateGasMetric(final CallType callType, final long gasUsed, final int iterations) {
        gasCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }

    private Optional<RecordFile> findRecordFileByBlock(BlockType block) {
        if (block == BlockType.EARLIEST) {
            return recordFileRepository.findEarliest();
        }

        long latestBlock = recordFileRepository
                .findLatestIndex()
                .orElseThrow(() -> new BlockNumberOutOfRangeException(UNKNOWN_BLOCK_NUMBER));

        if (block.number() > latestBlock) {
            throw new BlockNumberOutOfRangeException(UNKNOWN_BLOCK_NUMBER);
        }
        return recordFileRepository.findByIndex(block.number());
    }
}
