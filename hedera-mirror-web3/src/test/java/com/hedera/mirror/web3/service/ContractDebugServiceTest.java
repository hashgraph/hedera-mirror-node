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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@RequiredArgsConstructor
class ContractDebugServiceTest extends ContractCallTestSetup {

    private static final Long DEFAULT_CALL_VALUE = 0L;
    private static final OpcodeTracerOptions OPTIONS = new OpcodeTracerOptions(false, false, false);

    private static String toHumanReadableMessage(final String solidityError) {
        return BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(Bytes.fromHexString(solidityError));
    }

    private static Comparator<Long> gasComparator() {
        return (d1, d2) -> {
            final var diff = Math.abs(d1 - d2);
            return Math.toIntExact(diff <= 64L ? 0 : d1 - d2);
        };
    }

    private final ContractDebugService contractDebugService;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> paramsCaptor;

    @Captor
    private ArgumentCaptor<Long> gasCaptor;

    private HederaEvmTransactionProcessingResult resultCaptor;

    private ContractCallContext contextCaptor;

    private EntityId ownerEntityId;
    private EntityId senderEntityId;
    private EntityId treasuryEntityId;
    private EntityId spenderEntityId;

    @BeforeEach
    void setUpEntities() {
        // Obligatory data
        genesisBlockPersist();
        historicalBlocksPersist();
        historicalDataPersist();
        precompileContractPersist();
        exchangeRatesPersist();
        feeSchedulesPersist();
        // Account entities
        receiverPersist();
        notAssociatedSpenderEntityPersist();
        ownerEntityId = ownerEntityPersist();
        senderEntityId = senderEntityPersist();
        treasuryEntityId = treasureEntityPersist();
        spenderEntityId = spenderEntityPersist();
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
                .execute(paramsCaptor.capture(), gasCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(ContractCallServicePrecompileTest.SupportedContractModificationFunctions.class)
    void evmPrecompileSupportedModificationTokenFunctions(final ContractFunctionProviderEnum function) {
        setUpModificationContractEntities();
        final var params = serviceParametersForDebug(
                function,
                MODIFICATION_CONTRACT_ABI_PATH,
                MODIFICATION_CONTRACT_ADDRESS,
                DEFAULT_CALL_VALUE,
                domainBuilder.timestamp());
        verifyOpcodeTracerCall(params, function);
    }

    @ParameterizedTest
    @EnumSource(ContractCallNestedCallsTest.NestedEthCallContractFunctionsNegativeCases.class)
    void failedNestedCallWithHardcodedResult(final ContractFunctionProviderEnum function) {
        nestedEthCallsContractPersist();
        final var params = serviceParametersForDebug(
                function,
                NESTED_CALLS_ABI_PATH,
                NESTED_ETH_CALLS_CONTRACT_ADDRESS,
                DEFAULT_CALL_VALUE,
                domainBuilder.timestamp());
        verifyOpcodeTracerCall(params, function);
    }

    @ParameterizedTest
    @EnumSource(ContractCallDynamicCallsTest.DynamicCallsContractFunctions.class)
    void evmDynamicCallsTokenFunctions(final ContractFunctionProviderEnum function) {
        setUpDynamicCallsContractEntities();
        final var params = serviceParametersForDebug(
                function,
                DYNAMIC_ETH_CALLS_ABI_PATH,
                DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                DEFAULT_CALL_VALUE,
                domainBuilder.timestamp());
        verifyOpcodeTracerCall(params, function);
    }

    private void verifyOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderEnum function) {
        if (function.getExpectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(params, function);
        } else {
            verifySuccessfulOpcodeTracerCall(params, function);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(params);
        assertThat(gasCaptor.getValue()).isEqualTo(params.getGas());
    }

    @SneakyThrows
    private void verifyThrowingOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderEnum function) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        assertThat(actual.transactionProcessingResult().isSuccessful()).isFalse();
        assertThat(actual.transactionProcessingResult().getOutput()).isEqualTo(Bytes.EMPTY);
        assertThat(actual.transactionProcessingResult())
                .satisfiesAnyOf(
                        result -> assertThat(result.getRevertReason())
                                .isPresent()
                                .map(BytesDecoder::maybeDecodeSolidityErrorStringToReadableMessage)
                                .hasValue(function.getExpectedErrorMessage()),
                        result -> assertThat(result.getHaltReason())
                                .isPresent()
                                .map(ExceptionalHaltReason::getDescription)
                                .hasValue(function.getExpectedErrorMessage()));
        assertThat(actual.opcodes().size()).isNotZero();
        assertThat(toHumanReadableMessage(actual.opcodes().getLast().reason()))
                .isEqualTo(function.getExpectedErrorMessage());
    }

    private void verifySuccessfulOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderEnum function) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        final var expected = new OpcodesProcessingResult(resultCaptor, contextCaptor.getOpcodes());

        if (function.getExpectedResultFields() != null) {
            assertThat(actual.transactionProcessingResult().getOutput().toHexString())
                    .isEqualTo(functionEncodeDecoder.encodedResultFor(
                            function.getName(), NESTED_CALLS_ABI_PATH, function.getExpectedResultFields()));
        }

        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .usingRecursiveComparison()
                .ignoringFields("logs")
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }

    private void setUpModificationContractEntities() {
        final var modificationContractId = modificationContractPersist();
        commonTokensPersist(modificationContractId, MODIFICATION_CONTRACT_ADDRESS);
        fungibleTokenPersist(
                spenderEntityId,
                KEY_PROTO,
                FROZEN_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        fungibleTokenPersist(
                senderEntityId,
                KEY_PROTO,
                UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
    }

    private void setUpDynamicCallsContractEntities() {
        final var dynamicCallsContractId = dynamicEthCallContractPresist();
        commonTokensPersist(dynamicCallsContractId, DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS);
        final var nftWithoutKycKeyEntityId = nftPersistWithoutKycKey(
                NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                dynamicCallsContractId,
                spenderEntityId,
                dynamicCallsContractId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        tokenAccountsPersist(dynamicCallsContractId, List.of(nftWithoutKycKeyEntityId));
    }

    private void commonTokensPersist(EntityId contractId, Address contractAddress) {
        final var tokenId = fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var treasuryTokenId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TREASURY_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var transferFromTokenId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TRANSFRER_FROM_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        fungibleTokenPersist(
                treasuryEntityId,
                KEY_PROTO,
                NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                0L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var nftEntityId = nftPersist(
                NFT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftTransferEntityId = nftPersist(
                NFT_TRANSFER_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        nftPersist(
                NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                senderEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        allowancesPersist(ownerEntityId, contractId, tokenId, nftEntityId);
        allowancesPersist(senderEntityId, contractId, transferFromTokenId, nftEntityId);
        contractAllowancesPersist(senderEntityId, contractAddress, treasuryTokenId, nftTransferEntityId);
        tokenAccountsPersist(
                contractId, List.of(tokenId, treasuryTokenId, transferFromTokenId, nftEntityId, nftTransferEntityId));
    }

    private void tokenAccountsPersist(EntityId contractId, List<EntityId> tokenIds) {
        for (EntityId tokenId : tokenIds) {
            tokenAccountPersist(contractId, tokenId, TokenFreezeStatusEnum.UNFROZEN);
        }
    }
}
