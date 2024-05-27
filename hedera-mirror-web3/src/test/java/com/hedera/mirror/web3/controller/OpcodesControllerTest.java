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

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.common.domain.TransactionMocks.ContractCall.getContractCallRecordFile;
import static com.hedera.mirror.common.domain.TransactionMocks.ContractCall.getContractCallTransaction;
import static com.hedera.mirror.common.util.CommonUtils.timestamp;
import static com.hedera.mirror.common.util.CommonUtils.toAccountID;
import static com.hedera.mirror.web3.service.TransactionServiceImpl.MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.ContractTransactionHashRepository;
import com.hedera.mirror.web3.repository.EthereumTransactionRepository;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.repository.TransactionRepository;
import com.hedera.mirror.web3.service.CallServiceParametersBuilder;
import com.hedera.mirror.web3.service.CallServiceParametersBuilderImpl;
import com.hedera.mirror.web3.service.EthereumTransactionService;
import com.hedera.mirror.web3.service.EthereumTransactionServiceImpl;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.RecordFileServiceImpl;
import com.hedera.mirror.web3.service.TransactionService;
import com.hedera.mirror.web3.service.TransactionServiceImpl;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.TransactionMocksProvider;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;

@Import(OpcodesControllerTest.Config.class)
@WebMvcTest(controllers = OpcodesController.class)
class OpcodesControllerTest extends ControllerTest {

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private EthereumTransactionRepository ethereumTransactionRepository;

    @MockBean
    private ContractTransactionHashRepository contractTransactionHashRepository;

    @MockBean
    private RecordFileRepository recordFileRepository;

    @Autowired
    private CallServiceParametersBuilder callServiceParametersBuilder;

    @Nested
    @DisplayName(OpcodesEndpoint.OPCODES_URI)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OpcodesEndpoint extends EndpointTest {

        public static final String OPCODES_URI = "/api/v1/contracts/results/{transactionIdOrHash}/opcodes";

        @Captor
        private ArgumentCaptor<CallServiceParameters> callServiceParametersCaptor;

        @Captor
        private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

        private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();

        private String transactionIdOrHash;
        private OpcodeTracerOptions opcodeTracerOptions;

        @Override
        protected HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        protected String getUrl() {
            return OPCODES_URI;
        }

        @Override
        protected Object[] getParameters() {
            return new String[]{ transactionIdOrHash };
        }

        @Override
        protected MockHttpServletRequestBuilder customizeRequest(MockHttpServletRequestBuilder requestBuilder) {
            return requestBuilder
                    .queryParam("stack", String.valueOf(opcodeTracerOptions.isStack()))
                    .queryParam("memory", String.valueOf(opcodeTracerOptions.isMemory()))
                    .queryParam("storage", String.valueOf(opcodeTracerOptions.isStorage()));
        }

        @BeforeEach
        void setUp() {
            // tests will use this transaction and record file, in case it's not overridden by the other setUp() method
            final Transaction transaction = getContractCallTransaction();
            final RecordFile recordFile = getContractCallRecordFile();
            setUp(transaction, null, recordFile);
        }

        void setUp(final Transaction transaction, final EthereumTransaction ethTransaction, final RecordFile recordFile) {
            setUpMocks(transaction, ethTransaction, recordFile);
            this.transactionIdOrHash = getTransactionIdOrHash(transaction, ethTransaction);
            this.opcodeTracerOptions = new OpcodeTracerOptions();
        }

        void setUpMocks(final Transaction transaction, final EthereumTransaction ethTransaction, final RecordFile recordFile) {
            reset(contractCallService);
            when(contractCallService.processOpcodeCall(
                    callServiceParametersCaptor.capture(),
                    tracerOptionsCaptor.capture()
            )).thenAnswer(context -> {
                final CallServiceParameters params = context.getArgument(0);
                final OpcodeTracerOptions options = context.getArgument(1);
                opcodesResultCaptor.set(Builder.opcodesProcessingResult(params, options));
                return opcodesResultCaptor.get();
            });
            when(contractTransactionHashRepository.findByHash(
                    Optional.ofNullable(ethTransaction)
                            .map(EthereumTransaction::getHash)
                            .orElse(transaction.getTransactionHash())
            )).thenReturn(Optional.of(Builder.contractTransactionHashFrom(transaction, ethTransaction)));
            when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                    transaction.getConsensusTimestamp(),
                    transaction.getPayerAccountId()
            )).thenReturn(Optional.ofNullable(ethTransaction));
            when(transactionRepository.findByPayerAccountIdAndValidStartNsAndConsensusTimestampBefore(
                    transaction.getPayerAccountId(),
                    transaction.getValidStartNs(),
                    transaction.getValidStartNs() + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE.toNanos()
            )).thenReturn(Optional.of(transaction));
            when(transactionRepository.findById(transaction.getConsensusTimestamp())).thenReturn(Optional.of(transaction));
            when(recordFileRepository.findByTimestamp(transaction.getConsensusTimestamp())).thenReturn(Optional.of(recordFile));
        }

        @ParameterizedTest
        @ArgumentsSource(TransactionMocksProvider.class)
        void shouldThrowUnsupportedOperationFromContractCallService(final Transaction transaction,
                                                                    final EthereumTransaction ethTransaction,
                                                                    final RecordFile recordFile) throws Exception {
            setUp(transaction, ethTransaction, recordFile);

            reset(contractCallService);
            when(contractCallService.processOpcodeCall(
                    callServiceParametersCaptor.capture(),
                    tracerOptionsCaptor.capture()
            )).thenCallRealMethod();

            mockMvc.perform(buildRequest())
                    .andExpect(status().isNotImplemented())
                    .andExpect(responseBody(new GenericErrorResponse("Not implemented")));
        }

        @ParameterizedTest
        @ArgumentsSource(TransactionMocksProvider.class)
        void callRevertMethodAndExpectDetailMessage(final Transaction transaction,
                                                    final EthereumTransaction ethTransaction,
                                                    final RecordFile recordFile) throws Exception {
            setUp(transaction, ethTransaction, recordFile);

            final var detailedErrorMessage = "Custom revert message";
            final var hexDataErrorMessage =
                    "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";

            reset(contractCallService);
            when(contractCallService.processOpcodeCall(
                    callServiceParametersCaptor.capture(),
                    tracerOptionsCaptor.capture()
            )).thenThrow(new MirrorEvmTransactionException(CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

            mockMvc.perform(buildRequest())
                    .andExpect(status().isBadRequest())
                    .andExpect(responseBody(new GenericErrorResponse(
                            CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage)));
        }

        @ParameterizedTest
        @ArgumentsSource(TransactionMocksProvider.class)
        void callWithDifferentCombinationsOfTracerOptions(final Transaction transaction,
                                                          final EthereumTransaction ethTransaction,
                                                          final RecordFile recordFile) throws Exception {
            setUp(transaction, ethTransaction, recordFile);

            final var tracerOptions = new OpcodeTracerOptions[] {
                    new OpcodeTracerOptions(true, true, true),
                    new OpcodeTracerOptions(false, true, true),
                    new OpcodeTracerOptions(true, false, true),
                    new OpcodeTracerOptions(true, true, false),
                    new OpcodeTracerOptions(false, false, true),
                    new OpcodeTracerOptions(false, true, false),
                    new OpcodeTracerOptions(true, false, false),
                    new OpcodeTracerOptions(false, false, false)
            };

            for (final var options : tracerOptions) {
                opcodeTracerOptions = options;
                mockMvc.perform(buildRequest())
                        .andExpect(status().isOk())
                        .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get())));

                assertEquals(options, tracerOptionsCaptor.getValue());
                assertEquals(
                        callServiceParametersBuilder
                                .buildFromTransaction(TransactionIdOrHashParameter.valueOf(transactionIdOrHash)),
                        callServiceParametersCaptor.getValue());
            }
        }

        @ParameterizedTest
        @ArgumentsSource(TransactionMocksProvider.class)
        void callWithRecordFileNotFoundExceptionTest(final Transaction transaction,
                                                     final EthereumTransaction ethTransaction,
                                                     final RecordFile recordFile) throws Exception {
            setUp(transaction, ethTransaction, recordFile);

            when(recordFileRepository.findByTimestamp(anyLong())).thenReturn(Optional.empty());

            mockMvc.perform(buildRequest())
                    .andExpect(status().isNotFound())
                    .andExpect(responseBody(new GenericErrorResponse("Record file with transaction not found")));
        }

        @ParameterizedTest
        @ArgumentsSource(TransactionMocksProvider.class)
        void callWithTransactionNotFoundExceptionTest(final Transaction transaction,
                                                      final EthereumTransaction ethTransaction,
                                                      final RecordFile recordFile) throws Exception {
            setUp(transaction, ethTransaction, recordFile);

            when(transactionRepository.findById(anyLong())).thenReturn(Optional.empty());
            when(transactionRepository.findByPayerAccountIdAndValidStartNsAndConsensusTimestampBefore(
                    any(EntityId.class), anyLong(), anyLong()
            )).thenReturn(Optional.empty());

            mockMvc.perform(buildRequest())
                    .andExpect(status().isNotFound())
                    .andExpect(responseBody(new GenericErrorResponse("Transaction not found")));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                        " ",
                        "0x",
                        "0xghijklmno",
                        "0x00000000000000000000000000000000000004e",
                        "0x00000000000000000000000000000000000004e2a",
                        "00000000001239847e",
                        "0.0.1234-1234567890", // missing nanos
                        "0.0.1234-0-1234567890", // nanos overflow
                        "0.0.1234-1-123456789-",  // dash after nanos
                })
        void callInvalidTransactionIdOrHash(final String transactionIdOrHash) throws Exception {
            final var expectedMessage = StringUtils.hasText(transactionIdOrHash) ?
                    "Invalid transaction ID or hash" :
                    "Transaction ID or hash is required";

            this.transactionIdOrHash = transactionIdOrHash;

            mockMvc.perform(buildRequest())
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(new StringContains(expectedMessage)));
        }

        private String getTransactionIdOrHash(final Transaction transaction,
                                                                    final EthereumTransaction ethereumTransaction) {
            if (ethereumTransaction != null) {
                return Bytes.of(ethereumTransaction.getHash()).toHexString();
            } else {
                return buildTransactionIdString(
                        toAccountID(transaction.getPayerAccountId()),
                        timestamp(transaction.getValidStartNs())
                );
            }
        }

        private String buildTransactionIdString(final AccountID accountID, final Timestamp transactionValidStart) {
            final String entityId = "%d.%d.%d".formatted(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
            return "%s-%d-%d".formatted(
                    entityId,
                    transactionValidStart.getSeconds(),
                    transactionValidStart.getNanos());
        }
    }

    /**
     * Utility class with helper methods for building different objects in the tests
     */
    @UtilityClass
    private static class Builder {

        private static ContractTransactionHash contractTransactionHashFrom(final Transaction transaction,
                                                                           final EthereumTransaction ethTransaction) {
            return ContractTransactionHash.builder()
                    .consensusTimestamp(transaction.getConsensusTimestamp())
                    .entityId(transaction.getEntityId().getId())
                    .hash(Optional.ofNullable(ethTransaction)
                            .map(EthereumTransaction::getHash)
                            .orElse(transaction.getTransactionHash()))
                    .payerAccountId(transaction.getPayerAccountId().getId())
                    .transactionResult(transaction.getResult())
                    .build();
        }

        private static OpcodesResponse opcodesResponse(final OpcodesProcessingResult result) {
            return new OpcodesResponse()
                    .address(result.transactionProcessingResult().getRecipient()
                            .map(Address::toHexString)
                            .orElse(Address.ZERO.toHexString()))
                    .contractId(result.transactionProcessingResult().getRecipient()
                            .map(EntityIdUtils::contractIdFromEvmAddress)
                            .map(contractId -> "%d.%d.%d".formatted(
                                    contractId.getShardNum(),
                                    contractId.getRealmNum(),
                                    contractId.getContractNum()))
                            .orElse(null))
                    .failed(!result.transactionProcessingResult().isSuccessful())
                    .gas(result.transactionProcessingResult().getGasUsed())
                    .opcodes(result.opcodes().stream()
                            .map(opcode -> new com.hedera.mirror.rest.model.Opcode()
                                    .depth(opcode.depth())
                                    .gas(opcode.gas())
                                    .gasCost(opcode.gasCost())
                                    .op(opcode.op())
                                    .pc(opcode.pc())
                                    .reason(opcode.reason())
                                    .stack(opcode.stack().isPresent() ?
                                            Arrays.stream(opcode.stack().get())
                                                    .map(Bytes::toHexString)
                                                    .toList() :
                                            null)
                                    .memory(opcode.memory().isPresent() ?
                                            Arrays.stream(opcode.memory().get())
                                                    .map(Bytes::toHexString)
                                                    .toList() :
                                            null)
                                    .storage(opcode.storage().isPresent() ?
                                            opcode.storage().get().entrySet().stream()
                                                    .collect(Collectors.toMap(
                                                            entry -> entry.getKey().toHexString(),
                                                            entry -> entry.getValue().toHexString())) :
                                            null))
                            .toList())
                    .returnValue(Optional.ofNullable(result.transactionProcessingResult().getOutput())
                            .map(Bytes::toHexString)
                            .orElse(Bytes.EMPTY.toHexString()));
        }

        private static OpcodesProcessingResult opcodesProcessingResult(final CallServiceParameters params,
                                                                       final OpcodeTracerOptions options) {
            final Address recipient = params != null ? params.getReceiver() : Address.ZERO;
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed = opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L);
            final long gasCost = opcodes.stream().map(Opcode::gasCost).reduce(Long::sum).orElse(0L);
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(HederaEvmTransactionProcessingResult
                            .successful(List.of(), gasUsed , 0, gasCost, Bytes.EMPTY, recipient))
                    .opcodes(opcodes)
                    .build();
        }

        private static List<Opcode> opcodes(final OpcodeTracerOptions options) {
            return Arrays.asList(
                    new Opcode(
                            1273,
                            "PUSH1",
                            2731,
                            3,
                            2,
                            options.isStack() ?
                                    Optional.of(new Bytes[]{
                                            Bytes.fromHexString("000000000000000000000000000000000000000000000000000000004700d305"),
                                            Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a7")
                                    }) : Optional.empty(),
                            options.isMemory() ?
                                    Optional.of(new Bytes[]{
                                            Bytes.fromHexString("4e487b7100000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString("0000001200000000000000000000000000000000000000000000000000000000")
                                    }) : Optional.empty(),
                            options.isStorage() ?
                                    Optional.of(Collections.emptySortedMap()) : Optional.empty(),
                            null
                    ),
                    new Opcode(
                            1275,
                            "REVERT",
                            2728,
                            0,
                            2,
                            options.isStack() ?
                                    Optional.of(new Bytes[]{
                                            Bytes.fromHexString("000000000000000000000000000000000000000000000000000000004700d305"),
                                            Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a7")
                                    }) : Optional.empty(),
                            options.isMemory() ?
                                    Optional.of(new Bytes[]{
                                            Bytes.fromHexString("4e487b7100000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString("0000001200000000000000000000000000000000000000000000000000000000")
                                    }) : Optional.empty(),
                            options.isStorage() ?
                                    Optional.of(Collections.emptySortedMap()) : Optional.empty(),
                            "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"
                    ),
                    new Opcode(
                            682,
                            "SWAP2",
                            2776,
                            3,
                            1,
                            options.isStack() ?
                                    Optional.of(new Bytes[]{
                                            Bytes.fromHexString("000000000000000000000000000000000000000000000000000000000135b7d0"),
                                            Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a0")
                                    }) : Optional.empty(),
                            options.isMemory() ?
                                    Optional.of(new Bytes[]{
                                            Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")
                                    }) : Optional.empty(),
                            options.isStorage() ?
                                    Optional.of(ImmutableSortedMap.of(
                                            Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000014")
                                    )) : Optional.empty(),
                            null
                    )
            );
        }
    }

    @TestConfiguration
    public static class Config {

        @Bean
        TransactionService transactionService(final TransactionRepository transactionRepository) {
            return new TransactionServiceImpl(transactionRepository);
        }

        @Bean
        EthereumTransactionService ethereumTransactionService(
                final EthereumTransactionRepository ethereumTransactionRepository,
                final ContractTransactionHashRepository contractTransactionHashRepository
        ) {
            return new EthereumTransactionServiceImpl(ethereumTransactionRepository, contractTransactionHashRepository);
        }

        @Bean
        RecordFileService recordFileService(final RecordFileRepository recordFileRepository) {
            return new RecordFileServiceImpl(recordFileRepository);
        }

        @Bean
        CallServiceParametersBuilder callServiceParametersBuilder(
                final TransactionService transactionService,
                final EthereumTransactionService ethereumTransactionService,
                final RecordFileService recordFileService
        ) {
            return new CallServiceParametersBuilderImpl(
                    transactionService,
                    ethereumTransactionService,
                    recordFileService
            );
        }
    }
}
