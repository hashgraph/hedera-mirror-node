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

import static com.hedera.mirror.common.util.CommonUtils.instant;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.web3.utils.TransactionProviderEnum.entityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.ContractResultRepository;
import com.hedera.mirror.web3.repository.ContractTransactionHashRepository;
import com.hedera.mirror.web3.repository.EthereumTransactionRepository;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.repository.TransactionRepository;
import com.hedera.mirror.web3.service.ContractDebugService;
import com.hedera.mirror.web3.service.OpcodeService;
import com.hedera.mirror.web3.service.OpcodeServiceImpl;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.RecordFileServiceImpl;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.TransactionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.Key;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
    private static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @MockBean
    private ContractDebugService contractDebugService;

    @MockBean(name = "rateLimitBucket")
    private Bucket rateLimitBucket;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private EthereumTransactionRepository ethereumTransactionRepository;

    @MockBean
    private ContractTransactionHashRepository contractTransactionHashRepository;

    @MockBean
    private ContractResultRepository contractResultRepository;

    @MockBean
    private RecordFileRepository recordFileRepository;

    @MockBean
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> callServiceParametersCaptor;

    @Captor
    private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

    private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();

    private final AtomicReference<ContractDebugParameters> expectedCallServiceParameters = new AtomicReference<>();

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter) {
        return opcodesRequest(parameter, new OpcodeTracerOptions());
    }

    private MockHttpServletRequestBuilder opcodesRequest(
            final TransactionIdOrHashParameter parameter, final OpcodeTracerOptions options) {
        final String transactionIdOrHash =
                switch (parameter) {
                    case TransactionHashParameter hashParameter -> hashParameter
                            .hash()
                            .toHexString();
                    case TransactionIdParameter transactionIdParameter -> Builder.transactionIdString(
                            transactionIdParameter.payerAccountId(), transactionIdParameter.validStart());
                };

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

    @BeforeEach
    void setUp() {
        when(rateLimitBucket.tryConsume(anyLong())).thenReturn(true);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenAnswer(context -> {
                    final ContractDebugParameters params = context.getArgument(0);
                    final OpcodeTracerOptions options = context.getArgument(1);
                    opcodesResultCaptor.set(Builder.successfulOpcodesProcessingResult(params, options));
                    return opcodesResultCaptor.get();
                });
    }

    TransactionIdOrHashParameter setUp(final TransactionProviderEnum provider) {
        provider.init(DOMAIN_BUILDER);

        final var transaction = provider.getTransaction().get();
        final var ethTransaction = provider.getEthTransaction().get();
        final var recordFile = provider.getRecordFile().get();
        final var contractTransactionHash =
                provider.getContractTransactionHash().get();
        final var contractResult = provider.getContractResult().get();
        final var contractEntity = provider.getContractEntity().get();
        final var senderEntity = provider.getSenderEntity().get();

        final var hash = provider.hasEthTransaction() ? ethTransaction.getHash() : transaction.getTransactionHash();
        final var consensusTimestamp = transaction.getConsensusTimestamp();
        final var payerAccountId = transaction.getPayerAccountId();
        final var validStartNs = transaction.getValidStartNs();
        final var senderId = contractResult.getSenderId();
        final var senderAddress = entityAddress(senderEntity);
        final var contractId = transaction.getEntityId();
        final var contractAddress = entityAddress(contractEntity);

        expectedCallServiceParameters.set(ContractDebugParameters.builder()
                .sender(new HederaEvmAccount(senderAddress))
                .receiver(contractAddress)
                .gas(provider.hasEthTransaction() ? ethTransaction.getGasLimit() : contractResult.getGasLimit())
                .value(
                        provider.hasEthTransaction()
                                ? new BigInteger(ethTransaction.getValue()).longValue()
                                : contractResult.getAmount())
                .callData(
                        provider.hasEthTransaction()
                                ? Bytes.of(ethTransaction.getCallData())
                                : Bytes.of(contractResult.getFunctionParameters()))
                .block(BlockType.of(recordFile.getIndex().toString()))
                .build());

        when(contractTransactionHashRepository.findByHash(hash)).thenReturn(Optional.of(contractTransactionHash));
        when(transactionRepository.findByPayerAccountIdAndValidStartNs(payerAccountId, validStartNs))
                .thenReturn(Optional.of(transaction));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        contractTransactionHash.getConsensusTimestamp(), payerAccountId))
                .thenReturn(Optional.ofNullable(ethTransaction));
        when(contractResultRepository.findById(consensusTimestamp)).thenReturn(Optional.of(contractResult));
        when(recordFileRepository.findByTimestamp(consensusTimestamp)).thenReturn(Optional.of(recordFile));
        when(entityDatabaseAccessor.evmAddressFromId(contractId, Optional.empty()))
                .thenReturn(contractAddress);
        when(entityDatabaseAccessor.evmAddressFromId(senderId, Optional.empty()))
                .thenReturn(senderAddress);
        when(entityDatabaseAccessor.get(contractAddress, Optional.empty())).thenReturn(Optional.of(contractEntity));
        when(entityDatabaseAccessor.get(senderAddress, Optional.empty())).thenReturn(Optional.of(senderEntity));

        if (ethTransaction != null) {
            return new TransactionHashParameter(Bytes.of(ethTransaction.getHash()));
        } else {
            return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
        }
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callThrowsExceptionAndExpectDetailMessage(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";

        reset(contractDebugService);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenThrow(new MirrorEvmTransactionException(
                        CONTRACT_EXECUTION_EXCEPTION, detailedErrorMessage, hexDataErrorMessage));

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse(
                        CONTRACT_EXECUTION_EXCEPTION.name(), detailedErrorMessage, hexDataErrorMessage)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void unsuccessfulCall(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        reset(contractDebugService);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenAnswer(context -> {
                    final OpcodeTracerOptions options = context.getArgument(1);
                    opcodesResultCaptor.set(Builder.unsuccessfulOpcodesProcessingResult(options));
                    return opcodesResultCaptor.get();
                });

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentTracerOptions")
    void callWithDifferentCombinationsOfTracerOptions(
            final TransactionProviderEnum providerEnum, final OpcodeTracerOptions options) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        mockMvc.perform(opcodesRequest(transactionIdOrHash, options))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

        assertThat(tracerOptionsCaptor.getValue()).isEqualTo(options);
        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithContractResultNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        when(contractResultRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(new GenericErrorResponse("Contract result not found")));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithTransactionNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final GenericErrorResponse expectedError =
                switch (transactionIdOrHash) {
                    case TransactionHashParameter parameter -> {
                        reset(contractTransactionHashRepository);
                        when(contractTransactionHashRepository.findByHash(
                                        parameter.hash().toArray()))
                                .thenReturn(Optional.empty());
                        yield new GenericErrorResponse("Contract transaction hash not found");
                    }
                    case TransactionIdParameter parameter -> {
                        reset(transactionRepository);
                        when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                                        parameter.payerAccountId(), convertToNanosMax(parameter.validStart())))
                                .thenReturn(Optional.empty());
                        yield new GenericErrorResponse("Transaction not found");
                    }
                };

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(expectedError));
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentSenderAddresses")
    void callWithDifferentSenderAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum)
            throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                .sender(new HederaEvmAccount(
                        entityAddress(providerEnum.getSenderEntity().get())))
                .build());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentReceiverAddresses")
    void callWithDifferentReceiverAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum)
            throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                .receiver(entityAddress(providerEnum.getContractEntity().get()))
                .build());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
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
                "0.0.1234-1-123456789-", // dash after nanos
            })
    void callInvalidTransactionIdOrHash(final String transactionIdOrHash) throws Exception {
        when(rateLimitBucket.tryConsume(1)).thenReturn(true);

        final var expectedMessage = StringUtils.hasText(transactionIdOrHash)
                ? "Unsupported ID format: '%s'".formatted(transactionIdOrHash)
                : "Missing transaction ID or hash";

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void exceedingRateLimit(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isOk())
                    .andExpect(
                            responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

            assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
        }

        when(rateLimitBucket.tryConsume(1)).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests())
                .andExpect(responseBody(new GenericErrorResponse("Rate limit exceeded.")));
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callSuccessCors(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final String param =
                switch (transactionIdOrHash) {
                    case TransactionHashParameter hashParameter -> hashParameter
                            .hash()
                            .toHexString();
                    case TransactionIdParameter transactionIdParameter -> Builder.transactionIdString(
                            transactionIdParameter.payerAccountId(), transactionIdParameter.validStart());
                };

        mockMvc.perform(options(OPCODES_URI, param)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD,POST"));
    }

    static Stream<Arguments> transactionsWithDifferentTracerOptions() {
        final List<OpcodeTracerOptions> tracerOptions = List.of(
                new OpcodeTracerOptions(true, true, true),
                new OpcodeTracerOptions(false, true, true),
                new OpcodeTracerOptions(true, false, true),
                new OpcodeTracerOptions(true, true, false),
                new OpcodeTracerOptions(false, false, true),
                new OpcodeTracerOptions(false, true, false),
                new OpcodeTracerOptions(true, false, false),
                new OpcodeTracerOptions(false, false, false));
        return Arrays.stream(TransactionProviderEnum.values())
                .flatMap(providerEnum -> tracerOptions.stream().map(options -> Arguments.of(providerEnum, options)));
    }

    static Stream<Arguments> transactionsWithDifferentSenderAddresses() {
        return Arrays.stream(TransactionProviderEnum.values()).flatMap(providerEnum -> entityAddressCombinations(
                        providerEnum.getPayerAccountId())
                .map(addressPair -> Arguments.of(Named.of(
                        "%s(evmAddress=%s, alias=%s)"
                                .formatted(
                                        providerEnum.name(),
                                        addressPair.getLeft() != null ? Bytes.of(addressPair.getLeft()) : null,
                                        addressPair.getRight() != null ? Bytes.of(addressPair.getRight()) : null),
                        providerEnum.customize(p -> {
                            p.setPayerEvmAddress(addressPair.getLeft());
                            p.setPayerAlias(addressPair.getRight());
                        })))));
    }

    static Stream<Arguments> transactionsWithDifferentReceiverAddresses() {
        return Arrays.stream(TransactionProviderEnum.values()).flatMap(providerEnum -> entityAddressCombinations(
                        providerEnum.getContractId())
                .map(addressPair -> Arguments.of(Named.of(
                        "%s(evmAddress=%s, alias=%s)"
                                .formatted(
                                        providerEnum.name(),
                                        addressPair.getLeft() != null ? Bytes.of(addressPair.getLeft()) : null,
                                        addressPair.getRight() != null ? Bytes.of(addressPair.getRight()) : null),
                        providerEnum.customize(p -> {
                            p.setContractEvmAddress(addressPair.getLeft());
                            p.setContractAlias(addressPair.getRight());
                        })))));
    }

    static Stream<Pair<byte[], byte[]>> entityAddressCombinations(EntityId entityId) {
        Supplier<byte[]> validAlias = () -> new byte[] {
            0,
            0,
            0,
            0, // shard
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0, // realm
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            Long.valueOf(entityId.getNum()).byteValue(), // num
        };
        Supplier<byte[]> invalidAlias = () -> DOMAIN_BUILDER.key(Key.KeyCase.ED25519);
        return Stream.of(
                Pair.of(DOMAIN_BUILDER.evmAddress(), validAlias.get()),
                Pair.of(null, validAlias.get()),
                Pair.of(null, invalidAlias.get()),
                Pair.of(null, null));
    }

    /**
     * Utility class with helper methods for building different objects in the tests
     */
    @UtilityClass
    private static class Builder {

        private static String transactionIdString(final EntityId payerAccountId, final Instant validStart) {
            return "%s-%d-%d".formatted(payerAccountId.toString(), validStart.getEpochSecond(), validStart.getNano());
        }

        private static OpcodesResponse opcodesResponse(
                final OpcodesProcessingResult result, final EntityDatabaseAccessor entityDatabaseAccessor) {
            return new OpcodesResponse()
                    .address(result.transactionProcessingResult()
                            .getRecipient()
                            .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                            .map(TransactionProviderEnum::entityAddress)
                            .map(Address::toHexString)
                            .orElse(Address.ZERO.toHexString()))
                    .contractId(result.transactionProcessingResult()
                            .getRecipient()
                            .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                            .map(Entity::toEntityId)
                            .map(EntityId::toString)
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
                                    .stack(opcode.stack().stream()
                                            .map(Bytes::toHexString)
                                            .toList())
                                    .memory(opcode.memory().stream()
                                            .map(Bytes::toHexString)
                                            .toList())
                                    .storage(opcode.storage().entrySet().stream()
                                            .collect(Collectors.toMap(
                                                    entry -> entry.getKey().toHexString(),
                                                    entry -> entry.getValue().toHexString()))))
                            .toList())
                    .returnValue(Optional.ofNullable(
                                    result.transactionProcessingResult().getOutput())
                            .map(Bytes::toHexString)
                            .orElse(Bytes.EMPTY.toHexString()));
        }

        private static OpcodesProcessingResult successfulOpcodesProcessingResult(
                final ContractDebugParameters params, final OpcodeTracerOptions options) {
            final Address recipient = params != null ? params.getReceiver() : Address.ZERO;
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed =
                    opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L);
            final long gasCost =
                    opcodes.stream().map(Opcode::gasCost).reduce(Long::sum).orElse(0L);
            return new OpcodesProcessingResult(
                    HederaEvmTransactionProcessingResult.successful(
                            List.of(), gasUsed, 0, gasCost, Bytes.EMPTY, recipient),
                    opcodes);
        }

        private static OpcodesProcessingResult unsuccessfulOpcodesProcessingResult(final OpcodeTracerOptions options) {
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed =
                    opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L);
            final long gasCost =
                    opcodes.stream().map(Opcode::gasCost).reduce(Long::sum).orElse(0L);
            return new OpcodesProcessingResult(
                    HederaEvmTransactionProcessingResult.failed(
                            gasUsed,
                            0,
                            gasCost,
                            Optional.of(Bytes.EMPTY),
                            Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR)),
                    opcodes);
        }

        private static List<Opcode> opcodes(final OpcodeTracerOptions options) {
            return Arrays.asList(
                    new Opcode(
                            1273,
                            "PUSH1",
                            2731,
                            3,
                            2,
                            options.isStack()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "000000000000000000000000000000000000000000000000000000004700d305"),
                                            Bytes.fromHexString(
                                                    "00000000000000000000000000000000000000000000000000000000000000a7"))
                                    : Collections.emptyList(),
                            options.isMemory()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "4e487b7100000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000001200000000000000000000000000000000000000000000000000000000"))
                                    : Collections.emptyList(),
                            Collections.emptySortedMap(),
                            null),
                    new Opcode(
                            1275,
                            "REVERT",
                            2728,
                            0,
                            2,
                            options.isStack()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "000000000000000000000000000000000000000000000000000000004700d305"),
                                            Bytes.fromHexString(
                                                    "00000000000000000000000000000000000000000000000000000000000000a7"))
                                    : Collections.emptyList(),
                            options.isMemory()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "4e487b7100000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000001200000000000000000000000000000000000000000000000000000000"))
                                    : Collections.emptyList(),
                            Collections.emptySortedMap(),
                            "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
                    new Opcode(
                            682,
                            "SWAP2",
                            2776,
                            3,
                            1,
                            options.isStack()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "000000000000000000000000000000000000000000000000000000000135b7d0"),
                                            Bytes.fromHexString(
                                                    "00000000000000000000000000000000000000000000000000000000000000a0"))
                                    : Collections.emptyList(),
                            options.isMemory()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000000"))
                                    : Collections.emptyList(),
                            options.isStorage()
                                    ? ImmutableSortedMap.of(
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000014"))
                                    : Collections.emptySortedMap(),
                            null));
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
        RecordFileService recordFileService(final RecordFileRepository recordFileRepository) {
            return new RecordFileServiceImpl(recordFileRepository);
        }

        @Bean
        OpcodeService opcodeService(
                final RecordFileService recordFileService,
                final ContractDebugService contractDebugService,
                final ContractTransactionHashRepository contractTransactionHashRepository,
                final EthereumTransactionRepository ethereumTransactionRepository,
                final TransactionRepository transactionRepository,
                final ContractResultRepository contractResultRepository,
                final EntityDatabaseAccessor entityDatabaseAccessor) {
            return new OpcodeServiceImpl(
                    recordFileService,
                    contractDebugService,
                    contractTransactionHashRepository,
                    ethereumTransactionRepository,
                    transactionRepository,
                    contractResultRepository,
                    entityDatabaseAccessor);
        }
    }
}
