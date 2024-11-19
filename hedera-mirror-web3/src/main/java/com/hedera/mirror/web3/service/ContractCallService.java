/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.swirlds.state.State;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

@Named
@CustomLog
public abstract class ContractCallService {
    static final String GAS_LIMIT_METRIC = "hedera.mirror.web3.call.gas.limit";
    static final String GAS_USED_METRIC = "hedera.mirror.web3.call.gas.used";
    private final MeterProvider<Counter> gasLimitCounter;
    private final MeterProvider<Counter> gasUsedCounter;
    protected final Store store;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileService recordFileService;
    private final ThrottleProperties throttleProperties;
    private final Bucket gasLimitBucket;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final State mirrorNodeState;

    protected ContractCallService(
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry,
            RecordFileService recordFileService,
            Store store,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            State mirrorNodeState) {
        this.gasLimitCounter = Counter.builder(GAS_LIMIT_METRIC)
                .description("The amount of gas limit sent in the request")
                .withRegistry(meterRegistry);
        this.gasUsedCounter = Counter.builder(GAS_USED_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
        this.store = store;
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.recordFileService = recordFileService;
        this.throttleProperties = throttleProperties;
        this.gasLimitBucket = gasLimitBucket;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.mirrorNodeState = mirrorNodeState;
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
     * @param ctx the contract call context
     * @return {@link HederaEvmTransactionProcessingResult} of the contract call
     * @throws MirrorEvmTransactionException if any pre-checks
     * fail with {@link IllegalStateException} or {@link IllegalArgumentException}
     */
    protected HederaEvmTransactionProcessingResult callContract(CallServiceParameters params, ContractCallContext ctx)
            throws MirrorEvmTransactionException {
        // if we have historical call, then set the corresponding record file in the context
        if (params.getBlock() != BlockType.LATEST) {
            ctx.setRecordFile(recordFileService
                    .findByBlockType(params.getBlock())
                    .orElseThrow(BlockNumberNotFoundException::new));
        }
        // initializes the stack frame with the current state or historical state (if the call is historical)
        ctx.initializeStackFrames(store.getStackedStateFrames());
        return doProcessCall(params, params.getGas(), true);
    }

    protected HederaEvmTransactionProcessingResult doProcessCall(
            CallServiceParameters params, long estimatedGas, boolean restoreGasToThrottleBucket)
            throws MirrorEvmTransactionException {
        try {
            HederaEvmTransactionProcessingResult result = null;
            //            if (!mirrorNodeEvmProperties.isModularizedServices()) {
            //                result = mirrorEvmTxProcessor.execute(params, estimatedGas);
            //            } else {
            final var isContractCreate = params.getReceiver().isZero();
            var executor = TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(
                    mirrorNodeState,
                    Map.of(
                            "contracts.evm.version",
                            "v0.50",
                            "contracts.evm.version.dynamic",
                            "true",
                            "contracts.maxRefundPercentOfGasLimit",
                            "100"),
                    null);
            TransactionBody transactionBody = null;
            if (isContractCreate) {

            } else {
                transactionBody = TransactionBody.newBuilder()
                        .contractCall(ContractCallTransactionBody.newBuilder()
                                .contractID(ContractID.newBuilder()
                                        .evmAddress(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                                params.getReceiver().toArrayUnsafe()))
                                        .build())
                                .functionParameters(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                        params.getCallData().toArray()))
                                .gas(estimatedGas)
                                .build())
                        .nodeAccountID(AccountID.newBuilder().accountNum(2).build())
                        .transactionID(TransactionID.newBuilder()
                                .transactionValidStart(new Timestamp(0, 0))
                                .accountID(AccountID.newBuilder().accountNum(2).build())
                                .build())
                        .transactionValidDuration(new Duration(15))
                        .build();
            }
            var receipt = executor.execute(transactionBody, Instant.EPOCH);

            if (receipt.getFirst().transactionRecord().receipt().status() == ResponseCodeEnum.SUCCESS) {
                result = HederaEvmTransactionProcessingResult.successful(
                        List.of(),
                        receipt.getFirst()
                                .transactionRecord()
                                .contractCallResult()
                                .gasUsed(),
                        0L,
                        0L,
                        Bytes.wrap(receipt.getFirst()
                                .transactionRecord()
                                .contractCallResult()
                                .contractCallResult()
                                .toByteArray()),
                        params.getReceiver());
            } else {
                result = HederaEvmTransactionProcessingResult.failed(
                        receipt.getFirst()
                                .transactionRecord()
                                .contractCallResult()
                                .gasUsed(),
                        0L,
                        0L,
                        Optional.of(Bytes.wrap(receipt.getFirst()
                                .transactionRecord()
                                .contractCallResult()
                                .errorMessage()
                                .getBytes())),
                        Optional.empty());
            }
            //            }

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

    protected void validateResult(final HederaEvmTransactionProcessingResult txnResult, final CallType type) {
        if (!txnResult.isSuccessful()) {
            updateGasUsedMetric(ERROR, txnResult.getGasUsed(), 1);
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            throw new MirrorEvmTransactionException(getStatusOrDefault(txnResult), detail, revertReason.toHexString());
        } else {
            updateGasUsedMetric(type, txnResult.getGasUsed(), 1);
        }
    }

    protected void updateGasUsedMetric(final CallType callType, final long gasUsed, final int iterations) {
        gasUsedCounter
                .withTags("type", callType.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }

    protected void updateGasLimitMetric(final CallType callType, final long gasLimit) {
        gasLimitCounter.withTags("type", callType.toString()).increment(gasLimit);
    }
}
