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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.ContractTransactionHashRepository;
import com.hedera.mirror.web3.repository.EthereumTransactionRepository;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.repository.TransactionRepository;
import com.hedera.mirror.web3.service.CallServiceParametersBuilder;
import com.hedera.mirror.web3.service.CallServiceParametersBuilderImpl;
import com.hedera.mirror.web3.service.ContractCallService;
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
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = OpcodesController.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpcodesControllerTest {

    private static final String OPCODES_URI = "/api/v1/contracts/results/{transactionIdOrHash}/opcodes";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @MockBean
    private ContractCallService contractCallService;

    @MockBean(name = "rateLimitBucket")
    private Bucket rateLimitBucket;

    @MockBean(name = "gasLimitBucket")
    private Bucket gasLimitBucket;

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

    @Captor
    private ArgumentCaptor<CallServiceParameters> callServiceParametersCaptor;

    @Captor
    private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

    private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter) {
        return opcodesRequest(parameter, new OpcodeTracerOptions());
    }

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter,
                                                         final OpcodeTracerOptions options) {
        final String transactionIdOrHash = parameter.isHash() ?
                Bytes.of(parameter.hash().toByteArray()).toHexString() :
                transactionIdString(
                        parameter.transactionID().getAccountID(),
                        parameter.transactionID().getTransactionValidStart()
                );

        return opcodesRequest(transactionIdOrHash)
                .queryParam("stack", String.valueOf(options.isStack()))
                .queryParam("memory", String.valueOf(options.isMemory()))
                .queryParam("storage", String.valueOf(options.isStorage()));
    }

    private MockHttpServletRequestBuilder opcodesRequest(final String transactionIdOrHash) {
        return get(OPCODES_URI, transactionIdOrHash)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultMatcher responseBody(final Object expectedBody) throws JsonProcessingException {
        return content().string(objectMapper.writeValueAsString(expectedBody));
    }

    private TransactionIdOrHashParameter getTransactionIdOrHash(final Transaction transaction,
                                                                final EthereumTransaction ethereumTransaction) {
        if (ethereumTransaction != null) {
            final String ethHash = Bytes.of(ethereumTransaction.getHash()).toHexString();
            return TransactionIdOrHashParameter.valueOf(ethHash);
        } else {
            final String transactionId = transactionIdString(
                    toAccountID(transaction.getPayerAccountId()),
                    timestamp(transaction.getValidStartNs())
            );
            return TransactionIdOrHashParameter.valueOf(transactionId);
        }
    }

    private String transactionIdString(final AccountID accountID, final Timestamp transactionValidStart) {
        final String entityId = "%d.%d.%d".formatted(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
        return "%s-%d-%d".formatted(
                entityId,
                transactionValidStart.getSeconds(),
                transactionValidStart.getNanos());
    }

    void setUp(final Transaction transaction, final EthereumTransaction ethTransaction, final RecordFile recordFile) {
        when(rateLimitBucket.tryConsume(1)).thenReturn(true);
        when(gasLimitBucket.tryConsume(anyLong())).thenReturn(true);
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
        when(ethereumTransactionRepository.findById(transaction.getConsensusTimestamp())).thenReturn(Optional.ofNullable(ethTransaction));
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

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, ethTransaction);

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
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

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, ethTransaction);

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
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

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, ethTransaction);

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

        for (var options : tracerOptions) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash, options))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get())));

            assertEquals(options, tracerOptionsCaptor.getValue());
            assertEquals(
                    callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash),
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

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, ethTransaction);

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
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

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, ethTransaction);

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
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
        when(rateLimitBucket.tryConsume(1)).thenReturn(true);

        final var expectedMessage = StringUtils.hasText(transactionIdOrHash) ?
                "Invalid transaction ID or hash" :
                "Transaction ID or hash is required";

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
    }

    @Test
    void exceedingRateLimit() throws Exception {
        final Transaction transaction = getContractCallTransaction();
        final RecordFile recordFile = getContractCallRecordFile();
        setUp(transaction, null, recordFile);

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, null);

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get())));

            assertEquals(new OpcodeTracerOptions(), tracerOptionsCaptor.getValue());
            assertEquals(
                    callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash),
                    callServiceParametersCaptor.getValue());
        }

        when(rateLimitBucket.tryConsume(1)).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void exceedingGasLimit() throws Exception {
        final Transaction transaction = getContractCallTransaction();
        final RecordFile recordFile = getContractCallRecordFile();
        setUp(transaction, null, recordFile);

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, null);

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get())));

            assertEquals(new OpcodeTracerOptions(), tracerOptionsCaptor.getValue());
            assertEquals(
                    callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash),
                    callServiceParametersCaptor.getValue());
        }

        when(gasLimitBucket.tryConsume(anyLong())).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests());
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @Test
    void callSuccessCors() throws Exception {
        final Transaction transaction = getContractCallTransaction();
        final RecordFile recordFile = getContractCallRecordFile();
        setUp(transaction, null, recordFile);

        final TransactionIdOrHashParameter transactionIdOrHash = getTransactionIdOrHash(transaction, null);
        final String param = transactionIdOrHash.isTransactionId() ?
                transactionIdOrHash.transactionID().toString() :
                Bytes.of(transactionIdOrHash.hash().toByteArray()).toHexString();

        mockMvc.perform(options(OPCODES_URI, param)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET"));
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
                    .entityId(transaction.getEntityId().getNum())
                    .hash(Optional.ofNullable(ethTransaction)
                            .map(EthereumTransaction::getHash)
                            .orElse(transaction.getTransactionHash()))
                    .payerAccountId(transaction.getPayerAccountId().getNum())
                    .transactionResult(transaction.getResult())
                    .build();
        }

        private static OpcodesResponse opcodesResponse(final OpcodesProcessingResult result) {
            return new OpcodesResponse()
                    .contractId(result.transactionProcessingResult().getRecipient()
                            .map(EntityIdUtils::contractIdFromEvmAddress)
                            .map(contractId -> "%d.%d.%d".formatted(
                                    contractId.getShardNum(),
                                    contractId.getRealmNum(),
                                    contractId.getContractNum()))
                            .orElse(null))
                    .address(result.transactionProcessingResult().getRecipient()
                            .map(Address::toHexString)
                            .orElse(Address.ZERO.toHexString()))
                    .gas(result.transactionProcessingResult().getGasPrice())
                    .failed(!result.transactionProcessingResult().isSuccessful())
                    .returnValue(Optional.ofNullable(result.transactionProcessingResult().getOutput())
                            .map(Bytes::toHexString)
                            .orElse(Bytes.EMPTY.toHexString()))
                    .opcodes(result.opcodes().stream()
                            .map(opcode -> new com.hedera.mirror.rest.model.Opcode()
                                    .pc(opcode.pc())
                                    .op(opcode.op())
                                    .gas(opcode.gas())
                                    .gasCost(opcode.gasCost())
                                    .depth(opcode.depth())
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
                                            null)
                                    .reason(opcode.reason()))
                            .toList());
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
    public static class TestConfig {

        @Bean
        MirrorNodeEvmProperties evmProperties() {
            return new MirrorNodeEvmProperties();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        TransactionOperations transactionOperations() {
            return mock(TransactionOperations.class);
        }

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