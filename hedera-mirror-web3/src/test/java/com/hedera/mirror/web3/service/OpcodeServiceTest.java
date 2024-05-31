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
import static com.hedera.mirror.common.util.CommonUtils.nextBytes;
import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_DEBUG_TRACE_TRANSACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.TransactionHashParameter;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.common.TransactionIdParameter;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.TransactionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class OpcodeServiceTest extends Web3IntegrationTest {

    @Autowired
    private OpcodeService opcodeService;

    @Autowired
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Nested
    @DisplayName("buildCallServiceParameters")
    class BuildCallServiceParameters {

        private final AtomicReference<CallServiceParameters> expectedCallServiceParameters = new AtomicReference<>();

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
            final var ethTransaction = Optional.ofNullable(provider.getEthTransaction())
                    .map(DomainWrapper::persist)
                    .orElse(null);
            final var recordFile = provider.getRecordFile().persist();
            final var contractResult = persistContractResult ?
                    provider.getContractResult().persist() :
                    provider.getContractResult().get();
            final var contractEntity = provider.getContractEntity().persist();
            final var senderEntity = provider.getSenderEntity().persist();
            if (persistContractTransactionHash) {
                provider.getContractTransactionHash().persist();
            }

            expectedCallServiceParameters.set(CallServiceParameters.builder()
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

            final var exception = assertThrows(
                    EntityNotFoundException.class,
                    () -> opcodeService.buildCallServiceParameters(transactionIdOrHash));

            assertEquals("Contract result not found", exception.getMessage());
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callWithTransactionNotFoundExceptionTest(final TransactionProviderEnum providerEnum) {
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum, false, false, true);

            final var expectedMessage = switch (transactionIdOrHash) {
                case TransactionHashParameter ignored -> "Contract transaction hash not found";
                case TransactionIdParameter ignored -> "Transaction not found";
            };

            final var exception = assertThrows(
                    EntityNotFoundException.class,
                    () -> opcodeService.buildCallServiceParameters(transactionIdOrHash));

            assertEquals(expectedMessage, exception.getMessage());
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForSenderWithAliasAndEvmAddressShouldUseEvmAddress(final TransactionProviderEnum providerEnum) {
            providerEnum.setPayerEvmAddress(domainBuilder.evmAddress());
            providerEnum.setPayerAlias(DomainUtils.fromBytes(new byte[]{
                    0, 0, 0, 0, // shard
                    0, 0, 0, 0, 0, 0, 0, 0, // realm
                    0, 0, 0, 0, 0, 0, 0, Long.valueOf(providerEnum.getPayerAccountId().getNum()).byteValue(), // num
            }).toByteArray());
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .sender(new HederaEvmAccount(Address.wrap(Bytes.wrap(providerEnum.getPayerEvmAddress()))))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForSenderOnlyWithEvmAliasAddressShouldUseAlias(final TransactionProviderEnum providerEnum) {
            providerEnum.setPayerEvmAddress(null);
            providerEnum.setPayerAlias(DomainUtils.fromBytes(new byte[]{
                    0, 0, 0, 0, // shard
                    0, 0, 0, 0, 0, 0, 0, 0, // realm
                    0, 0, 0, 0, 0, 0, 0, Long.valueOf(providerEnum.getPayerAccountId().getNum()).byteValue(), // num
            }).toByteArray());
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .sender(new HederaEvmAccount(Address.wrap(Bytes.wrap(providerEnum.getPayerAlias()))))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForSenderOnlyWithNonEvmAliasAddressShouldUseMirrorAddress(final TransactionProviderEnum providerEnum) {
            providerEnum.setPayerEvmAddress(null);
            providerEnum.setPayerAlias(domainBuilder.key(Key.KeyCase.ED25519));
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .sender(new HederaEvmAccount(toAddress(providerEnum.getPayerAccountId())))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForSenderOnlyWithMirrorAddressShouldUseMirrorAddress(final TransactionProviderEnum providerEnum) {
            providerEnum.setPayerEvmAddress(null);
            providerEnum.setPayerAlias(null);
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .sender(new HederaEvmAccount(toAddress(providerEnum.getPayerAccountId())))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForContractWithAliasAndEvmAddressShouldUseEvmAddress(final TransactionProviderEnum providerEnum) {
            providerEnum.setContractEvmAddress(domainBuilder.evmAddress());
            providerEnum.setContractAlias(DomainUtils.fromBytes(new byte[]{
                    0, 0, 0, 0, // shard
                    0, 0, 0, 0, 0, 0, 0, 0, // realm
                    0, 0, 0, 0, 0, 0, 0, Long.valueOf(providerEnum.getContractId().getNum()).byteValue(), // num
            }).toByteArray());
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .receiver(Address.wrap(Bytes.wrap(providerEnum.getContractEvmAddress())))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForContractOnlyWithEvmAliasAddressShouldUseAlias(final TransactionProviderEnum providerEnum) {
            providerEnum.setContractEvmAddress(null);
            providerEnum.setContractAlias(DomainUtils.fromBytes(new byte[]{
                    0, 0, 0, 0, // shard
                    0, 0, 0, 0, 0, 0, 0, 0, // realm
                    0, 0, 0, 0, 0, 0, 0, Long.valueOf(providerEnum.getContractId().getNum()).byteValue(), // num
            }).toByteArray());
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .receiver(Address.wrap(Bytes.wrap(providerEnum.getContractAlias())))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForContractOnlyWithNonEvmAliasAddressShouldUseMirrorAddress(final TransactionProviderEnum providerEnum) {
            providerEnum.setContractEvmAddress(null);
            providerEnum.setContractAlias(domainBuilder.key(Key.KeyCase.ED25519));
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .receiver(toAddress(providerEnum.getContractId()))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }

        @ParameterizedTest
        @EnumSource(TransactionProviderEnum.class)
        void callForContractOnlyWithMirrorAddressShouldUseMirrorAddress(final TransactionProviderEnum providerEnum) {
            providerEnum.setContractEvmAddress(null);
            providerEnum.setContractAlias(null);
            final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

            expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                    .receiver(toAddress(providerEnum.getContractId()))
                    .build());

            final var params = opcodeService.buildCallServiceParameters(transactionIdOrHash);

            assertEquals(expectedCallServiceParameters.get(), params);
        }
    }

    @Nested
    @DisplayName("buildOpcodesResponse")
    class BuildOpcodesResponse {

        private static Stream<Arguments> tracerOptions() {
            return Stream.of(
                    Arguments.of(new OpcodeTracerOptions(true, true, true)),
                    Arguments.of(new OpcodeTracerOptions(false, true, true)),
                    Arguments.of(new OpcodeTracerOptions(true, false, true)),
                    Arguments.of(new OpcodeTracerOptions(true, true, false)),
                    Arguments.of(new OpcodeTracerOptions(false, false, true)),
                    Arguments.of(new OpcodeTracerOptions(false, true, false)),
                    Arguments.of(new OpcodeTracerOptions(true, false, false)),
                    Arguments.of(new OpcodeTracerOptions(false, false, false))
            );
        }

        @ParameterizedTest
        @MethodSource("tracerOptions")
        void successfulCall(final OpcodeTracerOptions options) {
            final var opcodes = opcodes(options);
            final var opcodesResult = new OpcodesProcessingResult(
                    HederaEvmTransactionProcessingResult.successful(
                            List.of(),
                            opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L),
                            0,
                            opcodes.stream().map(Opcode::gasCost).reduce(Long::sum).orElse(0L),
                            Bytes.EMPTY,
                            Address.wrap(Bytes.wrap(domainBuilder.evmAddress()))
                    ),
                    opcodes
            );

            final var opcodeResponse = opcodeService.buildOpcodesResponse(opcodesResult);
            assertEquals(expectedOpcodesResponse(opcodesResult), opcodeResponse);
        }

        @ParameterizedTest
        @MethodSource("tracerOptions")
        void unsuccessfulCall(final OpcodeTracerOptions options) {
            final var opcodes = opcodes(options);
            final var opcodesResult = new OpcodesProcessingResult(
                    HederaEvmTransactionProcessingResult.failed(
                            opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L),
                            0,
                            opcodes.stream().map(Opcode::gasCost).reduce(Long::sum).orElse(0L),
                            Optional.of(Bytes.of("0x".getBytes())),
                            Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR)
                    ),
                    opcodes
            );

            final var opcodeResponse = opcodeService.buildOpcodesResponse(opcodesResult);

            assertEquals(expectedOpcodesResponse(opcodesResult), opcodeResponse);
        }
    }

    private OpcodesResponse expectedOpcodesResponse(final OpcodesProcessingResult result) {
        return new OpcodesResponse()
                .address(result.transactionProcessingResult().getRecipient()
                        .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                        .map(OpcodeServiceTest::entityAddress)
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

    private List<Opcode> opcodes(final OpcodeTracerOptions options) {
        return Arrays.asList(
                new Opcode(
                        1273,
                        "PUSH1",
                        2731,
                        3,
                        2,
                        options.isStack() ?
                                List.of(Bytes.of(nextBytes(32))) :
                                Collections.emptyList(),
                        options.isMemory() ?
                                List.of(Bytes.of(nextBytes(32))) :
                                Collections.emptyList(),
                        Collections.emptySortedMap(),
                        null
                ),
                new Opcode(
                        1275,
                        "REVERT",
                        2728,
                        0,
                        2,
                        options.isStack() ?
                                List.of(
                                        Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32))) :
                                Collections.emptyList(),
                        options.isMemory() ?
                                List.of(
                                        Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32))) :
                                Collections.emptyList(),
                        Collections.emptySortedMap(),
                        "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"
                ),
                new Opcode(
                        682,
                        "SWAP2",
                        2776,
                        3,
                        1,
                        options.isStack() ?
                                List.of(
                                        Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32))) :
                                Collections.emptyList(),
                        options.isMemory() ?
                                List.of(
                                        Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32))) :
                                Collections.emptyList(),
                        options.isStorage() ?
                                ImmutableSortedMap.of(
                                        Bytes.of(nextBytes(32)), Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32)), Bytes.of(nextBytes(32)),
                                        Bytes.of(nextBytes(32)), Bytes.of(nextBytes(32))) :
                                Collections.emptySortedMap(),
                        null
                )
        );
    }

    private static Address entityAddress(Entity entity) {
        if (entity == null) {
            return Address.ZERO;
        }
        if (entity.getEvmAddress() != null) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return toAddress(entity.toEntityId());
    }
}
