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
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTxProcessor;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

@CustomLog
@Named
public class ContractCallService {

    static final String GAS_METRIC = "hedera.mirror.web3.call.gas";

    private final BinaryGasEstimator binaryGasEstimator;
    private final MeterProvider<Counter> gasCounter;
    private final Store store;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileService recordFileService;
    private final ContractActionService contractActionService;
    private final TransactionService transactionService;
    private final EthereumTransactionService ethereumTransactionService;
    private final ThrottleProperties throttleProperties;
    private final Bucket gasLimitBucket;

    public ContractCallService(
            MeterRegistry meterRegistry,
            BinaryGasEstimator binaryGasEstimator,
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            RecordFileService recordFileService,
            ContractActionService contractActionService,
            TransactionService transactionService,
            EthereumTransactionService ethereumTransactionService,
            ThrottleProperties throttleProperties,
            Bucket gasLimitBucket) {
        this.binaryGasEstimator = binaryGasEstimator;
        this.gasCounter = Counter.builder(GAS_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
        this.store = store;
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.recordFileService = recordFileService;
        this.transactionService = transactionService;
        this.contractActionService = contractActionService;
        this.ethereumTransactionService = ethereumTransactionService;
        this.throttleProperties = throttleProperties;
        this.gasLimitBucket = gasLimitBucket;
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
                    final var ethCallTxnResult = getCallTxnResult(params, HederaEvmTxProcessor.TracerType.OPERATION, ctx);

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

    public OpcodesProcessingResult processOpcodeCall(final CallServiceParameters params,
                                                     final OpcodeTracerOptions opcodeTracerOptions,
                                                     @Nullable final TransactionIdOrHashParameter transactionIdOrHash) {
        return ContractCallContext.run(ctx -> {
            if (transactionIdOrHash != null && transactionIdOrHash.isValid()) {
                List<ContractAction> contractActions = getContractAction(transactionIdOrHash);
                ctx.setContractActions(contractActions);
            }
            ctx.setOpcodeTracerOptions(opcodeTracerOptions);
            final var ethCallTxnResult = getCallTxnResult(params, HederaEvmTxProcessor.TracerType.OPCODE, ctx);
            validateResult(ethCallTxnResult, params.getCallType());
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(ethCallTxnResult)
                    .opcodes(ctx.getOpcodes())
                    .build();
        });
    }

    @SneakyThrows
    public List<ContractAction> getContractAction(@NonNull @Valid TransactionIdOrHashParameter transactionIdOrHash) {
        Assert.isTrue(transactionIdOrHash.isValid(), "Invalid transactionIdOrHash");

        Optional<Long> consensusTimestamp;
        if (transactionIdOrHash.isHash()) {
            consensusTimestamp = ethereumTransactionService
                    .findByHash(transactionIdOrHash.hash().toByteArray())
                    .map(EthereumTransaction::getConsensusTimestamp);
        } else {
            consensusTimestamp = transactionService
                    .findByTransactionId(transactionIdOrHash.transactionID())
                    .map(Transaction::getConsensusTimestamp);
        }

        return consensusTimestamp
                .map(contractActionService::findContractActionByConsensusTimestamp)
                .orElseThrow(() -> new IllegalArgumentException("Contract actions for transaction not found"));
    }

    private HederaEvmTransactionProcessingResult getCallTxnResult(CallServiceParameters params,
                                                                  HederaEvmTxProcessor.TracerType tracerType,
                                                                  ContractCallContext ctx) throws MirrorEvmTransactionException {
        // if we have historical call then set corresponding file record
        if (params.getBlock() != BlockType.LATEST) {
            ctx.setRecordFile(recordFileService
                    .findRecordFileByBlock(params.getBlock())
                    .orElseThrow(BlockNumberNotFoundException::new));
        }
        // eth_call initialization - historical timestamp is Optional.of(recordFile.getConsensusEnd())
        // if the call is historical
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
        final var processingResult = doProcessCall(params, params.getGas(), true, HederaEvmTxProcessor.TracerType.OPERATION, ctx);
        validateResult(processingResult, ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall);
        }

        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateGasMetric(ETH_ESTIMATE_GAS, totalGas, iterations),
                gas -> doProcessCall(params, gas, false, HederaEvmTxProcessor.TracerType.OPERATION, ctx),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas);
    }

    private HederaEvmTransactionProcessingResult doProcessCall(CallServiceParameters params,
                                                               long estimatedGas,
                                                               boolean restoreGasToThrottleBucket,
                                                               HederaEvmTxProcessor.TracerType tracerType,
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

    private void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallType type) {
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

    private void updateGasMetric(final CallType callType, final long gasUsed, final int iterations) {
        gasCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }
}
