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

import static com.hedera.mirror.web3.state.Utils.isMirror;

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
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.state.AliasesReadableKVState;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.TracerBinding;
import com.hedera.node.config.data.EntitiesConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Named
@CustomLog
public class TransactionExecutionService {

    private static final Configuration DEFAULT_CONFIG = new ConfigProviderImpl().getConfiguration();
    private static final OperationTracer[] EMPTY_OPERATION_TRACER_ARRAY = new OperationTracer[0];
    private static final AccountID TREASURY_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(2).build();
    private static final Duration TRANSACTION_DURATION = new Duration(15);
    private static final Timestamp TRANSACTION_START = new Timestamp(0, 0);
    private static final int INITCODE_SIZE_KB = 6 * 1024;
    private final State mirrorNodeState;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final OpcodeTracer opcodeTracer;

    protected TransactionExecutionService(
            State mirrorNodeState, MirrorNodeEvmProperties mirrorNodeEvmProperties, OpcodeTracer opcodeTracer) {
        this.mirrorNodeState = mirrorNodeState;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.opcodeTracer = opcodeTracer;
    }

    public HederaEvmTransactionProcessingResult execute(final CallServiceParameters params, final long estimatedGas) {
        final var isContractCreate = params.getReceiver().isZero();
        final var maxLifetime =
                DEFAULT_CONFIG.getConfigData(EntitiesConfig.class).maxLifetime();
        var executor =
                ExecutorFactory.newExecutor(mirrorNodeState, mirrorNodeEvmProperties.getTransactionProperties(), null);

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

        var receipt = executor.execute(transactionBody, Instant.EPOCH, getOperationTracers());
        var transactionRecord = receipt.getFirst().transactionRecord();
        if (transactionRecord.receiptOrThrow().status() == ResponseCodeEnum.SUCCESS) {
            result = buildSuccessResult(isContractCreate, transactionRecord, params);
        } else {
            result = buildFailedResult(transactionRecord, isContractCreate);
        }
        return result;
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

    private HederaEvmTransactionProcessingResult buildFailedResult(
            final TransactionRecord transactionRecord, final boolean isContractCreate) {
        var result = getTransactionResult(transactionRecord, isContractCreate);
        var status = transactionRecord.receipt().status();

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
                : EMPTY_OPERATION_TRACER_ARRAY;
    }

    public static class ExecutorFactory {

        private ExecutorFactory() {}

        public static TransactionExecutor newExecutor(
                State mirrorNodeState,
                Map<String, String> properties,
                @Nullable final TracerBinding customTracerBinding) {
            return TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(
                    mirrorNodeState, properties, customTracerBinding);
        }
    }
}
