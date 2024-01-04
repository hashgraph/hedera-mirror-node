/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.service.ContractCallTestConstants.AUTO_RENEW_ACCOUNT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FIXED_FEE_WRAPPER;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FRACTIONAL_FEE_WRAPPER;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FROZEN_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_HBAR_TOKEN_AND_KEYS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN2;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.MODIFICATION_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NEW_ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NEW_ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_HBAR_TOKEN_AND_KEYS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_TRANSFER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NON_FUNGIBLE_TOKEN;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.OWNER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.PRECOMPILE_TEST_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.RECEIVER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.ROYALTY_FEE_WRAPPER;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SPENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SUCCESS_RESULT;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TOKEN_EXPIRY_WRAPPER;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TRANSFRER_FROM_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TREASURY_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TREASURY_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.UNPAUSED_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.longValueOf;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.АLL_CASES_KEY_TYPE;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.ROYALTY_FEE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class ContractCallServicePrecompileTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(ContractReadFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTestEthCall(final ContractReadFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
        switch (contractFunc) {
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE -> customFeePersist(FIXED_FEE);
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE,
                    GET_INFORMATION_FOR_TOKEN_FUNGIBLE,
                    GET_INFORMATION_FOR_TOKEN_NFT,
                    GET_FUNGIBLE_TOKEN_INFO,
                    GET_NFT_INFO -> customFeePersist(FRACTIONAL_FEE);
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE -> customFeePersist(ROYALTY_FEE);
        }
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ContractReadFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTestEthEstimateGas(final ContractReadFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(SupportedContractModificationFunctions.class)
    void evmPrecompileSupportedModificationTokenFunctionsTest(
            final SupportedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final long value = getValue(contractFunc);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, value, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(NestedContractModificationFunctions.class)
    void nestedContractModificationFunctionsTest(final NestedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = SupportedContractModificationFunctions.class,
            mode = Mode.INCLUDE,
            names = {
                "MINT_TOKEN",
                "MINT_NFT_TOKEN",
                "BURN_TOKEN",
                "BURN_NFT_TOKEN",
                "CREATE_FUNGIBLE_TOKEN",
                "CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES",
                "CREATE_NON_FUNGIBLE_TOKEN",
                "CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES"
            })
    void supportedContractModificationFunctionsResponseBodyTest(
            final SupportedContractModificationFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final long value = getValue(contractFunc);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_CALL, value, BlockType.LATEST);
        final var expectedResult = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.expectedResult);
        final var result = contractCallService.processCall(serviceParameters);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void nftInfoForInvalidSerialNo() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "getInformationForNonFungibleToken", PRECOMPILE_TEST_CONTRACT_ABI_PATH, NFT_ADDRESS, 4L);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void tokenInfoForNonTokenAccount() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "getInformationForFungibleToken", PRECOMPILE_TEST_CONTRACT_ABI_PATH, SENDER_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void notExistingPrecompileCallFails() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "callNotExistingPrecompile", MODIFICATION_CONTRACT_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, MODIFICATION_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("Precompile result is null");
    }

    private static long getValue(SupportedContractModificationFunctions contractFunc) {
        return switch (contractFunc) {
            case CREATE_FUNGIBLE_TOKEN,
                    CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES,
                    CREATE_NON_FUNGIBLE_TOKEN,
                    CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES -> 10000 * 100_000_000L;
            default -> 0L;
        };
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
        GET_TOKEN_ADMIN_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 1L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 4L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 8L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 16L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY, 1L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY, 4L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY, 8L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY, 16L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 1L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 4L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 8L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 16L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 1L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_FREEZE_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 4L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_WIPE_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 8L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_SUPPLY_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 16L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 2L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 32L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS, 64L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY, 2L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY, 32L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY, 64L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 2L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 32L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY, 64L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 2L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 32L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID, 64L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
                    new Object[] {100L, FUNGIBLE_TOKEN_ADDRESS, false, false, SENDER_ALIAS},
                    new Object[0],
                    new Object[0]
                }),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE(
                "getCustomFeesForToken",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS},
                new Object[] {new Object[0], new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS}, new Object[0]}),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
                    new Object[0], new Object[0], new Object[] {20L, 10L, 100L, FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}
                }),
        GET_TOKEN_EXPIRY("getExpiryInfoForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY}, new Object[] {
            1000L, AUTO_RENEW_ACCOUNT_ADDRESS, 1800L
        }),
        HTS_GET_APPROVED("htsGetApproved", new Object[] {NFT_ADDRESS, 1L}, new Object[] {SPENDER_ALIAS}),
        HTS_ALLOWANCE(
                "htsAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS},
                new Object[] {13L}),
        HTS_IS_APPROVED_FOR_ALL(
                "htsIsApprovedForAll", new Object[] {NFT_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Object[] {true
                }),
        GET_FUNGIBLE_TOKEN_INFO("getInformationForFungibleToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
            new Object[] {
                FUNGIBLE_HBAR_TOKEN_AND_KEYS,
                12345L,
                false,
                false,
                true,
                new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS},
                "0x01"
            },
            12
        }),
        GET_NFT_INFO("getInformationForNonFungibleToken", new Object[] {NFT_ADDRESS, 1L}, new Object[] {
            new Object[] {
                NFT_HBAR_TOKEN_AND_KEYS,
                1_000_000_000L,
                false,
                false,
                true,
                new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS},
                "0x01"
            },
            1L,
            OWNER_ADDRESS,
            1475067194L,
            "NFT_METADATA_URI".getBytes(),
            SPENDER_ADDRESS
        }),
        GET_INFORMATION_FOR_TOKEN_FUNGIBLE(
                "getInformationForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, new Object[] {
                    FUNGIBLE_HBAR_TOKEN_AND_KEYS,
                    12345L,
                    false,
                    false,
                    true,
                    new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS},
                    "0x01"
                }),
        GET_INFORMATION_FOR_TOKEN_NFT("getInformationForToken", new Object[] {NFT_ADDRESS}, new Object[] {
            NFT_HBAR_TOKEN_AND_KEYS,
            1_000_000_000L,
            false,
            false,
            true,
            new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS},
            "0x01"
        });

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    enum SupportedContractModificationFunctions {
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

    @RequiredArgsConstructor
    enum NestedContractModificationFunctions {
        CREATE_CONTRACT_VIA_CREATE2_AND_TRANSFER_FROM_IT(
                "createContractViaCreate2AndTransferFromIt",
                new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, RECEIVER_ADDRESS, 1L});
        private final String name;
        private final Object[] functionParameters;
    }
}
