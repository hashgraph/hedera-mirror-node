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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.TracerBinding;
import com.hedera.node.config.data.EntitiesConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Named
@CustomLog
public class TransactionExecutionService {

    private static final Configuration DEFAULT_CONFIG = new ConfigProviderImpl().getConfiguration();
    private static final AccountID TREASURY_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(2).build();
    private static final Duration TRANSACTION_DURATION = new Duration(15);
    private static final Timestamp TRANSACTION_START = new Timestamp(0, 0);
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
        var executor = ExecutorFactory.newExecutor(mirrorNodeState, buildTransactionExecutorProperties(), null);

        TransactionBody transactionBody;
        HederaEvmTransactionProcessingResult result;
        if (isContractCreate) {
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
            transactionBody = buildContractCreateTransactionBody(fileID, estimatedGas, maxLifetime);
        } else {
            transactionBody = buildContractCallTransactionBody(params, estimatedGas);
        }

        List<OperationTracer> operationTracers = new ArrayList<>();
        if (ContractCallContext.get().getOpcodeTracerOptions() != null) {
            operationTracers.add(opcodeTracer);
        }

        var receipt =
                executor.execute(transactionBody, Instant.EPOCH, operationTracers.toArray(OperationTracer[]::new));

        if (receipt.getFirst().transactionRecord().receiptOrThrow().status() == ResponseCodeEnum.SUCCESS) {
            result = buildSuccessResult(isContractCreate, receipt, params);
        } else {
            result = buildFailedResult(receipt, isContractCreate);
        }
        return result;
    }

    private Map<String, String> buildTransactionExecutorProperties() {
        final var mirrorNodeProperties = mirrorNodeEvmProperties.getProperties();
        mirrorNodeProperties.put(
                "contracts.evm.version",
                "v" + mirrorNodeEvmProperties.getSemanticEvmVersion().major() + "."
                        + mirrorNodeEvmProperties.getSemanticEvmVersion().minor());
        mirrorNodeProperties.put(
                "ledger.id",
                Bytes.wrap(mirrorNodeEvmProperties.getNetwork().getLedgerId()).toHexString());
        return mirrorNodeProperties;
    }

    private TransactionBody buildFileCreateTransactionBody(final CallServiceParameters params, long maxLifetime) {
        return TransactionBody.newBuilder()
                .fileCreate(FileCreateTransactionBody.newBuilder()
                        .contents(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArray()))
                        .expirationTime(new Timestamp(maxLifetime, 0))
                        .build())
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(TRANSACTION_START)
                        .accountID(TREASURY_ACCOUNT_ID)
                        .build())
                .transactionValidDuration(TRANSACTION_DURATION)
                .build();
    }

    private TransactionBody buildContractCreateTransactionBody(
            final FileID fileID, long estimatedGas, long maxLifetime) {
        return TransactionBody.newBuilder()
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .fileID(fileID)
                        .gas(estimatedGas)
                        .autoRenewPeriod(new Duration(maxLifetime))
                        .build())
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(TRANSACTION_START)
                        .accountID(TREASURY_ACCOUNT_ID)
                        .build())
                .nodeAccountID(TREASURY_ACCOUNT_ID)
                .transactionValidDuration(TRANSACTION_DURATION)
                .build();
    }

    private TransactionBody buildContractCallTransactionBody(
            final CallServiceParameters params, final long estimatedGas) {
        return TransactionBody.newBuilder()
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .contractID(ContractID.newBuilder()
                                .evmAddress(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                        params.getReceiver().toArrayUnsafe()))
                                .build())
                        .functionParameters(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArray()))
                        .gas(estimatedGas)
                        .build())
                .nodeAccountID(TREASURY_ACCOUNT_ID)
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(TRANSACTION_START)
                        .accountID(TREASURY_ACCOUNT_ID)
                        .build())
                .transactionValidDuration(TRANSACTION_DURATION)
                .build();
    }

    private HederaEvmTransactionProcessingResult buildSuccessResult(
            final boolean isContractCreate,
            final List<SingleTransactionRecord> receipt,
            final CallServiceParameters params) {
        var result = isContractCreate
                ? receipt.getFirst().transactionRecord().contractCreateResult()
                : receipt.getFirst().transactionRecord().contractCallResult();

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
        var result = isContractCreate
                ? receipt.getFirst().transactionRecord().contractCreateResultOrThrow()
                : receipt.getFirst().transactionRecord().contractCallResultOrThrow();
        var status = receipt.getFirst().transactionRecord().receipt().status();

        return HederaEvmTransactionProcessingResult.failed(
                result.gasUsed(),
                0L,
                0L,
                Optional.of(Bytes.wrap(status.protoName().getBytes())),
                Optional.empty());
    }

    public static class ExecutorFactory {
        public static TransactionExecutor newExecutor(
                State mirrorNodeState,
                Map<String, String> properties,
                @Nullable final TracerBinding customTracerBinding) {
            return TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(
                    mirrorNodeState, properties, customTracerBinding);
        }
    }
}
