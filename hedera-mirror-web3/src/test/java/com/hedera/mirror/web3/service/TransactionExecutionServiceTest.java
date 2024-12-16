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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import com.hedera.mirror.web3.service.TransactionExecutionService.ExecutorFactory;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import java.time.Instant;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionExecutionServiceTest {
    private static final Long DEFAULT_GAS = 50000L;

    @Mock
    private State mirrorNodeState;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private OpcodeTracer opcodeTracer;

    @Mock
    private TransactionExecutor transactionExecutor;

    @Mock
    private ContractCallContext contractCallContext;

    private TransactionExecutionService transactionExecutionService;

    @BeforeEach
    void setUp() {
        transactionExecutionService =
                new TransactionExecutionService(mirrorNodeState, mirrorNodeEvmProperties, opcodeTracer);
    }

    @Test
    void testExecuteContractCallSuccess() {
        // Given
        try (MockedStatic<ExecutorFactory> executorFactoryMock = mockStatic(ExecutorFactory.class);
                MockedStatic<ContractCallContext> contractCallContextMock = mockStatic(ContractCallContext.class)) {

            // Set up mock behaviors for ExecutorFactory
            executorFactoryMock
                    .when(() -> ExecutorFactory.newExecutor(any(), any(), any()))
                    .thenReturn(transactionExecutor);

            mockEvmProperties();
            // Set up mock behaviors for ContractCallContext
            contractCallContextMock.when(ContractCallContext::get).thenReturn(contractCallContext);
            when(contractCallContext.getOpcodeTracerOptions()).thenReturn(new OpcodeTracerOptions());

            // Mock the SingleTransactionRecord and TransactionRecord
            SingleTransactionRecord singleTransactionRecord = mock(SingleTransactionRecord.class);
            TransactionRecord transactionRecord = mock(TransactionRecord.class);
            TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);

            // Simulate SUCCESS status in the receipt
            when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.SUCCESS);
            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            ContractFunctionResult contractFunctionResult = mock(ContractFunctionResult.class);
            when(contractFunctionResult.gasUsed()).thenReturn(DEFAULT_GAS);
            when(contractFunctionResult.contractCallResult()).thenReturn(Bytes.EMPTY);

            // Mock the transactionRecord to return the contract call result
            when(transactionRecord.contractCallResultOrThrow()).thenReturn(contractFunctionResult);

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            CallServiceParameters callServiceParameters = buildServiceParams(false);

            // When
            var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
            assertThat(result.getRevertReason()).isNotPresent();
        }
    }

    @Test
    void testExecuteContractCallFailure() {
        // Given
        try (MockedStatic<ExecutorFactory> executorFactoryMock = mockStatic(ExecutorFactory.class);
                MockedStatic<ContractCallContext> contractCallContextMock = mockStatic(ContractCallContext.class)) {

            // Set up mock behaviors for ExecutorFactory
            executorFactoryMock
                    .when(() -> ExecutorFactory.newExecutor(any(), any(), any()))
                    .thenReturn(transactionExecutor);

            mockEvmProperties();
            // Set up mock behaviors for ContractCallContext
            contractCallContextMock.when(ContractCallContext::get).thenReturn(contractCallContext);

            // Mock the SingleTransactionRecord and TransactionRecord
            SingleTransactionRecord singleTransactionRecord = mock(SingleTransactionRecord.class);
            TransactionRecord transactionRecord = mock(TransactionRecord.class);
            TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);

            // Simulate CONTRACT_REVERT_EXECUTED status in the receipt
            when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);
            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(transactionRecord.receipt()).thenReturn(transactionReceipt);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            ContractFunctionResult contractFunctionResult = mock(ContractFunctionResult.class);
            when(transactionRecord.contractCallResultOrThrow()).thenReturn(contractFunctionResult);
            when(contractFunctionResult.gasUsed()).thenReturn(DEFAULT_GAS);

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            CallServiceParameters callServiceParameters = buildServiceParams(false);

            // When
            var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
            assertThat(result.getRevertReason()).isPresent();
        }
    }

    @Test
    void testExecuteContractCreateSuccess() {
        // Given
        try (MockedStatic<ExecutorFactory> executorFactoryMock = mockStatic(ExecutorFactory.class);
                MockedStatic<ContractCallContext> contractCallContextMock = mockStatic(ContractCallContext.class)) {

            // Set up mock behaviors for ExecutorFactory
            executorFactoryMock
                    .when(() -> ExecutorFactory.newExecutor(any(), any(), any()))
                    .thenReturn(transactionExecutor);

            mockEvmProperties();
            // Set up mock behaviors for ContractCallContext
            contractCallContextMock.when(ContractCallContext::get).thenReturn(contractCallContext);
            when(contractCallContext.getOpcodeTracerOptions()).thenReturn(new OpcodeTracerOptions());

            // Mock the SingleTransactionRecord and TransactionRecord
            SingleTransactionRecord singleTransactionRecord = mock(SingleTransactionRecord.class);
            TransactionRecord transactionRecord = mock(TransactionRecord.class);
            TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);

            when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.SUCCESS);
            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            ContractFunctionResult contractFunctionResult = mock(ContractFunctionResult.class);
            when(contractFunctionResult.gasUsed()).thenReturn(DEFAULT_GAS);
            when(contractFunctionResult.contractCallResult()).thenReturn(Bytes.EMPTY);

            // Mock the transactionRecord to return the contract call result
            when(transactionRecord.contractCreateResultOrThrow()).thenReturn(contractFunctionResult);

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            CallServiceParameters callServiceParameters = buildServiceParams(true);

            // When
            var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
            assertThat(result.getRevertReason()).isNotPresent();
        }
    }

    private void mockEvmProperties() {
        when(mirrorNodeEvmProperties.getSemanticEvmVersion()).thenReturn(EVM_VERSION);
        when(mirrorNodeEvmProperties.getNetwork()).thenReturn(HederaNetwork.OTHER);
    }

    private CallServiceParameters buildServiceParams(boolean isContractCreate) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(org.apache.tuweni.bytes.Bytes.EMPTY)
                .callType(CallType.ETH_CALL)
                .gas(DEFAULT_GAS)
                .isEstimate(false)
                .isStatic(true)
                .receiver(isContractCreate ? Address.ZERO : Address.fromHexString("0x1234"))
                .sender(new HederaEvmAccount(Address.ZERO))
                .value(0)
                .build();
    }
}
