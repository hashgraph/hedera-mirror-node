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
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static com.hedera.mirror.web3.utils.TransactionProviderEnum.entityAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doAnswer;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.ResultCaptor;
import com.hedera.mirror.web3.utils.TransactionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

class OpcodeServiceTest extends Web3IntegrationTest {

    @SpyBean
    private ContractCallService contractCallService;

    @Autowired
    private OpcodeService opcodeService;

    @Autowired
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("processOpcodeCall")
    class ProcessOpcodeCall {

        @Captor
        private ArgumentCaptor<CallServiceParameters> serviceParametersCaptor;

        @Captor
        private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

        private final ResultCaptor<OpcodesProcessingResult> opcodesResultCaptor = new ResultCaptor<>(OpcodesProcessingResult.class);

        private final AtomicReference<CallServiceParameters> expectedServiceParameters = new AtomicReference<>();
        private final AtomicReference<OpcodeTracerOptions> expectedTracerOptions = new AtomicReference<>();

        @BeforeEach
        void setUp() {
            doAnswer(opcodesResultCaptor)
                    .when(contractCallService)
                    .processOpcodeCall(serviceParametersCaptor.capture(), tracerOptionsCaptor.capture());
            expectedServiceParameters.set(null);
            expectedTracerOptions.set(new OpcodeTracerOptions());
        }

        TransactionIdOrHashParameter setUp(final TransactionProviderEnum provider) {
            return setUp(provider, true, true, true);
        }

        TransactionIdOrHashParameter setUp(final TransactionProviderEnum provider,
                                           final boolean persistTransaction,
                                           final boolean persistContractTransactionHash,
                                           final boolean persistContractResult) {
            provider.setDomainBuilder(domainBuilder);
            final var transaction = persistTransaction ?
                    provider.getTransaction().persist() :
                    provider.getTransaction().get();
            final var ethTransaction = provider.hasEthTransaction() ? provider.getEthTransaction().persist() : null;
            final var recordFile = provider.getRecordFile().persist();
            final var contractResult = persistContractResult ?
                    provider.getContractResult().persist() :
                    provider.getContractResult().get();
            final var contractEntity = provider.getContractEntity().persist();
            final var senderEntity = provider.getSenderEntity().persist();
            if (persistContractTransactionHash) {
                provider.getContractTransactionHash().persist();
            }

            expectedServiceParameters.set(CallServiceParameters.builder()
                    .sender(new HederaEvmAccount(entityAddress(senderEntity)))
                    .receiver(entityAddress(contractEntity))
                    .gas(ethTransaction != null ?
                            ethTransaction.getGasLimit() :
                            contractResult.getGasLimit())
                    .value(ethTransaction != null ?
                            new BigInteger(ethTransaction.getValue()).longValue() :
                            contractResult.getAmount())
                    .callData(ethTransaction != null ?
                            Bytes.of(ethTransaction.getCallData()) :
                            Bytes.of(contractResult.getFunctionParameters()))
                    .isStatic(false)
                    .callType(ETH_DEBUG_TRACE_TRANSACTION)
                    .isEstimate(false)
                    .block(BlockType.of(recordFile.getIndex().toString()))
                    .build());

            if (ethTransaction != null) {
                return new TransactionHashParameter(Bytes.of(ethTransaction.getHash()));
            } else {
                return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
            }
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callWithContractResultNotFoundExceptionTest(final TransactionProviderEnum providerEnum) {
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum, true, true, false);

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, new OpcodeTracerOptions()))
                    .withMessage("Contract result not found");
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callWithTransactionNotFoundExceptionTest(final TransactionProviderEnum providerEnum) {
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum, false, false, true);

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, new OpcodeTracerOptions()))
                    .withMessage(switch (transactionIdOrHash) {
                        case TransactionHashParameter ignored -> "Contract transaction hash not found";
                        case TransactionIdParameter ignored -> "Transaction not found";
                    });
        }

        @ParameterizedTest
        @MethodSource("transactionsWithDifferentTracerOptions")
        void callWithDifferentCombinationsOfTracerOptions(final TransactionProviderEnum providerEnum,
                                                          final OpcodeTracerOptions options) {
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);
            expectedTracerOptions.set(options);

            final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

            assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(opcodesResultCaptor.getValue()));
            assertThat(serviceParametersCaptor.getValue()).isEqualTo(expectedServiceParameters.get());
            assertThat(tracerOptionsCaptor.getValue()).isEqualTo(expectedTracerOptions.get());
        }

        @ParameterizedTest
        @MethodSource("transactionsWithDifferentSenderAddresses")
        void callWithDifferentSenderAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum) {
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedServiceParameters.set(expectedServiceParameters.get().toBuilder()
                    .sender(new HederaEvmAccount(entityAddress(providerEnum.getSenderEntity().get())))
                    .build());

            final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, new OpcodeTracerOptions());

            assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(opcodesResultCaptor.getValue()));
            assertThat(serviceParametersCaptor.getValue()).isEqualTo(expectedServiceParameters.get());
            assertThat(tracerOptionsCaptor.getValue()).isEqualTo(expectedTracerOptions.get());
        }

        @ParameterizedTest
        @MethodSource("transactionsWithDifferentReceiverAddresses")
        void callWithDifferentReceiverAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum) {
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedServiceParameters.set(expectedServiceParameters.get().toBuilder()
                    .receiver(entityAddress(providerEnum.getContractEntity().get()))
                    .build());

            final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, new OpcodeTracerOptions());

            assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(opcodesResultCaptor.getValue()));
            assertThat(serviceParametersCaptor.getValue()).isEqualTo(expectedServiceParameters.get());
            assertThat(tracerOptionsCaptor.getValue()).isEqualTo(expectedTracerOptions.get());
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
                    new OpcodeTracerOptions(false, false, false)
            );
            return Arrays.stream(TransactionProviderEnum.values())
                    .flatMap(providerEnum -> tracerOptions.stream()
                            .map(options -> Arguments.of(providerEnum, options)));
        }

        Stream<Arguments> transactionsWithDifferentSenderAddresses() {
            return Arrays.stream(TransactionProviderEnum.values())
                    .flatMap(providerEnum -> entityAddressCombinations(providerEnum.getPayerAccountId())
                            .map(addressPair -> Arguments.of(Named.of(
                                    "%s(evmAddress=%s, alias=%s)".formatted(
                                            providerEnum.name(),
                                            addressPair.getLeft() != null ? Bytes.of(addressPair.getLeft()) : null,
                                            addressPair.getRight() != null ? Bytes.of(addressPair.getRight()) : null
                                    ),
                                    providerEnum.customize(p -> {
                                        p.setPayerEvmAddress(addressPair.getLeft());
                                        p.setPayerAlias(addressPair.getRight());
                                    })
                            ))));
        }

        Stream<Arguments> transactionsWithDifferentReceiverAddresses() {
            return Arrays.stream(TransactionProviderEnum.values())
                    .flatMap(providerEnum -> entityAddressCombinations(providerEnum.getContractId())
                            .map(addressPair -> Arguments.of(Named.of(
                                    "%s(evmAddress=%s, alias=%s)".formatted(
                                            providerEnum.name(),
                                            addressPair.getLeft() != null ? Bytes.of(addressPair.getLeft()) : null,
                                            addressPair.getRight() != null ? Bytes.of(addressPair.getRight()) : null
                                    ),
                                    providerEnum.customize(p -> {
                                        p.setContractEvmAddress(addressPair.getLeft());
                                        p.setContractAlias(addressPair.getRight());
                                    })
                            ))));
        }

        Stream<Pair<byte[], byte[]>> entityAddressCombinations(EntityId entityId) {
            Supplier<byte[]> validAlias = () -> new byte[] {
                    0, 0, 0, 0, // shard
                    0, 0, 0, 0, 0, 0, 0, 0, // realm
                    0, 0, 0, 0, 0, 0, 0, Long.valueOf(entityId.getNum()).byteValue(), // num
            };
            Supplier<byte[]> invalidAlias = () -> domainBuilder.key(Key.KeyCase.ED25519);
            return Stream.of(
                    Pair.of(domainBuilder.evmAddress(), validAlias.get()),
                    Pair.of(null, validAlias.get()),
                    Pair.of(null, invalidAlias.get()),
                    Pair.of(null, null)
            );
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
