/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.web3.state.Utils.isMirror;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;

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
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.state.keyvalue.AliasesReadableKVState;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.data.EntitiesConfig;
import com.swirlds.state.State;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Named
@CustomLog
@RequiredArgsConstructor
public class TransactionExecutionService {

    private static final AccountID TREASURY_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(2).build();
    private static final Duration TRANSACTION_DURATION = new Duration(15);
    private static final int INITCODE_SIZE_KB = 6 * 1024;

    private final State mirrorNodeState;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final OpcodeTracer opcodeTracer;
    private final MirrorOperationTracer mirrorOperationTracer;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public HederaEvmTransactionProcessingResult execute(
            final CallServiceParameters params, final long estimatedGas, final MeterProvider<Counter> gasUsedCounter) {
        final var isContractCreate = params.getReceiver().isZero();
        final var configuration = mirrorNodeEvmProperties.getVersionedConfiguration();
        final var maxLifetime =
                configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        var executor = transactionExecutorFactory.get();

        TransactionBody transactionBody;
        HederaEvmTransactionProcessingResult result = null;
        if (isContractCreate) {
            if (params.getCallData().size() < INITCODE_SIZE_KB) {
                transactionBody = buildContractCreateTransactionBodyWithInitBytecode(params, estimatedGas, maxLifetime);
            } else {
                // Upload the init bytecode
                transactionBody = buildFileCreateTransactionBody(params, maxLifetime);
                var uploadReceipt = executor.execute(transactionBody, Instant.now());
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

        var receipt = executor.execute(transactionBody, Instant.now(), getOperationTracers());
        var transactionRecords =
                receipt.stream().map(SingleTransactionRecord::transactionRecord).toList();
        var transactionRecordsStatuses = transactionRecords.stream()
                .map(r -> r.receiptOrThrow().status())
                .toList();
        if (transactionRecordsStatuses.stream().allMatch(com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS::equals)) {
            result = buildSuccessResult(isContractCreate, transactionRecords.getFirst(), params);
        } else {
            handleFailedResult(transactionRecords, isContractCreate, gasUsedCounter);
        }
        return result;
    }

    // Duplicated code as in ContractCallService class - it will be removed from there when switch to the modularized
    // implementation entirely.
    private void updateErrorGasUsedMetric(
            final MeterProvider<Counter> gasUsedCounter, final long gasUsed, final int iterations) {
        gasUsedCounter
                .withTags("type", CallType.ERROR.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }

    private ContractFunctionResult getTransactionResult(
            final TransactionRecord transactionRecord, boolean isContractCreate) {
        return isContractCreate
                ? transactionRecord.contractCreateResultOrThrow()
                : transactionRecord.contractCallResultOrThrow();
    }

    private HederaEvmTransactionProcessingResult buildSuccessResult(
            final boolean isContractCreate,
            final TransactionRecord transactionRecord,
            final CallServiceParameters params) {
        var result = getTransactionResult(transactionRecord, isContractCreate);

        return HederaEvmTransactionProcessingResult.successful(
                List.of(),
                result.gasUsed(),
                0L,
                0L,
                Bytes.wrap(result.contractCallResult().toByteArray()),
                params.getReceiver());
    }

    private void handleFailedResult(
            final List<TransactionRecord> transactionRecords,
            final boolean isContractCreate,
            final MeterProvider<Counter> gasUsedCounter)
            throws MirrorEvmTransactionException {
        var results = isContractCreate
                ? transactionRecords.stream()
                        .map(TransactionRecord::contractCreateResult)
                        .toList()
                : transactionRecords.stream()
                        .map(TransactionRecord::contractCallResult)
                        .toList();
        var statuses = transactionRecords.stream()
                .map(t -> t.receiptOrThrow().status())
                .toList();
        if (results.stream().allMatch(Objects::isNull)) {
            // No result - the call did not reach the EVM and probably failed at pre-checks. No metric to update in this
            // case.
            throw new MirrorEvmTransactionException(
                    String.join(
                            " -> ",
                            statuses.stream().map(ResponseCodeEnum::protoName).toList()),
                    StringUtils.EMPTY,
                    StringUtils.EMPTY);
        } else {
            var errorMessage = getErrorMessage(results.getFirst()).orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(errorMessage);
            updateErrorGasUsedMetric(gasUsedCounter, results.getFirst().gasUsed(), 1);
            throw new MirrorEvmTransactionException(
                    String.join(
                            " -> ",
                            statuses.stream().map(ResponseCodeEnum::protoName).toList()),
                    detail,
                    errorMessage.toHexString());
        }
    }

    private Optional<Bytes> getErrorMessage(final ContractFunctionResult result) {
        return result.errorMessage().startsWith(HEX_PREFIX)
                ? Optional.of(Bytes.fromHexString(result.errorMessage()))
                : Optional.empty(); // If it doesn't start with 0x, the message is already decoded and readable.
    }

    private TransactionBody.Builder defaultTransactionBodyBuilder(final CallServiceParameters params) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(new Timestamp(Instant.now().getEpochSecond(), 0))
                        .accountID(getSenderAccountID(params))
                        .build())
                .nodeAccountID(TREASURY_ACCOUNT_ID) // We don't really need another account here.
                .transactionValidDuration(TRANSACTION_DURATION);
    }

    private TransactionBody buildFileCreateTransactionBody(final CallServiceParameters params, long maxLifetime) {
        return defaultTransactionBodyBuilder(params)
                .fileCreate(FileCreateTransactionBody.newBuilder()
                        .contents(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArrayUnsafe()))
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
                                params.getCallData().toArrayUnsafe()))
                        .amount(params.getValue()) // tinybars sent to contract
                        .gas(estimatedGas)
                        .build())
                .build();
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
                .get(AliasesReadableKVState.KEY)
                .get(convertAddressToProtoBytes(senderAddress));
    }

    private OperationTracer[] getOperationTracers() {
        return ContractCallContext.get().getOpcodeTracerOptions() != null
                ? new OperationTracer[] {opcodeTracer}
                : new OperationTracer[] {mirrorOperationTracer};
    }
}
