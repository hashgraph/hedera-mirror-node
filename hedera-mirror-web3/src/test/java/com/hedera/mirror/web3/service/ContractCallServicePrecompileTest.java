/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.FunctionEncodeDecoder.convertAddress;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallServicePrecompileTest extends ContractCallTestSetup {
    private static final String ERROR_MESSAGE = "Precompile not supported for non-static frames";

    @ParameterizedTest
    @EnumSource(ContractReadFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTest(ContractReadFunctions contractFunc) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(contractFunc.name, ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(functionHash, CONTRACT_ADDRESS, ETH_CALL, 0L);
        final var successfulResponse =
                functionEncodeDecoder.encodedResultFor(contractFunc.name, ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ContractReadFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTestWithNonStaticFrame(ContractReadFunctions contractFunc) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(contractFunc.name, ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(functionHash, CONTRACT_ADDRESS, ETH_CALL, 0L);
        final var successfulResponse =
                functionEncodeDecoder.encodedResultFor(contractFunc.name, ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(UnsupportedContractModificationFunctions.class)
    void evmPrecompileUnsupportedModificationTokenFunctionsTest(UnsupportedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashWithEmptyDataFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage(ERROR_MESSAGE);
    }

    @ParameterizedTest
    @EnumSource(SupportedContractModificationFunctions.class)
    void evmPrecompileSupportedModificationTokenFunctionsTest(SupportedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @ParameterizedTest
    @EnumSource(FeeCase.class)
    void customFees(FeeCase feeCase) {
        final var functionName = "getCustomFeesForToken";
        final var functionHash = functionEncodeDecoder.functionHashFor(functionName, ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(functionHash, CONTRACT_ADDRESS, ETH_CALL, 0L);
        customFeesPersist(feeCase);

        final var callResult = contractCallService.processCall(serviceParameters);
        final var decodeResult = functionEncodeDecoder.decodeResult(functionName, ABI_PATH, callResult);
        final Tuple[] fixedFee = decodeResult.get(0);
        final Tuple[] fractionalFee = decodeResult.get(1);
        final Tuple[] royaltyFee = decodeResult.get(2);

        switch (feeCase) {
            case FIXED_FEE -> {
                assertThat((long) fixedFee[0].get(0)).isEqualTo(100L);
                assertThat((com.esaulpaugh.headlong.abi.Address) fixedFee[0].get(1))
                        .isEqualTo(convertAddress(FUNGIBLE_TOKEN_ADDRESS));
                assertThat((boolean) fixedFee[0].get(2)).isFalse();
                assertThat((boolean) fixedFee[0].get(3)).isFalse();
                assertThat((com.esaulpaugh.headlong.abi.Address) fixedFee[0].get(4))
                        .isEqualTo(convertAddress(SENDER_ALIAS));
            }
            case FRACTIONAL_FEE -> {
                assertThat((long) fractionalFee[0].get(0)).isEqualTo(100L);
                assertThat((long) fractionalFee[0].get(1)).isEqualTo(10L);
                assertThat((long) fractionalFee[0].get(2)).isEqualTo(1L);
                assertThat((long) fractionalFee[0].get(3)).isEqualTo(1000L);
                assertThat((boolean) fractionalFee[0].get(4)).isTrue();
                assertThat((com.esaulpaugh.headlong.abi.Address) fractionalFee[0].get(5))
                        .isEqualTo(convertAddress(SENDER_ALIAS));
            }
            case ROYALTY_FEE -> {
                assertThat((long) royaltyFee[0].get(0)).isEqualTo(20L);
                assertThat((long) royaltyFee[0].get(1)).isEqualTo(10L);
                assertThat((long) royaltyFee[0].get(2)).isEqualTo(100L);
                assertThat((com.esaulpaugh.headlong.abi.Address) royaltyFee[0].get(3))
                        .isEqualTo(convertAddress(FUNGIBLE_TOKEN_ADDRESS));
                assertThat((boolean) royaltyFee[0].get(4)).isFalse();
                assertThat((com.esaulpaugh.headlong.abi.Address) royaltyFee[0].get(5))
                        .isEqualTo(convertAddress(SENDER_ALIAS));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"getInformationForFungibleToken,false", "getInformationForNonFungibleToken,true"})
    void getTokenInfo(String functionName, boolean isNft) {
        final var functionHash = isNft
                ? functionEncodeDecoder.functionHashFor(functionName, ABI_PATH, NFT_ADDRESS, 1L)
                : functionEncodeDecoder.functionHashFor(functionName, ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(functionHash, CONTRACT_ADDRESS, ETH_CALL, 0L);
        customFeesPersist(FRACTIONAL_FEE);

        final var callResult = contractCallService.processCall(serviceParameters);
        final Tuple decodeResult = functionEncodeDecoder
                .decodeResult(functionName, ABI_PATH, callResult)
                .get(0);
        Tuple tokenInfo = decodeResult.get(0);
        Tuple hederaToken = tokenInfo.get(0);
        boolean deleted = tokenInfo.get(2);
        boolean defaultKycStatus = tokenInfo.get(3);
        boolean pauseStatus = tokenInfo.get(4);
        Tuple[] fractionalFees = tokenInfo.get(6);
        String ledgerId = tokenInfo.get(8);
        String name = hederaToken.get(0);
        String symbol = hederaToken.get(1);
        com.esaulpaugh.headlong.abi.Address treasury = hederaToken.get(2);
        String memo = hederaToken.get(3);
        boolean supplyType = hederaToken.get(4);
        long maxSupply = hederaToken.get(5);
        boolean freezeStatus = hederaToken.get(6);
        Tuple expiry = hederaToken.get(8);
        com.esaulpaugh.headlong.abi.Address autoRenewAccount = expiry.get(1);
        long autoRenewPeriod = expiry.get(2);

        assertThat(deleted).isFalse();
        assertThat(defaultKycStatus).isFalse();
        assertThat(pauseStatus).isTrue();
        assertThat(fractionalFees).isNotEmpty();
        assertThat(ledgerId).isEqualTo("0x01");
        assertThat(name).isEqualTo("Hbars");
        assertThat(symbol).isEqualTo("HBAR");
        assertThat(treasury).isEqualTo(convertAddress(OWNER_ADDRESS));
        assertThat(memo).isEqualTo("TestMemo");
        assertThat(freezeStatus).isTrue();
        assertThat(autoRenewPeriod).isEqualTo(1800L);

        if (isNft) {
            long serialNum = decodeResult.get(1);
            com.esaulpaugh.headlong.abi.Address owner = decodeResult.get(2);
            long creationTime = decodeResult.get(3);
            byte[] metadata = decodeResult.get(4);
            com.esaulpaugh.headlong.abi.Address spender = decodeResult.get(5);

            assertThat(serialNum).isEqualTo(1L);
            assertThat(owner).isEqualTo(convertAddress(OWNER_ADDRESS));
            assertThat(creationTime).isEqualTo(1475067194L);
            assertThat(metadata).isNotEmpty();
            assertThat(spender).isEqualTo(convertAddress(SPENDER_ADDRESS));
            assertThat(maxSupply).isEqualTo(2000000000L);
            assertThat(supplyType).isTrue();
            assertThat(autoRenewAccount).isEqualTo(convertAddress(NFT_ADDRESS));
        } else {
            int decimals = decodeResult.get(1);
            long totalSupply = tokenInfo.get(1);
            assertThat(decimals).isEqualTo(12);
            assertThat(totalSupply).isEqualTo(12345L);
            assertThat(maxSupply).isEqualTo(2525L);
            assertThat(supplyType).isFalse();
            assertThat(autoRenewAccount).isEqualTo(convertAddress(FUNGIBLE_TOKEN_ADDRESS));
        }
    }

    @Test
    void nftInfoForInvalidSerialNo() {
        final var functionHash =
                functionEncodeDecoder.functionHashFor("getInformationForNonFungibleToken", ABI_PATH, NFT_ADDRESS, 4L);
        final var serviceParameters = serviceParametersForExecution(functionHash, CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void tokenInfoForNonTokenAccount() {
        final var functionHash =
                functionEncodeDecoder.functionHashFor("getInformationForFungibleToken", ABI_PATH, SENDER_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(functionHash, CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void notExistingPrecompileCallFails() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "callNotExistingPrecompile", MODIFICATION_CONTRACT_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage(ERROR_MESSAGE);
    }

    @RequiredArgsConstructor
    enum ContractReadFunctions {
        IS_FROZEN("isTokenFrozen", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Boolean[] {true}),
        IS_FROZEN_WITH_ALIAS(
                "isTokenFrozen", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Boolean[] {true}),
        IS_KYC("isKycGranted", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Boolean[] {true}),
        IS_KYC_WITH_ALIAS("isKycGranted", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Boolean[] {true}),
        IS_KYC_FOR_NFT("isKycGranted", new Address[] {NFT_ADDRESS, SENDER_ADDRESS}, new Boolean[] {true}),
        IS_KYC_FOR_NFT_WITH_ALIAS("isKycGranted", new Address[] {NFT_ADDRESS, SENDER_ALIAS}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE("isTokenAddress", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE_NFT("isTokenAddress", new Address[] {NFT_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC("getTokenDefaultKyc", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC_NFT("getTokenDefaultKyc", new Address[] {NFT_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_TYPE("getType", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {0L}),
        GET_TOKEN_TYPE_FOR_NFT("getType", new Address[] {NFT_ADDRESS}, new Long[] {1L}),
        GET_TOKEN_DEFAULT_FREEZE("getTokenDefaultFreeze", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_FREEZE_FOR_NFT("getTokenDefaultFreeze", new Address[] {NFT_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_ADMIN_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 1L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_FREEZE_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 4L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_WIPE_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 8L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_SUPPLY_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 16L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_KYC_KEY_FOR_NFT("getTokenKeyPublic", new Object[] {NFT_ADDRESS, 2L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_FEE_KEY_FOR_NFT("getTokenKeyPublic", new Object[] {NFT_ADDRESS, 32L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_PAUSE_KEY_FOR_NFT("getTokenKeyPublic", new Object[] {NFT_ADDRESS, 64L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        });

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    enum UnsupportedContractModificationFunctions {
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", new Object[] {EMPTY_ADDRESS, new Object[] {}}),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", new Object[] {EMPTY_ADDRESS, new Object[] {}}),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", new Object[] {EMPTY_ADDRESS, new Object[] {}});

        private final String name;
        private final Object[] functionParameters;
    }

    @RequiredArgsConstructor
    enum SupportedContractModificationFunctions {
        APPROVE("approveExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 1L}),
        DELETE_ALLOWANCE("approveExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 0L}),
        DELETE_ALLOWANCE_NFT("approveNFTExternal", new Object[] {NFT_ADDRESS, Address.ZERO, 1L}),
        APPROVE_NFT("approveNFTExternal", new Object[] {NFT_ADDRESS, TREASURY_ADDRESS, 1L}),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", new Object[] {NFT_ADDRESS, TREASURY_ADDRESS, true}),
        ASSOCIATE_TOKEN("associateTokenExternal", new Object[] {SPENDER_ADDRESS, FUNGIBLE_TOKEN_ADDRESS}),
        ASSOCIATE_TOKEN_WITH_ALIAS("associateTokenExternal", new Object[] {SPENDER_ALIAS, FUNGIBLE_TOKEN_ADDRESS}),
        ASSOCIATE_TOKENS(
                "associateTokensExternal", new Object[] {SPENDER_ADDRESS, new Address[] {FUNGIBLE_TOKEN_ADDRESS}}),
        ASSOCIATE_TOKENS_WITH_ALIAS(
                "associateTokensExternal", new Object[] {SPENDER_ALIAS, new Address[] {FUNGIBLE_TOKEN_ADDRESS}}),
        MINT_TOKEN("mintTokenExternal", new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 100L, new byte[0][0]}),
        MINT_NFT_TOKEN("mintTokenExternal", new Object[] {
            NFT_ADDRESS, 0L, new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
        }),
        DISSOCIATE_TOKEN("dissociateTokenExternal", new Object[] {SPENDER_ADDRESS, TREASURY_TOKEN_ADDRESS}),
        DISSOCIATE_TOKEN_WITH_ALIAS("dissociateTokenExternal", new Object[] {SPENDER_ALIAS, TREASURY_TOKEN_ADDRESS}),
        DISSOCIATE_TOKENS(
                "dissociateTokensExternal", new Object[] {SPENDER_ADDRESS, new Address[] {TREASURY_TOKEN_ADDRESS}}),
        DISSOCIATE_TOKENS_WITH_ALIAS(
                "dissociateTokensExternal", new Object[] {SPENDER_ALIAS, new Address[] {TREASURY_TOKEN_ADDRESS}}),
        BURN_TOKEN("burnTokenExternal", new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 1L, new long[0]}),
        WIPE_TOKEN("wipeTokenAccountExternal", new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 1L}),
        WIPE_TOKEN_WITH_ALIAS(
                "wipeTokenAccountExternal", new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 1L}),
        WIPE_NFT_TOKEN(
                "wipeTokenAccountNFTExternal",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, SENDER_ADDRESS, new long[] {1}}),
        WIPE_NFT_TOKEN_WITH_ALIAS(
                "wipeTokenAccountNFTExternal",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, SENDER_ALIAS, new long[] {1}}),
        BURN_NFT_TOKEN("burnTokenExternal", new Object[] {NFT_ADDRESS, 0L, new long[] {1}}),
        REVOKE_TOKEN_KYC("revokeTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}),
        REVOKE_TOKEN_KYC_WITH_ALIAS("revokeTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}),
        GRANT_TOKEN_KYC("grantTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}),
        GRANT_TOKEN_KYC_WITH_ALIAS("grantTokenKycExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}),
        DELETE_TOKEN("deleteTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}),
        FREEZE_TOKEN("freezeTokenExternal", new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS}),
        UNFREEZE_TOKEN("unfreezeTokenExternal", new Object[] {FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS}),
        PAUSE_TOKEN("pauseTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}),
        UNPAUSE_TOKEN("unpauseTokenExternal", new Object[] {FUNGIBLE_TOKEN_ADDRESS}),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenExternal", new Object[] {FUNGIBLE_TOKEN, 10L, 10}),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createFungibleTokenWithCustomFeesExternal",
                new Object[] {FUNGIBLE_TOKEN, 10L, 10, FIXED_FEE_WRAPPER, FRACTIONAL_FEE_WRAPPER}),
        CREATE_NON_FUNGIBLE_TOKEN("createNonFungibleTokenExternal", new Object[] {NON_FUNGIBLE_TOKEN}),
        CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createNonFungibleTokenWithCustomFeesExternal",
                new Object[] {NON_FUNGIBLE_TOKEN, FIXED_FEE_WRAPPER, ROYALTY_FEE_WRAPPER}),
        TRANSFER_TOKEN(
                "transferTokenExternal", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS, SENDER_ADDRESS, 1L}),
        TRANSFER_TOKENS("transferTokensExternal", new Object[] {
            TREASURY_TOKEN_ADDRESS, new Address[] {SPENDER_ADDRESS, SENDER_ADDRESS}, new long[] {1L, -1L}
        }),
        TRANSFER_NFT_TOKENS("transferNFTsExternal", new Object[] {
            NFT_TRANSFER_ADDRESS, new Address[] {OWNER_ADDRESS}, new Address[] {SPENDER_ADDRESS}, new long[] {1}
        }),
        TRANSFER_NFT_TOKEN(
                "transferNFTExternal", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ADDRESS, 1L}),
        TRANSFER_FROM(
                "transferFromExternal", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS, 1L}),
        TRANSFER_FROM_NFT(
                "transferFromNFTExternal", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ADDRESS, 1L});

        private final String name;
        private final Object[] functionParameters;
    }
}
