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

import static com.hedera.mirror.common.util.CommonUtils.instant;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doAnswer;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.service.model.ContractCallDebugServiceParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.utils.ResultCaptor;
import com.hedera.mirror.web3.utils.TransactionProviderEnum;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

class OpcodeServiceTest extends ContractCallTestSetup {

    public static final long AMOUNT = 0L;
    public static final long GAS = 15_000_000L;

    @SpyBean
    private ContractDebugService contractCallService;

    @Autowired
    private OpcodeService opcodeService;

    @Autowired
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("processOpcodeCall")
    class ProcessOpcodeCall {

        @Captor
        private ArgumentCaptor<ContractCallDebugServiceParameters> serviceParametersCaptor;

        @Captor
        private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

        private final ResultCaptor<OpcodesProcessingResult> opcodesResultCaptor = new ResultCaptor<>(OpcodesProcessingResult.class);

        private final AtomicReference<ContractCallDebugServiceParameters> expectedServiceParameters = new AtomicReference<>();

        @BeforeEach
        void setUp() {
            genesisBlockPersist();
            historicalBlocksPersist();
            historicalDataPersist();
            precompileContractPersist();
            fileDataPersist();
            final var ownerEntityId = ownerEntityPersist();
            final var spenderEntityId = spenderEntityPersist();
            final var senderEntityId = senderEntityPersist();
            fungibleTokenPersist(
                    senderEntityId,
                    KEY_PROTO,
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    AUTO_RENEW_ACCOUNT_ADDRESS,
                    9999999999999L,
                    TokenPauseStatusEnum.UNPAUSED,
                    false);
            nftPersist(
                    NFT_TRANSFER_ADDRESS,
                    AUTO_RENEW_ACCOUNT_ADDRESS,
                    ownerEntityId,
                    spenderEntityId,
                    ownerEntityId,
                    KEY_PROTO,
                    TokenPauseStatusEnum.UNPAUSED,
                    false);
            exchangeRatesPersist();
            feeSchedulesPersist();
        }

        @BeforeEach
        void setUpArgumentCaptors() {
            expectedServiceParameters.set(null);
            doAnswer(opcodesResultCaptor)
                    .when(contractCallService)
                    .processOpcodeCall(
                            serviceParametersCaptor.capture(),
                            tracerOptionsCaptor.capture());
        }

        @SneakyThrows
        TransactionIdOrHashParameter setUp(final ContractFunctionProviderEnum provider,
                                           final TransactionType transactionType,
                                           final Address contractAddress,
                                           final Path contractAbiPath,
                                           final boolean persistTransaction,
                                           final boolean persistContractResult) {
            final var contractEntityId = fromEvmAddress(contractAddress.toArrayUnsafe());
            final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
            assertThat(contractEntityId).isNotNull();
            assertThat(senderEntityId).isNotNull();

            final var ethHash = domainBuilder.bytes(32);
            final var consensusTimestamp = domainBuilder.timestamp();
            final var validStartNs = consensusTimestamp - 1;
            final var callData = functionEncodeDecoder
                    .functionHashFor(provider.getName(), contractAbiPath, provider.getFunctionParameters())
                    .toArray();

            final var transactionBuilder = domainBuilder.transaction()
                    .customize(tx -> tx
                            .consensusTimestamp(consensusTimestamp)
                            .entityId(contractEntityId)
                            .payerAccountId(senderEntityId)
                            .type(transactionType.getProtoId())
                            .validStartNs(validStartNs));
            final var transaction = persistTransaction ? transactionBuilder.persist() : transactionBuilder.get();

            final EthereumTransaction ethTransaction;
            if (transactionType == TransactionType.ETHEREUMTRANSACTION) {
                final var ethTransactionBuilder = domainBuilder.ethereumTransaction(false)
                        .customize(t -> t
                                .callData(callData)
                                .consensusTimestamp(consensusTimestamp)
                                .gasLimit(GAS)
                                .hash(ethHash)
                                .payerAccountId(senderEntityId)
                                .toAddress(contractAddress.toArray())
                                .value(BigInteger.valueOf(AMOUNT).toByteArray()));
                ethTransaction = persistTransaction ? ethTransactionBuilder.persist() : ethTransactionBuilder.get();
            } else {
                ethTransaction = null;
            }

            final var contractResultBuilder = domainBuilder.contractResult()
                    .customize(r -> r
                            .amount(AMOUNT)
                            .consensusTimestamp(consensusTimestamp)
                            .contractId(contractEntityId.getId())
                            .functionParameters(callData)
                            .gasLimit(GAS)
                            .senderId(senderEntityId)
                            .transactionHash(transaction.getTransactionHash()));
            final var contractResult = persistContractResult ? contractResultBuilder.persist() : contractResultBuilder.get();

            final var expectedResult = provider.getExpectedResultFields() != null ?
                    Bytes.fromHexString(functionEncodeDecoder
                            .encodedResultFor(provider.getName(), contractAbiPath, provider.getExpectedResultFields()))
                            .toArray():
                    null;
            final var expectedError = provider.getExpectedErrorMessage() != null ?
                    provider.getExpectedErrorMessage().getBytes() :
                    null;

            domainBuilder.contractAction()
                    .customize(a -> a
                            .caller(senderEntityId)
                            .callerType(EntityType.ACCOUNT)
                            .consensusTimestamp(consensusTimestamp)
                            .payerAccountId(senderEntityId)
                            .recipientContract(contractEntityId)
                            .recipientAddress(contractAddress.toArrayUnsafe())
                            .gas(GAS)
                            .resultData(expectedError != null ? expectedError : expectedResult)
                            .resultDataType(expectedError != null ? REVERT_REASON.getNumber() : OUTPUT.getNumber())
                            .value(AMOUNT))
                    .persist();

            if (persistTransaction) {
                domainBuilder.contractTransactionHash()
                        .customize(h -> h
                                .consensusTimestamp(consensusTimestamp)
                                .entityId(contractEntityId.getId())
                                .hash(ethHash)
                                .payerAccountId(senderEntityId.getId())
                                .transactionResult(contractResult.getTransactionResult()))
                        .persist();
            }

            expectedServiceParameters.set(ContractCallDebugServiceParameters.builder()
                    .sender(new HederaEvmAccount(SENDER_ALIAS))
                    .receiver(entityDatabaseAccessor.get(contractAddress, Optional.empty())
                            .map(TransactionProviderEnum::entityAddress)
                            .orElse(Address.ZERO))
                    .gas(GAS)
                    .value(AMOUNT)
                    .callData(Bytes.of(callData))
                    .block(provider.getBlock())
                    .build());

            if (ethTransaction != null) {
                return new TransactionHashParameter(Bytes.of(ethTransaction.getHash()));
            } else {
                return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
            }
        }

        static Stream<Arguments> tracerOptions() {
            return Stream
                    .of(
                        new OpcodeTracerOptions(true, true, true),
                        new OpcodeTracerOptions(false, true, true),
                        new OpcodeTracerOptions(true, false, true),
                        new OpcodeTracerOptions(true, true, false),
                        new OpcodeTracerOptions(false, false, true),
                        new OpcodeTracerOptions(false, true, false),
                        new OpcodeTracerOptions(true, false, false),
                        new OpcodeTracerOptions(false, false, false))
                    .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("tracerOptions")
        void callWithDifferentCombinationsOfTracerOptions(final OpcodeTracerOptions options) {
            dynamicEthCallContractPresist();
            final var providerEnum = ContractCallDynamicCallsTest.DynamicCallsContractFunctions.MINT_NFT;

            final TransactionIdOrHashParameter transactionIdOrHash = setUp(
                    providerEnum,
                    TransactionType.ETHEREUMTRANSACTION,
                    DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS,
                    DYNAMIC_ETH_CALLS_ABI_PATH,
                    true,
                    true);

            final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

            verifyOpcodesResponse(providerEnum, DYNAMIC_ETH_CALLS_ABI_PATH, opcodesResponse, options);
        }

        @ParameterizedTest
        @EnumSource(ContractCallNestedCallsTest.NestedEthCallContractFunctions.class)
        void callWithNestedEthCalls(final ContractFunctionProviderEnum providerEnum) {
            nestedEthCallsContractPersist();

            final TransactionIdOrHashParameter transactionIdOrHash = setUp(
                    providerEnum,
                    TransactionType.ETHEREUMTRANSACTION,
                    NESTED_ETH_CALLS_CONTRACT_ADDRESS,
                    NESTED_CALLS_ABI_PATH,
                    true,
                    true);
            final OpcodeTracerOptions options = new OpcodeTracerOptions();

            final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

            verifyOpcodesResponse(providerEnum, NESTED_CALLS_ABI_PATH, opcodesResponse, options);
        }

        @Test
        void callWithContractResultNotFoundExceptionTest() {
            systemExchangeRateContractPersist();

            final TransactionIdOrHashParameter transactionIdOrHash = setUp(
                    ContractCallSystemPrecompileTest.ExchangeRateFunctions.TINYBARS_TO_TINYCENTS,
                    TransactionType.CONTRACTCALL,
                    EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS,
                    EXCHANGE_RATE_PRECOMPILE_ABI_PATH,
                    true,
                    false);
            final OpcodeTracerOptions options = new OpcodeTracerOptions();

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                    .withMessage("Contract result not found");
        }

        @Test
        void callWithTransactionNotFoundExceptionTest() {
            systemExchangeRateContractPersist();

            final TransactionIdOrHashParameter transactionIdOrHash = setUp(
                    ContractCallSystemPrecompileTest.ExchangeRateFunctions.TINYCENTS_TO_TINYBARS,
                    TransactionType.CONTRACTCALL,
                    EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS,
                    EXCHANGE_RATE_PRECOMPILE_ABI_PATH,
                    false,
                    true);
            final OpcodeTracerOptions options = new OpcodeTracerOptions();

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                    .withMessage("Transaction not found");
        }

        @Test
        void callWithContractTransactionHashNotFoundExceptionTest() {
            nestedEthCallsContractPersist();

            final TransactionIdOrHashParameter transactionIdOrHash = setUp(
                    ContractCallNestedCallsTest.NestedEthCallContractFunctions.CREATE_FUNGIBLE_TOKEN_NO_KEYS,
                    TransactionType.ETHEREUMTRANSACTION,
                    NESTED_ETH_CALLS_CONTRACT_ADDRESS,
                    NESTED_CALLS_ABI_PATH,
                    false,
                    true);
            final OpcodeTracerOptions options = new OpcodeTracerOptions();

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                    .withMessage("Contract transaction hash not found");
        }

        private void verifyOpcodesResponse(final ContractFunctionProviderEnum providerEnum,
                                           final Path contractAbiPath,
                                           final OpcodesResponse opcodesResponse,
                                           final OpcodeTracerOptions options) {
            assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(opcodesResultCaptor.getValue()));
            assertThat(serviceParametersCaptor.getValue()).isEqualTo(expectedServiceParameters.get());
            assertThat(tracerOptionsCaptor.getValue()).isEqualTo(options);

            if (providerEnum.getExpectedErrorMessage() != null) {
                assertThat(opcodesResponse.getOpcodes().getLast().getReason())
                        .isEqualTo(Hex.encodeHexString(providerEnum.getExpectedErrorMessage().getBytes()));
            } else if (!opcodesResponse.getFailed() && providerEnum.getExpectedResultFields() != null) {
                assertThat(opcodesResponse.getReturnValue())
                        .isEqualTo(Bytes
                                // trims the leading zeros
                                .fromHexString(functionEncodeDecoder.encodedResultFor(
                                        providerEnum.getName(), contractAbiPath, providerEnum.getExpectedResultFields()))
                                .toHexString());
            }
        }
    }

    private OpcodesResponse expectedOpcodesResponse(final OpcodesProcessingResult result) {
        return new OpcodesResponse()
                .address(result.transactionProcessingResult().getRecipient()
                        .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                        .map(TransactionProviderEnum::entityAddress)
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
                .returnValue(Optional.ofNullable(result.transactionProcessingResult().getOutput())
                        .map(Bytes::toHexString)
                        .orElse(Bytes.EMPTY.toHexString()));
    }
}
