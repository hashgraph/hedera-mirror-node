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
import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
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
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.service.ContractDebugServiceTest.DynamicCallsContractFunctions;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
class OpcodeServiceTest extends ContractCallTestSetup {

    public static final long AMOUNT = 0L;
    public static final long GAS = 15_000_000L;

    private final OpcodeService opcodeService;
    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("processOpcodeCall")
    class ProcessOpcodeCall {

        @Captor
        private ArgumentCaptor<ContractDebugParameters> serviceParametersCaptor;

        @Captor
        private ArgumentCaptor<Long> gasCaptor;

        private HederaEvmTransactionProcessingResult resultCaptor;

        private ContractCallContext contextCaptor;

        private ContractDebugParameters expectedServiceParameters;
        private EntityId senderEntityId;
        private EntityId contractEntityId;
        private long consensusTimestamp;

        static Stream<Arguments> tracerOptions() {
            return Stream.of(
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

        @BeforeEach
        void setUp() {
            genesisBlockPersist();
            historicalBlocksPersist();
            historicalDataPersist();
            precompileContractPersist();
            senderEntityId = senderEntityPersist();
            final var ownerEntityId = ownerEntityPersist();
            final var spenderEntityId = spenderEntityPersist();
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
        }

        @BeforeEach
        void setUpArgumentCaptors() {
            doAnswer(invocation -> {
                        final var transactionProcessingResult =
                                (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                        resultCaptor = transactionProcessingResult;
                        contextCaptor = ContractCallContext.get();
                        return transactionProcessingResult;
                    })
                    .when(processor)
                    .execute(serviceParametersCaptor.capture(), gasCaptor.capture());
        }

        @SneakyThrows
        TransactionIdOrHashParameter setUp(
                final ContractFunctionProviderEnum provider,
                final TransactionType transactionType,
                final Address contractAddress,
                final Path contractAbiPath,
                final boolean persistTransaction,
                final boolean persistContractResult) {
            assertThat(contractEntityId).isNotNull();
            assertThat(senderEntityId).isNotNull();

            consensusTimestamp = domainBuilder.timestamp();
            final var validStartNs = consensusTimestamp - 1;
            final var ethHash = domainBuilder.bytes(32);
            final var callData = functionEncodeDecoder
                    .functionHashFor(provider.getName(), contractAbiPath, provider.getFunctionParameters())
                    .toArray();

            final var transactionBuilder = domainBuilder.transaction().customize(transaction -> transaction
                    .consensusTimestamp(consensusTimestamp)
                    .entityId(contractEntityId)
                    .payerAccountId(senderEntityId)
                    .type(transactionType.getProtoId())
                    .validStartNs(validStartNs));
            final var transaction = persistTransaction ? transactionBuilder.persist() : transactionBuilder.get();

            final EthereumTransaction ethTransaction;
            if (transactionType == TransactionType.ETHEREUMTRANSACTION) {
                final var ethTransactionBuilder = domainBuilder
                        .ethereumTransaction(false)
                        .customize(ethereumTransaction -> ethereumTransaction
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

            final var contractResultBuilder = domainBuilder.contractResult().customize(contractResult -> contractResult
                    .amount(AMOUNT)
                    .consensusTimestamp(consensusTimestamp)
                    .contractId(contractEntityId.getId())
                    .functionParameters(callData)
                    .gasLimit(GAS)
                    .senderId(senderEntityId)
                    .transactionHash(transaction.getTransactionHash()));
            final var contractResult =
                    persistContractResult ? contractResultBuilder.persist() : contractResultBuilder.get();

            final var expectedResult = provider.getExpectedResultFields() != null
                    ? Bytes.fromHexString(functionEncodeDecoder.encodedResultFor(
                                    provider.getName(), contractAbiPath, provider.getExpectedResultFields()))
                            .toArray()
                    : null;
            final var expectedError = provider.getExpectedErrorMessage() != null
                    ? provider.getExpectedErrorMessage().getBytes()
                    : null;

            domainBuilder
                    .contractAction()
                    .customize(contractAction -> contractAction
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
                domainBuilder
                        .contractTransactionHash()
                        .customize(contractTransactionHash -> contractTransactionHash
                                .consensusTimestamp(consensusTimestamp)
                                .entityId(contractEntityId.getId())
                                .hash(ethHash)
                                .payerAccountId(senderEntityId.getId())
                                .transactionResult(contractResult.getTransactionResult()))
                        .persist();
            }

            expectedServiceParameters = ContractDebugParameters.builder()
                    .block(provider.getBlock())
                    .callData(Bytes.of(callData))
                    .consensusTimestamp(consensusTimestamp)
                    .gas(GAS)
                    .receiver(entityDatabaseAccessor
                            .get(contractAddress, Optional.empty())
                            .map(this::entityAddress)
                            .orElse(Address.ZERO))
                    .sender(new HederaEvmAccount(SENDER_ALIAS))
                    .value(AMOUNT)
                    .build();

            if (ethTransaction != null) {
                return new TransactionHashParameter(Bytes.of(ethTransaction.getHash()));
            } else {
                return new TransactionIdParameter(
                        transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
            }
        }

        @ParameterizedTest
        @MethodSource("tracerOptions")
        void callWithDifferentCombinationsOfTracerOptions(final OpcodeTracerOptions options) {
            contractEntityId = dynamicEthCallContractPresist();
            final var providerEnum = DynamicCallsContractFunctions.MINT_NFT;

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
            contractEntityId = nestedEthCallsContractPersist();

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
            contractEntityId = systemExchangeRateContractPersist();

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
                    .withMessage("Contract result not found: " + consensusTimestamp);
        }

        @Test
        void callWithTransactionNotFoundExceptionTest() {
            contractEntityId = systemExchangeRateContractPersist();

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
                    .withMessage("Transaction not found: " + transactionIdOrHash);
        }

        @Test
        void callWithContractTransactionHashNotFoundExceptionTest() {
            contractEntityId = nestedEthCallsContractPersist();

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
                    .withMessage("Contract transaction hash not found: " + transactionIdOrHash);
        }

        private void verifyOpcodesResponse(
                final ContractFunctionProviderEnum providerEnum,
                final Path contractAbiPath,
                final OpcodesResponse opcodesResponse,
                final OpcodeTracerOptions options) {
            assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(resultCaptor, contextCaptor.getOpcodes()));
            assertThat(serviceParametersCaptor.getValue()).isEqualTo(expectedServiceParameters);
            assertThat(gasCaptor.getValue()).isEqualTo(expectedServiceParameters.getGas());
            assertThat(contextCaptor.getOpcodeTracerOptions()).isEqualTo(options);

            if (providerEnum.getExpectedErrorMessage() != null) {
                assertThat(opcodesResponse.getOpcodes().getLast().getReason())
                        .isEqualTo(Hex.encodeHexString(
                                providerEnum.getExpectedErrorMessage().getBytes()));
            } else if (!opcodesResponse.getFailed() && providerEnum.getExpectedResultFields() != null) {
                assertThat(opcodesResponse.getReturnValue())
                        .isEqualTo(Bytes
                                // trims the leading zeros
                                .fromHexString(functionEncodeDecoder.encodedResultFor(
                                        providerEnum.getName(),
                                        contractAbiPath,
                                        providerEnum.getExpectedResultFields()))
                                .toHexString());
            }
        }

        private OpcodesResponse expectedOpcodesResponse(
                final HederaEvmTransactionProcessingResult result, final List<Opcode> opcodes) {
            return new OpcodesResponse()
                    .address(result.getRecipient()
                            .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                            .map(this::entityAddress)
                            .map(Address::toHexString)
                            .orElse(Address.ZERO.toHexString()))
                    .contractId(result.getRecipient()
                            .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                            .map(Entity::toEntityId)
                            .map(EntityId::toString)
                            .orElse(null))
                    .failed(!result.isSuccessful())
                    .gas(result.getGasUsed())
                    .opcodes(opcodes.stream()
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
                    .returnValue(Optional.ofNullable(result.getOutput())
                            .map(Bytes::toHexString)
                            .orElse(Bytes.EMPTY.toHexString()));
        }

        public Address entityAddress(Entity entity) {
            if (entity == null) {
                return Address.ZERO;
            }
            if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
                return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
            }
            if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
                return Address.wrap(Bytes.wrap(entity.getAlias()));
            }
            return toAddress(entity.toEntityId());
        }
    }
}
