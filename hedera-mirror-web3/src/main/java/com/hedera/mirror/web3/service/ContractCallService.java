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
import static com.hedera.mirror.web3.state.Utils.isMirror;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
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
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.config.data.EntitiesConfig;
import com.swirlds.config.api.Configuration;
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
import org.hyperledger.besu.datatypes.Address;

@Named
@CustomLog
public abstract class ContractCallService {
    static final String GAS_LIMIT_METRIC = "hedera.mirror.web3.call.gas.limit";
    static final String GAS_USED_METRIC = "hedera.mirror.web3.call.gas.used";
    private static final Configuration DEFAULT_CONFIG = new ConfigProviderImpl().getConfiguration();
    private static final Duration TRANSACTION_DURATION = new Duration(15);
    private static final Timestamp TRANSACTION_START = new Timestamp(0, 0);
    private static final AccountID TREASURY_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(2).build();
    private static final AccountID NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(3).build();
    // The size in KB for the init bytecode on contract deploy.
    // Less than this value: it can be passed directly in the contract create request.
    // Over this value: the bytecode needs to be uploaded as a file and the file id needs to be passed in the
    // contract create transaction body.
    private static final int INITCODE_SIZE_KB = 6 * 1024;
    private final MeterProvider<Counter> gasLimitCounter;
    private final MeterProvider<Counter> gasUsedCounter;
    protected final Store store;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileService recordFileService;
    private final ThrottleProperties throttleProperties;
    private final Bucket gasLimitBucket;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final State mirrorNodeState;
    private Map<String, String> transactionProperties;

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
            HederaEvmTransactionProcessingResult result;
            if (!mirrorNodeEvmProperties.isModularizedServices()) {
                result = mirrorEvmTxProcessor.execute(params, estimatedGas);
            } else {
                result = processModularizedCall(params, estimatedGas);
            }
            if (!restoreGasToThrottleBucket) {
                return result;
            }

            restoreGasToBucket(result, params.getGas());
            return result;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new MirrorEvmTransactionException(e.getMessage(), EMPTY, EMPTY);
        }
    }

    private Map<String, String> getTransactionProperties() {
        if (transactionProperties == null) {
            final var mirrorNodeProperties = mirrorNodeEvmProperties.getProperties();
            mirrorNodeProperties.put(
                    "contracts.evm.version",
                    "v"
                            + mirrorNodeEvmProperties.getSemanticEvmVersion().major() + "."
                            + mirrorNodeEvmProperties.getSemanticEvmVersion().minor());
            mirrorNodeProperties.put(
                    "ledger.id",
                    Bytes.wrap(mirrorNodeEvmProperties.getNetwork().getLedgerId())
                            .toHexString());
            this.transactionProperties = mirrorNodeProperties;
        }
        return transactionProperties;
    }

    private ContractFunctionResult getTransactionResult(
            final List<SingleTransactionRecord> receipt, boolean isContractCreate) {
        return isContractCreate
                ? receipt.getFirst().transactionRecord().contractCreateResultOrThrow()
                : receipt.getFirst().transactionRecord().contractCallResultOrThrow();
    }

    private HederaEvmTransactionProcessingResult buildSuccessResult(
            final boolean isContractCreate,
            final List<SingleTransactionRecord> receipt,
            final CallServiceParameters params) {
        var result = getTransactionResult(receipt, isContractCreate);

        return HederaEvmTransactionProcessingResult.successful(
                List.of(),
                result.gasUsed(),
                0L,
                0L,
                Bytes.wrap(result.contractCallResult().toByteArray()),
                params.getReceiver());
    }

    private HederaEvmTransactionProcessingResult buildFailedResult(
            final List<SingleTransactionRecord> receipt, final boolean isContractCreate) {
        var result = getTransactionResult(receipt, isContractCreate);
        var status = receipt.getFirst().transactionRecord().receipt().status();

        return HederaEvmTransactionProcessingResult.failed(
                result.gasUsed(),
                0L,
                0L,
                Optional.of(Bytes.wrap(status.protoName().getBytes())),
                Optional.empty());
    }

    private TransactionBody.Builder defaultTransactionBodyBuilder(final CallServiceParameters params) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(TRANSACTION_START)
                        .accountID(getSenderAccountID(params))
                        .build())
                .nodeAccountID(NODE_ACCOUNT_ID)
                .transactionValidDuration(TRANSACTION_DURATION);
    }

    private TransactionBody buildFileCreateTransactionBody(final CallServiceParameters params, long maxLifetime) {
        return defaultTransactionBodyBuilder(params)
                .fileCreate(FileCreateTransactionBody.newBuilder()
                        .contents(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArray()))
                        .expirationTime(new Timestamp(maxLifetime, 0))
                        .build())
                .build();
    }

    private TransactionBody buildContractCreateTransactionBodyWithInitBytecode(
            final CallServiceParameters params, long estimatedGas, long maxLifetime) {
        return defaultTransactionBodyBuilder(params)
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .initcode(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArrayUnsafe()))
                        .gas(estimatedGas)
                        .autoRenewPeriod(new Duration(maxLifetime))
                        .build())
                .build();
    }

    private TransactionBody buildContractCreateTransactionBodyWithFileID(
            final CallServiceParameters params, final FileID fileID, long estimatedGas, long maxLifetime) {
        return defaultTransactionBodyBuilder(params)
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .fileID(fileID)
                        .gas(estimatedGas)
                        .autoRenewPeriod(new Duration(maxLifetime))
                        .build())
                .build();
    }

    private TransactionBody buildContractCallTransactionBody(
            final CallServiceParameters params, final long estimatedGas) {
        return defaultTransactionBodyBuilder(params)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .contractID(ContractID.newBuilder()
                                .evmAddress(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                        params.getReceiver().toArrayUnsafe()))
                                .build())
                        .functionParameters(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArray()))
                        .gas(estimatedGas)
                        .build())
                .build();
    }

    private HederaEvmTransactionProcessingResult processModularizedCall(
            final CallServiceParameters params, final long estimatedGas) {
        final var isContractCreate = params.getReceiver().isZero();
        final var maxLifetime =
                DEFAULT_CONFIG.getConfigData(EntitiesConfig.class).maxLifetime();
        var executor = TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(
                mirrorNodeState, getTransactionProperties(), null);

        TransactionBody transactionBody;
        HederaEvmTransactionProcessingResult result;
        if (isContractCreate) {
            if (params.getCallData().size() < INITCODE_SIZE_KB) {
                transactionBody = buildContractCreateTransactionBodyWithInitBytecode(params, estimatedGas, maxLifetime);
            } else {
                // Upload the init bytecode
                transactionBody = buildFileCreateTransactionBody(params, maxLifetime);
                var uploadReceipt = executor.execute(transactionBody, Instant.EPOCH);
                final var fileID = uploadReceipt
                        .getFirst()
                        .transactionRecord()
                        .receiptOrThrow()
                        .fileIDOrThrow();
                final var file = File.newBuilder()
                        .fileId(fileID)
                        .contents(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toFastHex(false).getBytes()))
                        .build();
                // Set the context variables for the uploaded contract.
                ContractCallContext.get().setFile(Optional.of(file));

                // Create the contract with the init bytecode
                transactionBody =
                        buildContractCreateTransactionBodyWithFileID(params, fileID, estimatedGas, maxLifetime);
            }
        } else {
            transactionBody = buildContractCallTransactionBody(params, estimatedGas);
        }
        var receipt = executor.execute(transactionBody, Instant.EPOCH);
        if (receipt.getFirst().transactionRecord().receiptOrThrow().status() == ResponseCodeEnum.SUCCESS) {
            result = buildSuccessResult(isContractCreate, receipt, params);
        } else {
            result = buildFailedResult(receipt, isContractCreate);
        }
        return result;
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

    private ProtoBytes convertAddressToProtoBytes(final Address address) {
        return ProtoBytes.newBuilder()
                .value(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(address.toArrayUnsafe()))
                .build();
    }

    private AccountID getSenderAccountID(final CallServiceParameters params) {
        if (params.getSender().canonicalAddress().isZero() && params.getValue() == 0L) {
            // Set a default account to keep the sender parameter optional.
            return TREASURY_ACCOUNT_ID;
        }

        final var senderAddress = params.getSender().canonicalAddress();
        if (isMirror(senderAddress)) {
            return AccountID.newBuilder()
                    .accountNum(senderAddress.trimLeadingZeros().toLong())
                    .build();
        }
        return (AccountID) mirrorNodeState
                .getReadableStates(TokenService.NAME)
                .get("ALIASES")
                .get(convertAddressToProtoBytes(senderAddress));
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
