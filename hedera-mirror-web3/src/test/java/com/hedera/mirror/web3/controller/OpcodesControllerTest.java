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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Opcode;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.CallServiceParametersBuilder;
import com.hedera.mirror.web3.service.CallServiceParametersBuilderImpl;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.EthereumTransactionService;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.TransactionService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.TransactionMocksProvider;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionID;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hyperledger.besu.datatypes.Address;
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

    @MockBean
    private Bucket bucket;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private EthereumTransactionService ethereumTransactionService;

    @MockBean
    private RecordFileService recordFileService;

    @Autowired
    private CallServiceParametersBuilder callServiceParametersBuilder;

    @Captor
    private ArgumentCaptor<CallServiceParameters> callServiceParametersCaptor;

    private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();

    private MockHttpServletRequestBuilder opcodesRequest(String transactionIdOrHash) {
        return opcodesRequest(transactionIdOrHash, true, true, true);
    }

    private MockHttpServletRequestBuilder opcodesRequest(String transactionIdOrHash,
                                                         boolean stack,
                                                         boolean memory,
                                                         boolean storage) {
        return get(OPCODES_URI, transactionIdOrHash)
                .queryParam("stack", String.valueOf(stack))
                .queryParam("memory", String.valueOf(memory))
                .queryParam("storage", String.valueOf(storage))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultMatcher responseBody(Object expectedBody) throws JsonProcessingException {
        return content().string(objectMapper.writeValueAsString(expectedBody));
    }

    private void setUp(Transaction transaction, EthereumTransaction ethTransaction, RecordFile recordFile) {
        when(bucket.tryConsume(1)).thenReturn(true);
        when(contractCallService.processOpcodeCall(callServiceParametersCaptor.capture())).thenAnswer(context -> {
            final CallServiceParameters params = context.getArgument(0);
            final var recipient = params != null ? params.getReceiver() : Address.ZERO;
            final var output = Bytes.EMPTY;
            final var result = Builder.opcodesProcessingResult(recipient, output);
            opcodesResultCaptor.set(result);
            return result;
        });
        when(ethereumTransactionService.findByHash(any(byte[].class))).thenReturn(Optional.of(ethTransaction));
        when(ethereumTransactionService.findByConsensusTimestamp(anyLong())).thenReturn(Optional.of(ethTransaction));
        when(transactionService.findByTransactionId(any(TransactionID.class))).thenReturn(Optional.of(transaction));
        when(transactionService.findByConsensusTimestamp(anyLong())).thenReturn(Optional.of(transaction));
        when(recordFileService.findRecordFileForTimestamp(anyLong())).thenReturn(Optional.of(recordFile));
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void exceedingRateLimit(Transaction transaction,
                            EthereumTransaction ethTransaction,
                            RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);

        final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();
        final var transactionIdOrHash = TransactionIdOrHashParameter.valueOf(transactionHash);

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionHash))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get())));

            assertEquals(
                    callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash),
                    callServiceParametersCaptor.getValue());
        }

        when(bucket.tryConsume(1)).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionHash))
                .andExpect(status().isTooManyRequests());
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void callRevertMethodAndExpectDetailMessage(Transaction transaction,
                                                EthereumTransaction ethTransaction,
                                                RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);

        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";
        final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();

        when(contractCallService.processOpcodeCall(any())).thenThrow(
                new MirrorEvmTransactionException(CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

        mockMvc.perform(opcodesRequest(transactionHash))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse(
                        CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage)));
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void callWithDisabledStackOrMemoryOrStorage(Transaction transaction,
                                                EthereumTransaction ethTransaction,
                                                RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);

        final var requestParams = new boolean[][] {
                { false, true, true },
                { true, false, true },
                { true, true, false },
                { false, false, false }
        };

        for (var params : requestParams) {
            final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();
            final var transactionIdOrHash = TransactionIdOrHashParameter.valueOf(transactionHash);

            mockMvc.perform(opcodesRequest(transactionHash, params[0], params[1], params[2]))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(
                            Builder.opcodesResponse(opcodesResultCaptor.get(), params[0], params[1], params[2])));

            assertEquals(
                    callServiceParametersBuilder.buildFromTransaction(transactionIdOrHash),
                    callServiceParametersCaptor.getValue());
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void callWithRecordFileNotFoundExceptionTest(Transaction transaction,
                                                 EthereumTransaction ethTransaction,
                                                 RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);
        when(recordFileService.findRecordFileForTimestamp(any())).thenReturn(Optional.empty());

        final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();
        mockMvc.perform(opcodesRequest(transactionHash))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse("Record file with transaction not found")));
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void callSuccessCors(Transaction transaction,
                         EthereumTransaction ethTransaction,
                         RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);

        final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();

        mockMvc.perform(options(OPCODES_URI, transactionHash)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    " ",
                    "0x",
                    "0xghijklmno",
                    "0x00000000000000000000000000000000000004e",
                    "0x00000000000000000000000000000000000004e2a",
                    "0x000000000000000000000000000000Z0000007e7",
                    "00000000001239847e"
            })
    void callInvalidTransactionIdOrHash(String transactionIdOrHash) throws Exception {
        final var expectedMessage = transactionIdOrHash.isBlank() ?
                "Transaction ID or hash is required" :
                "Invalid transaction ID or hash";
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
    }

    /**
     * Utility class with helper methods for building different objects in the tests
     */
    @UtilityClass
    private static class Builder {

        private static OpcodesResponse opcodesResponse(OpcodesProcessingResult result) {
            return opcodesResponse(result, true, true, true);
        }

        private static OpcodesResponse opcodesResponse(OpcodesProcessingResult result,
                                                       boolean stack,
                                                       boolean memory,
                                                       boolean storage) {
            return new OpcodesResponse()
                    .contractId(result.transactionProcessingResult().getRecipient()
                            .map(EntityIdUtils::contractIdFromEvmAddress)
                            .map(ContractID::toString)
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
                                    .stack(stack ?
                                            opcode.stack().stream()
                                                    .map(Bytes::toHexString)
                                                    .toList() :
                                            List.of())
                                    .memory(memory ?
                                            opcode.memory().stream()
                                                    .map(Bytes::toHexString)
                                                    .toList() :
                                            List.of())
                                    .storage(storage ?
                                            opcode.storage().entrySet().stream()
                                                    .collect(Collectors.toMap(
                                                            Map.Entry::getKey,
                                                            entry -> entry.getValue().toHexString())) :
                                            Map.of())
                                    .reason(opcode.reason()))
                            .toList());
        }

        private static OpcodesProcessingResult opcodesProcessingResult(final Address recipient,
                                                                       final Bytes output) {
            final List<Opcode> opcodes = opcodes();
            final long gasUsed = opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L);
            final long gasCost = opcodes.stream().map(Opcode::gasCost).reduce(Long::sum).orElse(0L);
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(HederaEvmTransactionProcessingResult
                            .successful(List.of(), gasUsed , 0, gasCost, output, recipient))
                    .opcodes(opcodes)
                    .build();
        }

        private static List<Opcode> opcodes() {
            return Arrays.asList(
                    new Opcode(
                            1273,
                            "PUSH1",
                            2731,
                            3,
                            2,
                            Arrays.asList(
                                    Bytes.fromHexString("000000000000000000000000000000000000000000000000000000004700d305"),
                                    Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a7")
                            ),
                            Arrays.asList(
                                    Bytes.fromHexString("4e487b7100000000000000000000000000000000000000000000000000000000"),
                                    Bytes.fromHexString("0000001200000000000000000000000000000000000000000000000000000000")
                            ),
                            Collections.emptyMap(),
                            null
                    ),
                    new Opcode(
                            1275,
                            "REVERT",
                            2728,
                            0,
                            2,
                            Arrays.asList(
                                    Bytes.fromHexString("000000000000000000000000000000000000000000000000000000004700d305"),
                                    Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a7")
                            ),
                            Arrays.asList(
                                    Bytes.fromHexString("4e487b7100000000000000000000000000000000000000000000000000000000"),
                                    Bytes.fromHexString("0000001200000000000000000000000000000000000000000000000000000000")
                            ),
                            Collections.emptyMap(),
                            "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"
                    ),
                    new Opcode(
                            682,
                            "SWAP2",
                            2776,
                            3,
                            1,
                            Arrays.asList(
                                    Bytes.fromHexString("000000000000000000000000000000000000000000000000000000000135b7d0"),
                                    Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000000a0")
                            ),
                            Arrays.asList(
                                    Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"),
                                    Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")
                            ),
                            Map.of(
                                    "0000000000000000000000000000000000000000000000000000000000000000",
                                    Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000014")
                            ),
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
        CallServiceParametersBuilder callServiceParametersBuilder(TransactionService transactionService,
                                                                  EthereumTransactionService ethereumTransactionService,
                                                                  RecordFileService recordFileService) {
            return new CallServiceParametersBuilderImpl(
                    transactionService,
                    ethereumTransactionService,
                    recordFileService
            );
        }
    }
}
