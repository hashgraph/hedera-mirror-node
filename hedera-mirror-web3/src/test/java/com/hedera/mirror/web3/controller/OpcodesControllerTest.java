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

import static com.hedera.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static com.hedera.mirror.common.domain.transaction.TransactionType.CONTRACTCREATEINSTANCE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static com.hedera.mirror.common.util.CommonUtils.instant;
import static com.hedera.mirror.common.util.CommonUtils.nextBytes;
import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.common.util.DomainUtils.convertToNanosMax;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
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
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.OpcodeService;
import com.hedera.mirror.web3.service.OpcodeServiceImpl;
import com.hedera.mirror.web3.service.RecordFileService;
import com.hedera.mirror.web3.service.RecordFileServiceImpl;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    /**
     * ETH Transaction Types
     */
    private static final int LEGACY_TYPE_BYTE = 0;
    private static final int EIP2930_TYPE_BYTE = 1;
    private static final int EIP1559_TYPE_BYTE = 2;

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
    private ContractResultRepository contractResultRepository;

    @MockBean
    private RecordFileRepository recordFileRepository;

    @MockBean
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Captor
    private ArgumentCaptor<CallServiceParameters> callServiceParametersCaptor;

    @Captor
    private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

    private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();

    private final AtomicReference<CallServiceParameters> expectedCallServiceParameters = new AtomicReference<>();

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter) {
        return opcodesRequest(parameter, new OpcodeTracerOptions());
    }

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter,
                                                         final OpcodeTracerOptions options) {
        final String transactionIdOrHash = switch (parameter) {
            case TransactionHashParameter hashParameter -> hashParameter.hash().toHexString();
            case TransactionIdParameter transactionIdParameter -> Builder.transactionIdString(
                    transactionIdParameter.payerAccountId(),
                    transactionIdParameter.validStart()
            );
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
    }

    TransactionIdOrHashParameter setUp(final TransactionProviderEnum provider) {
        final var transaction = provider.getTransaction();
        final var ethTransaction = provider.getEthTransaction();
        final var recordFile = provider.getRecordFile();
        final var contractTransactionHash = provider.getContractTransactionHash();
        final var contractResult = provider.getContractResult();
        final var contractEntity = provider.getContractEntity();
        final var senderEntity = provider.getSenderEntity();

        final var hash = ethTransaction != null ? ethTransaction.getHash() : transaction.getTransactionHash();
        final var consensusTimestamp = transaction.getConsensusTimestamp();
        final var payerAccountId = transaction.getPayerAccountId();
        final var validStartNs = transaction.getValidStartNs();
        final var senderId = contractResult.getSenderId();
        final var senderAddress = Address.wrap(Bytes.wrap(senderEntity.getEvmAddress()));
        final var contractId = transaction.getEntityId();
        final var contractAddress = Address.wrap(Bytes.wrap(contractEntity.getEvmAddress()));

        expectedCallServiceParameters.set(CallServiceParameters.builder()
                .sender(new HederaEvmAccount(senderAddress))
                .receiver(contractAddress)
                .gas(ethTransaction != null ? ethTransaction.getGasLimit() : contractResult.getGasLimit())
                .value(ethTransaction != null ?
                        new BigInteger(ethTransaction.getValue()).longValue() : contractResult.getAmount())
                .callData(Bytes.of(
                        ethTransaction != null ? ethTransaction.getCallData() : contractResult.getFunctionParameters()))
                .isStatic(false)
                .callType(ETH_DEBUG_TRACE_TRANSACTION)
                .isEstimate(false)
                .block(BlockType.of(recordFile.getIndex().toString()))
                .build());

        when(contractTransactionHashRepository.findById(hash)).thenReturn(Optional.of(contractTransactionHash));
        when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                payerAccountId,
                validStartNs
        )).thenReturn(Optional.of(transaction));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                contractTransactionHash.getConsensusTimestamp(),
                payerAccountId
        )).thenReturn(Optional.ofNullable(ethTransaction));
        when(contractResultRepository.findById(consensusTimestamp)).thenReturn(Optional.of(contractResult));
        when(recordFileRepository.findByTimestamp(consensusTimestamp)).thenReturn(Optional.of(recordFile));
        when(entityDatabaseAccessor.evmAddressFromId(contractId, Optional.empty())).thenReturn(contractAddress);
        when(entityDatabaseAccessor.evmAddressFromId(senderId, Optional.empty())).thenReturn(senderAddress);
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
    void shouldThrowUnsupportedOperationFromContractCallService(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        reset(contractCallService);
        when(contractCallService.processOpcodeCall(
                callServiceParametersCaptor.capture(),
                tracerOptionsCaptor.capture()
        )).thenCallRealMethod();

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotImplemented())
                .andExpect(responseBody(new GenericErrorResponse("Not implemented")));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callRevertMethodAndExpectDetailMessage(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";

        reset(contractCallService);
        when(contractCallService.processOpcodeCall(
                callServiceParametersCaptor.capture(),
                tracerOptionsCaptor.capture()
        )).thenThrow(new MirrorEvmTransactionException(CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse(
                        CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithDifferentCombinationsOfTracerOptions(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

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
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

            assertEquals(options, tracerOptionsCaptor.getValue());
            assertEquals(expectedCallServiceParameters.get(), callServiceParametersCaptor.getValue());
        }
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
    void callWithContractNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        when(entityDatabaseAccessor.get(any(Address.class), eq(Optional.empty()))).thenReturn(Optional.empty());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(new GenericErrorResponse("Contract not found")));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithTransactionNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final GenericErrorResponse expectedError = switch (transactionIdOrHash) {
            case TransactionHashParameter parameter -> {
                when(contractTransactionHashRepository.findById(parameter.hash().toArray())).thenReturn(Optional.empty());
                yield new GenericErrorResponse("Contract transaction hash not found");
            }
            case TransactionIdParameter parameter -> {
                when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        parameter.payerAccountId(),
                        convertToNanosMax(parameter.validStart())
                )).thenReturn(Optional.empty());
                yield new GenericErrorResponse("Transaction not found");
            }
        };

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(expectedError));
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
                "Unsupported ID format: '%s'".formatted(transactionIdOrHash) :
                "Missing transaction ID or hash";

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
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

            assertEquals(new OpcodeTracerOptions(), tracerOptionsCaptor.getValue());
            assertEquals(expectedCallServiceParameters.get(), callServiceParametersCaptor.getValue());
        }

        when(rateLimitBucket.tryConsume(1)).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void exceedingGasLimit(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), entityDatabaseAccessor)));

            assertEquals(new OpcodeTracerOptions(), tracerOptionsCaptor.getValue());
            assertEquals(expectedCallServiceParameters.get(), callServiceParametersCaptor.getValue());
        }

        when(gasLimitBucket.tryConsume(anyLong())).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests());
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callSuccessCors(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final String param = switch (transactionIdOrHash) {
            case TransactionHashParameter hashParameter -> hashParameter.hash().toHexString();
            case TransactionIdParameter transactionIdParameter -> Builder.transactionIdString(
                    transactionIdParameter.payerAccountId(),
                    transactionIdParameter.validStart()
            );
        };

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

        private static String transactionIdString(final EntityId payerAccountId, final Instant validStart) {
            return "%s-%d-%d".formatted(payerAccountId.toString(), validStart.getEpochSecond(), validStart.getNano());
        }

        private static OpcodesResponse opcodesResponse(final OpcodesProcessingResult result,
                                                       final EntityDatabaseAccessor entityDatabaseAccessor) {
            return new OpcodesResponse()
                    .address(result.transactionProcessingResult().getRecipient()
                            .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                            .map(entity -> {
                                if (entity.getEvmAddress() != null) {
                                    return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
                                }
                                if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
                                    return Address.wrap(Bytes.wrap(entity.getAlias()));
                                }
                                return toAddress(entity.toEntityId());
                            })
                            .map(Address::toHexString)
                            .orElse(Address.ZERO.toHexString()))
                    .contractId(result.transactionProcessingResult().getRecipient()
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

    @Getter
    @RequiredArgsConstructor
    enum TransactionProviderEnum {
        CONTRACT_CREATE(Instant.ofEpochSecond(1, 2000), CONTRACTCREATEINSTANCE, LEGACY_TYPE_BYTE),
        CONTRACT_CALL(Instant.ofEpochSecond(2, 3000), CONTRACTCALL, LEGACY_TYPE_BYTE),
        EIP1559(Instant.ofEpochSecond(3, 4000), ETHEREUMTRANSACTION, EIP1559_TYPE_BYTE),
        EIP2930(Instant.ofEpochSecond(4, 5000), ETHEREUMTRANSACTION, EIP2930_TYPE_BYTE),
        LEGACY(Instant.ofEpochSecond(5, 6000), ETHEREUMTRANSACTION, LEGACY_TYPE_BYTE);

        private final long amount = 1000L;
        private final byte[] hash = nextBytes(32);
        private final EntityId payerAccountId = DOMAIN_BUILDER.entityId();
        private final EntityId contractId = DOMAIN_BUILDER.entityId();
        private final Instant consensusTimestamp;
        private final TransactionType transactionType;
        private final int typeByte;

        public Transaction getTransaction() {
            return DOMAIN_BUILDER.transaction()
                    .customize(tx -> {
                        tx.type(transactionType.getProtoId());
                        tx.memo("%s_%d".formatted(transactionType.name(), typeByte).getBytes());
                        tx.transactionHash(hash);
                        tx.payerAccountId(payerAccountId);
                        tx.entityId(contractId);
                        tx.validStartNs(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano() - 1000));
                        tx.consensusTimestamp(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano()));
                    })
                    .get();
        }

        public EthereumTransaction getEthTransaction() {
            return DOMAIN_BUILDER.ethereumTransaction(true)
                    .customize(tx -> {
                        tx.type(typeByte);
                        tx.hash(hash);
                        tx.value(ByteBuffer.allocate(Long.BYTES).putLong(amount).array());
                        tx.payerAccountId(payerAccountId);
                        tx.consensusTimestamp(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano()));
                        if (typeByte == EIP1559_TYPE_BYTE) {
                            tx.maxGasAllowance(Long.MAX_VALUE);
                            tx.maxFeePerGas(nextBytes(32));
                            tx.maxPriorityFeePerGas(nextBytes(32));
                        }
                        if (typeByte == EIP2930_TYPE_BYTE) {
                            tx.accessList(nextBytes(100));
                        }
                    })
                    .get();
        }

        public RecordFile getRecordFile() {
            return DOMAIN_BUILDER.recordFile()
                    .customize(recordFile -> {
                        recordFile.consensusStart(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano() - 1000));
                        recordFile.consensusEnd(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano() + 1000));
                    })
                    .get();
        }

        public ContractTransactionHash getContractTransactionHash() {
            return DOMAIN_BUILDER.contractTransactionHash()
                    .customize(contractTransactionHash -> {
                        contractTransactionHash.consensusTimestamp(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano()));
                        contractTransactionHash.entityId(contractId.getId());
                        contractTransactionHash.hash(hash);
                        contractTransactionHash.payerAccountId(payerAccountId.getId());
                        contractTransactionHash.transactionResult(ResponseCodeEnum.SUCCESS_VALUE);
                    })
                    .get();
        }

        public ContractResult getContractResult() {
            return DOMAIN_BUILDER.contractResult()
                    .customize(result -> {
                        result.amount(amount);
                        result.consensusTimestamp(convertToNanosMax(
                                consensusTimestamp.getEpochSecond(),
                                consensusTimestamp.getNano()));
                        result.contractId(contractId.getId());
                        result.createdContractIds(transactionType == CONTRACTCREATEINSTANCE ?
                                List.of(contractId.getId()) : Collections.emptyList());
                        result.functionParameters(ByteString.copyFrom(nextBytes(256)).toByteArray());
                        result.payerAccountId(payerAccountId);
                        result.senderId(payerAccountId);
                        result.transactionHash(hash);
                    }).get();
        }

        public Entity getContractEntity() {
            final long createdAt = convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano());
            return DOMAIN_BUILDER.entity(contractId.getId(), createdAt).get();
        }

        public Entity getSenderEntity() {
            return DOMAIN_BUILDER.entity(payerAccountId.getId(), DOMAIN_BUILDER.timestamp()).get();
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
        OpcodeService opcodeService(final RecordFileService recordFileService,
                                    final ContractTransactionHashRepository contractTransactionHashRepository,
                                    final EthereumTransactionRepository ethereumTransactionRepository,
                                    final TransactionRepository transactionRepository,
                                    final ContractResultRepository contractResultRepository,
                                    final EntityDatabaseAccessor entityDatabaseAccessor) {
            return new OpcodeServiceImpl(
                    recordFileService,
                    contractTransactionHashRepository,
                    ethereumTransactionRepository,
                    transactionRepository,
                    contractResultRepository,
                    entityDatabaseAccessor
            );
        }
    }
}
