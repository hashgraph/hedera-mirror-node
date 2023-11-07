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

import static com.hedera.mirror.web3.common.ContractCallContext.init;
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
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

@CustomLog
@Named
@RequiredArgsConstructor
public class ContractCallService {

    private static final String UNKNOWN_BLOCK_NUMBER = "Unknown block number";

    private final Counter.Builder gasCounter =
            Counter.builder("hedera.mirror.web3.call.gas").description("The amount of gas consumed by the EVM");
    private final MeterRegistry meterRegistry;
    private final BinaryGasEstimator binaryGasEstimator;
    private final Store store;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileRepository recordFileRepository;

    @SuppressWarnings("try")
    public String processCall(final CallServiceParameters params) {
        var stopwatch = Stopwatch.createStarted();
        var stringResult = "";

        try (ContractCallContext ctx = init(store.getStackedStateFrames())) {
            Bytes result;
            if (params.isEstimate()) {
                result = estimateGas(params);
            } else {
                BlockType block = params.getBlock();

                if (block != BlockType.LATEST) {
                    Optional<RecordFile> recordFileOptional = findRecordFileByBlock(block);
                    if (recordFileOptional.isPresent()) {
                        ctx.setBlockTimestamp(recordFileOptional.get().getConsensusEnd());
                    } else {
                        // return default empty result when the block passed is valid but not found in DB
                        return Bytes.EMPTY.toHexString();
                    }
                }

                final var ethCallTxnResult = doProcessCall(params, params.getGas(), false);

                validateResult(ethCallTxnResult, params.getCallType());

                result = Objects.requireNonNullElse(ethCallTxnResult.getOutput(), Bytes.EMPTY);
            }
            return result.toHexString();
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
    private Bytes estimateGas(final CallServiceParameters params) {
        HederaEvmTransactionProcessingResult processingResult = doProcessCall(params, params.getGas(), true);
        validateResult(processingResult, ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall);
        }

        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateGasMetric(ETH_ESTIMATE_GAS, totalGas, iterations),
                gas -> doProcessCall(params, gas, true),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas);
    }

    private HederaEvmTransactionProcessingResult doProcessCall(
            final CallServiceParameters params, final long estimatedGas) {
        HederaEvmTransactionProcessingResult transactionResult;
        try {
            transactionResult = mirrorEvmTxProcessor.execute(params, estimatedGas);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new MirrorEvmTransactionException(e.getMessage(), EMPTY, EMPTY);
        }
        return transactionResult;
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
                .tag("type", callType.toString())
                .tag("iteration", String.valueOf(iterations))
                .register(meterRegistry)
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
