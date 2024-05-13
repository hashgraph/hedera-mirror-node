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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.EthereumTransactionService;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.TransactionService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.TransactionMocksProvider;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hederahashgraph.api.proto.java.TransactionID;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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

    void setUp(Transaction transaction, EthereumTransaction ethTransaction, RecordFile recordFile) {
        given(bucket.tryConsume(1)).willReturn(true);
        given(contractCallService.processOpcodeCall(any())).willAnswer(context -> {
            final CallServiceParameters params = context.getArgument(0);
            final var gas = params != null ? params.getGas() : 0L;
            final var recipient = params != null ? params.getReceiver() : Address.ZERO;
            return OpcodesProcessingResult.builder()
                    .transactionProcessingResult(HederaEvmTransactionProcessingResult
                            .successful(List.of(), gas , 0, gas, null, recipient))
                    .opcodes(List.of())
                    .build();
        });

        when(ethereumTransactionService.findByHash(any(byte[].class))).thenReturn(Optional.of(ethTransaction));
        when(ethereumTransactionService.findByConsensusTimestamp(anyLong())).thenReturn(Optional.of(ethTransaction));
        when(transactionService.findByTransactionId(any(TransactionID.class))).thenReturn(Optional.of(transaction));
        when(transactionService.findByConsensusTimestamp(anyLong())).thenReturn(Optional.of(transaction));
        when(recordFileService.findRecordFileForTimestamp(anyLong())).thenReturn(Optional.of(recordFile));
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }

    @SneakyThrows
    private ResultActions contractOpcodes(String transactionIdOrHash) {
        return mockMvc.perform(post(OPCODES_URI, transactionIdOrHash)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void exceedingRateLimit(Transaction transaction,
                            EthereumTransaction ethTransaction,
                            RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);

        final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();
        for (var i = 0; i < 3; i++) {
            contractOpcodes(transactionHash).andExpect(status().isOk());
        }

        given(bucket.tryConsume(1)).willReturn(false);
        contractOpcodes(transactionHash).andExpect(status().isTooManyRequests());
    }

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
    @ParameterizedTest
    void callInvalidTransactionIdOrHash(String transactionIdOrHash) throws Exception {
        final var expectedMessage = transactionIdOrHash.isBlank() ?
                "Transaction ID or hash is required" :
                "Invalid transaction ID or hash";
        contractOpcodes(transactionIdOrHash)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
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

        given(contractCallService.processOpcodeCall(any())).willThrow(
                new MirrorEvmTransactionException(CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

        contractOpcodes(transactionHash)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage))));
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void callValidBlockType(Transaction transaction,
                            EthereumTransaction ethTransaction,
                            RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);

        final var blockTypeStrings =
                new String[] { null, "", "earliest", "latest", "0", "0x1a", "pending", "safe", "finalized" };

        for (String blockType : blockTypeStrings) {
            given(recordFileService.findRecordFileForTimestamp(any()))
                    .willReturn(Optional.of(recordFile.toBuilder()
                            .index(BlockType.of(blockType).number())
                            .build()));

            final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();
            contractOpcodes(transactionHash).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TransactionMocksProvider.class)
    void callWithRecordFileNotFoundExceptionTest(Transaction transaction,
                                                 EthereumTransaction ethTransaction,
                                                 RecordFile recordFile) throws Exception {
        setUp(transaction, ethTransaction, recordFile);
        given(recordFileService.findRecordFileForTimestamp(any())).willReturn(Optional.empty());

        final var transactionHash = Bytes.of(ethTransaction.getHash()).toHexString();
        contractOpcodes(transactionHash)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse("Record file with transaction not found"))));
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
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "POST"));
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
    }
}
