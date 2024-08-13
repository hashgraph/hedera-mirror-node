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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
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

    private static String toHumanReadableMessage(final String solidityError) {
        return BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(Bytes.fromHexString(solidityError));
    }

    private static Comparator<Long> gasComparator() {
        return (d1, d2) -> {
            final var diff = Math.abs(d1 - d2);
            return Math.toIntExact(diff <= 64L ? 0 : d1 - d2);
        };
    }

    @BeforeEach
    void setUpEntities() {
        // Obligatory data
        genesisBlockPersist();
        historicalBlocksPersist();
        historicalDataPersist();
        precompileContractPersist();
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
    @EnumSource(NestedEthCallContractFunctionsNegativeCases.class)
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
    @EnumSource(DynamicCallsContractFunctions.class)
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

    @Getter
    @RequiredArgsConstructor
    private enum DynamicCallsContractFunctions implements ContractFunctionProviderEnum {
        MINT_FUNGIBLE_TOKEN(
                "mintTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 100L, new byte[0][0], TREASURY_ADDRESS},
                null),
        MINT_NFT(
                "mintTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {
                    NFT_ADDRESS,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()},
                    OWNER_ADDRESS
                },
                null),
        BURN_FUNGIBLE_TOKEN(
                "burnTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 12L, new long[0], TREASURY_ADDRESS},
                null),
        BURN_NFT(
                "burnTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NFT_ADDRESS, 0L, new long[] {1L}, OWNER_ADDRESS},
                null),
        WIPE_FUNGIBLE_TOKEN(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 10L, new long[0], SENDER_ALIAS},
                null),
        WIPE_NFT(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, 0L, new long[] {1L}, SENDER_ALIAS},
                null),
        PAUSE_UNPAUSE_FUNGIBLE_TOKEN(
                "pauseTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, null),
        FREEZE_UNFREEZE_FUNGIBLE_TOKEN(
                "freezeTokenGetPauseStatusUnpauseGetPauseStatus",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS},
                null),
        PAUSE_UNPAUSE_NFT("pauseTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {NFT_ADDRESS}, null),
        FREEZE_UNFREEZE_NFT(
                "freezeTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {NFT_ADDRESS, SPENDER_ALIAS}, null),
        ASSOCIATE_TRANSFER_NFT(
                "associateTokenTransfer",
                new Object[] {
                    NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY,
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    BigInteger.ZERO,
                    BigInteger.ONE
                },
                null),
        ASSOCIATE_TRANSFER_FUNGIBLE_TOKEN(
                "associateTokenTransfer",
                new Object[] {
                    TREASURY_TOKEN_ADDRESS,
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    BigInteger.ONE,
                    BigInteger.ZERO
                },
                null),
        ASSOCIATE_DISSOCIATE_TRANSFER_FUNGIBLE_TOKEN_FAIL(
                "associateTokenDissociateFailTransfer",
                new Object[] {
                    TREASURY_TOKEN_ADDRESS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    BigInteger.ONE,
                    BigInteger.ZERO
                },
                "IERC20: failed to transfer"),
        ASSOCIATE_DISSOCIATE_TRANSFER_NFT_FAIL(
                "associateTokenDissociateFailTransfer",
                new Object[] {NFT_TRANSFER_ADDRESS, SENDER_ALIAS, RECEIVER_ADDRESS, BigInteger.ZERO, BigInteger.ONE},
                "IERC721: failed to transfer"),
        ASSOCIATE_TRANSFER_NFT_EXCEPTION(
                "associateTokenTransfer",
                new Object[] {
                    toAddress(1), // Not persisted address
                    DYNAMIC_ETH_CALLS_CONTRACT_ALIAS,
                    NOT_ASSOCIATED_SPENDER_ALIAS,
                    BigInteger.ZERO,
                    BigInteger.ONE
                },
                "Failed to associate tokens"),
        APPROVE_FUNGIBLE_TOKEN_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS, OWNER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {NFT_ADDRESS, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_FROM_GET_ALLOWANCE(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_FROM_GET_ALLOWANCE_2(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_TOKEN_TRANSFER_FROM_GET_ALLOWANCE(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_NFT_TOKEN_TRANSFER_FROM_GET_ALLOWANCE_2(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY, SENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_GET_ALLOWANCE(
                "approveTokenTransferGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_TRANSFER_GET_ALLOWANCE(
                "approveTokenTransferGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_CRYPTO_TRANSFER_FUNGIBLE_GET_ALLOWANCE(
                "approveTokenCryptoTransferGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {TREASURY_TOKEN_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, false}
                },
                null),
        APPROVE_CRYPTO_TRANSFER_NFT_GET_ALLOWANCE(
                "approveTokenCryptoTransferGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {NFT_TRANSFER_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, true}
                },
                null),
        APPROVE_FOR_ALL_TRANSFER_FROM_NFT_GET_ALLOWANCE(
                "approveForAllTokenTransferFromGetAllowance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, 1L},
                null),
        APPROVE_FOR_ALL_TRANSFER_NFT_GET_ALLOWANCE(
                "approveForAllTokenTransferGetAllowance", new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, 1L}, null),
        APPROVE_FOR_ALL_CRYPTO_TRANSFER_NFT_GET_ALLOWANCE(
                "approveForAllCryptoTransferGetAllowance",
                new Object[] {
                    new Object[] {},
                    new Object[] {NFT_TRANSFER_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, true}
                },
                null),
        TRANSFER_NFT_GET_ALLOWANCE_OWNER_OF(
                "transferFromNFTGetAllowance", new Object[] {NFT_TRANSFER_ADDRESS, 1L}, null),
        TRANSFER_FUNGIBLE_TOKEN_GET_BALANCE(
                "transferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, BigInteger.ONE, BigInteger.ZERO},
                null),
        TRANSFER_NFT_GET_OWNER(
                "transferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ALIAS, BigInteger.ZERO, BigInteger.ONE},
                null),
        CRYPTO_TRANSFER_FUNFIBLE_TOKEN_GET_OWNER(
                "cryptoTransferFromGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {TREASURY_TOKEN_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, false}
                },
                null),
        CRYPTO_TRANSFER_NFT_GET_OWNER(
                "cryptoTransferFromGetAllowanceGetBalance",
                new Object[] {
                    new Object[] {},
                    new Object[] {NFT_TRANSFER_ADDRESS, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, SPENDER_ALIAS, 1L, true}
                },
                null),
        GRANT_KYC_REVOKE_KYC_FUNGIBLE("grantKycRevokeKyc", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, null),
        GRANT_KYC_REVOKE_KYC_NFT("grantKycRevokeKyc", new Object[] {NFT_ADDRESS, SENDER_ALIAS}, null),
        ADDRESS_THIS("getAddressThis", null, null);
        private final String name;
        private final Object[] functionParameters;
        private final String expectedErrorMessage;
    }

    @Getter
    @RequiredArgsConstructor
    private enum NestedEthCallContractFunctionsNegativeCases implements ContractFunctionProviderEnum {
        GET_TOKEN_INFO_HISTORICAL(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[] {NFT_ADDRESS_HISTORICAL},
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        GET_TOKEN_INFO(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[] {Address.ZERO},
                new Object[] {"hardcodedResult"},
                BlockType.LATEST),
        HTS_GET_APPROVED_HISTORICAL(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[] {NFT_ADDRESS_HISTORICAL, 1L},
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        HTS_GET_APPROVED(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[] {Address.ZERO, 1L},
                new Object[] {"hardcodedResult"},
                BlockType.LATEST),
        MINT_TOKEN_HISTORICAL(
                "nestedMintTokenAndHardcodedResult",
                new Object[] {
                    NFT_ADDRESS_HISTORICAL,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        MINT_TOKEN(
                "nestedMintTokenAndHardcodedResult",
                new Object[] {
                    Address.ZERO,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {"hardcodedResult"},
                BlockType.LATEST);

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
        private final BlockType block;
    }
}
