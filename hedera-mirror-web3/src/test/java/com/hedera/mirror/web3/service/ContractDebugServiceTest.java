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
    @EnumSource(SupportedContractModificationFunctions.class)
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
    enum SupportedContractModificationFunctions implements ContractFunctionProviderEnum {
        TRANSFER_FROM(
                "transferFromExternal",
                new Object[] {TRANSFRER_FROM_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 1L},
                new Object[] {}),
        APPROVE("approveExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS, 1L}, new Object[] {}),
        DELETE_ALLOWANCE(
                "approveExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 0L}, new Object[] {}),
        DELETE_ALLOWANCE_NFT("approveNFTExternal", new Object[] {NFT_ADDRESS, Address.ZERO, 1L}, new Object[] {}),
        APPROVE_NFT("approveNFTExternal", new Object[] {NFT_ADDRESS, TREASURY_ADDRESS, 1L}, new Object[] {}),
        SET_APPROVAL_FOR_ALL(
                "setApprovalForAllExternal", new Object[] {NFT_ADDRESS, TREASURY_ADDRESS, true}, new Object[] {}),
        ASSOCIATE_TOKEN(
                "associateTokenExternal", new Object[] {SPENDER_ALIAS, FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        ASSOCIATE_TOKENS(
                "associateTokensExternal",
                new Object[] {SPENDER_ALIAS, new Address[] {FUNGIBLE_TOKEN_ADDRESS}},
                new Object[] {}),
        HRC_ASSOCIATE_REDIRECT(
                "associateWithRedirect", new Address[] {FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED}, new Object[] {}),
        MINT_TOKEN(
                "mintTokenExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 100L, new byte[0][0]},
                new Object[] {SUCCESS_RESULT, 12445L, new long[0]}),
        MINT_NFT_TOKEN(
                "mintTokenExternal",
                new Object[] {
                    NFT_ADDRESS,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {SUCCESS_RESULT, 1_000_000_000L + 1, new long[] {1L}}),
        DISSOCIATE_TOKEN(
                "dissociateTokenExternal", new Object[] {SPENDER_ALIAS, TREASURY_TOKEN_ADDRESS}, new Object[] {}),
        DISSOCIATE_TOKENS(
                "dissociateTokensExternal",
                new Object[] {SPENDER_ALIAS, new Address[] {TREASURY_TOKEN_ADDRESS}},
                new Object[] {}),
        HRC_DISSOCIATE_REDIRECT("dissociateWithRedirect", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        BURN_TOKEN(
                "burnTokenExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 1L, new long[0]},
                new Object[] {SUCCESS_RESULT, 12345L - 1}),
        BURN_NFT_TOKEN("burnTokenExternal", new Object[] {NFT_ADDRESS, 0L, new long[] {1}}, new Object[] {
            SUCCESS_RESULT, 1_000_000_000L - 1
        }),
        WIPE_TOKEN(
                "wipeTokenAccountExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 1L},
                new Object[] {}),
        WIPE_NFT_TOKEN(
                "wipeTokenAccountNFTExternal",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, SENDER_ALIAS, new long[] {1}},
                new Object[] {}),
        REVOKE_TOKEN_KYC(
                "revokeTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Object[] {}),
        GRANT_TOKEN_KYC("grantTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Object[] {}),
        DELETE_TOKEN("deleteTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        FREEZE_TOKEN(
                "freezeTokenExternal",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS},
                new Object[] {}),
        UNFREEZE_TOKEN(
                "unfreezeTokenExternal", new Object[] {FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS}, new Object[] {}),
        PAUSE_TOKEN("pauseTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        UNPAUSE_TOKEN("unpauseTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {}),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenExternal", new Object[] {FUNGIBLE_TOKEN, 10L, 10}, new Object[] {
            SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS
        }),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createFungibleTokenWithCustomFeesExternal",
                new Object[] {FUNGIBLE_TOKEN, 10L, 10, FIXED_FEE_WRAPPER, FRACTIONAL_FEE_WRAPPER},
                new Object[] {SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS}),
        CREATE_NON_FUNGIBLE_TOKEN("createNonFungibleTokenExternal", new Object[] {NON_FUNGIBLE_TOKEN}, new Object[] {
            SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS
        }),
        CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createNonFungibleTokenWithCustomFeesExternal",
                new Object[] {NON_FUNGIBLE_TOKEN, FIXED_FEE_WRAPPER, ROYALTY_FEE_WRAPPER},
                new Object[] {SUCCESS_RESULT, MODIFICATION_CONTRACT_ADDRESS}),
        TRANSFER_TOKEN_WITH(
                "transferTokenExternal",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, SENDER_ALIAS, 1L},
                new Object[] {}),
        TRANSFER_TOKENS(
                "transferTokensExternal",
                new Object[] {TREASURY_TOKEN_ADDRESS, new Address[] {OWNER_ADDRESS, SPENDER_ALIAS}, new long[] {1L, -1L}
                },
                new Object[] {}),
        TRANSFER_TOKENS_WITH_ALIAS(
                "transferTokensExternal",
                new Object[] {TREASURY_TOKEN_ADDRESS, new Address[] {SPENDER_ALIAS, SENDER_ALIAS}, new long[] {1L, -1L}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_TOKENS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {}, new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, OWNER_ADDRESS, 5L, false}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_TOKENS_WITH_ALIAS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {}, new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 5L, false}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_HBARS_AND_TOKENS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {SENDER_ALIAS, OWNER_ADDRESS, 5L},
                    new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, OWNER_ADDRESS, 5L, false}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_HBARS(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {SENDER_ALIAS, OWNER_ADDRESS, 5L},
                    new Object[] {}
                },
                new Object[] {}),
        CRYPTO_TRANSFER_NFT(
                "cryptoTransferExternal",
                new Object[] {
                    new Object[] {}, new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L, true}
                },
                new Object[] {}),
        TRANSFER_NFT_TOKENS(
                "transferNFTsExternal",
                new Object[] {
                    NFT_TRANSFER_ADDRESS, new Address[] {OWNER_ADDRESS}, new Address[] {SPENDER_ALIAS}, new long[] {1}
                },
                new Object[] {}),
        TRANSFER_NFT_TOKEN(
                "transferNFTExternal",
                new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L},
                new Object[] {}),
        TRANSFER_FROM_NFT(
                "transferFromNFTExternal",
                new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L},
                new Object[] {}),
        UPDATE_TOKEN_INFO(
                "updateTokenInfoExternal",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, FUNGIBLE_TOKEN2},
                new Object[] {}),
        UPDATE_TOKEN_EXPIRY(
                "updateTokenExpiryInfoExternal",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, TOKEN_EXPIRY_WRAPPER},
                new Object[] {}),
        UPDATE_TOKEN_KEYS_CONTRACT_ADDRESS(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    }
                },
                new Object[] {}),
        UPDATE_TOKEN_KEYS_DELEGATABLE_CONTRACT_ID(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    }
                },
                new Object[] {}),
        UPDATE_TOKEN_KEYS_ED25519(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    }
                },
                new Object[] {}),
        UPDATE_TOKEN_KEYS_ECDSA(
                "updateTokenKeysExternal",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            АLL_CASES_KEY_TYPE,
                            new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}
                        }
                    }
                },
                new Object[] {});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResult;
    }
}
