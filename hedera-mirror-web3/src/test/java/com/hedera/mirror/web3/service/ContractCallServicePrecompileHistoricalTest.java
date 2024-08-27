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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.LEDGER_ID;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.ROYALTY_FEE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServicePrecompileHistoricalTest extends ContractCallTestSetup {
    private static Stream<Arguments> htsContractFunctionArgumentsProviderHistoricalReadOnly() {
        List<String> blockNumbers = List.of(String.valueOf(EVM_V_34_BLOCK - 1), String.valueOf(EVM_V_34_BLOCK));

        return Arrays.stream(ContractReadFunctionsHistorical.values()).flatMap(htsFunction -> blockNumbers.stream()
                .map(blockNumber -> Arguments.of(htsFunction, blockNumber)));
    }

    @ParameterizedTest
    @MethodSource("htsContractFunctionArgumentsProviderHistoricalReadOnly")
    void evmPrecompileReadOnlyTokenFunctionsTestEthCallHistorical(
            final ContractReadFunctionsHistorical contractFunc, final String blockNumber) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, PRECOMPILE_TEST_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.of(blockNumber));
        switch (contractFunc) {
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE -> customFeePersistHistorical(
                    FIXED_FEE,
                    Range.closedOpen(
                            recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE,
                    GET_INFORMATION_FOR_TOKEN_FUNGIBLE,
                    GET_INFORMATION_FOR_TOKEN_NFT,
                    GET_FUNGIBLE_TOKEN_INFO,
                    GET_NFT_INFO -> customFeePersistHistorical(
                    FRACTIONAL_FEE,
                    Range.closedOpen(
                            recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            case GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE -> customFeePersistHistorical(
                    ROYALTY_FEE,
                    Range.closedOpen(
                            recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            default -> {
                // Do nothing
            }
        }
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, PRECOMPILE_TEST_CONTRACT_ABI_PATH, contractFunc.expectedResultFields);

        if (Long.parseLong(blockNumber) < EVM_V_34_BLOCK) {
            switch (contractFunc) {
                    // These are the only cases where an exception is not thrown. There are custom
                    // precompiles for these cases and an exception is thrown there but the top
                    // stack frame overrides the result with the one from Besu and the zero address
                    // is returned.
                case HTS_GET_APPROVED, HTS_ALLOWANCE, HTS_IS_APPROVED_FOR_ALL -> {
                    assertThat(contractCallService.processCall(serviceParameters))
                            .isEqualTo(String.valueOf(Bytes32.ZERO));
                    return;
                }
                default -> {
                    // Do nothing
                }
            }
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void evmPrecompileReadOnlyTokenFunctionsEthCallHistoricalNotExistingBlockTest(final long blockNumber) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "isTokenFrozen",
                PRECOMPILE_TEST_CONTRACT_ABI_PATH,
                FUNGIBLE_TOKEN_ADDRESS_HISTORICAL,
                SENDER_ADDRESS_HISTORICAL);
        final var serviceParameters = serviceParametersForExecution(
                functionHash,
                PRECOMPILE_TEST_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.of(String.valueOf(blockNumber)));

        final var latestBlockNumber = recordFileRepository.findLatestIndex().orElse(Long.MAX_VALUE);

        // Block number (Long.MAX_VALUE - 1) does not exist in the DB and is after the
        // latest block available in the DB => returning error
        if (blockNumber > latestBlockNumber) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberOutOfRangeException.class);
        } else if (blockNumber == 51) {
            // Block number 51 = (EVM_V_34_BLOCK + 1) does not exist in the DB but it is before the latest
            // block available in the DB => throw an exception
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberNotFoundException.class);
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractReadFunctionsHistorical implements ContractFunctionProviderEnum {
        IS_FROZEN(
                "isTokenFrozen",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
                new Boolean[] {true}),
        IS_FROZEN_WITH_ALIAS(
                "isTokenFrozen",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL},
                new Boolean[] {true}),
        IS_KYC(
                "isKycGranted",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
                new Boolean[] {true}),
        IS_KYC_WITH_ALIAS(
                "isKycGranted",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL},
                new Boolean[] {true}),
        IS_KYC_FOR_NFT(
                "isKycGranted", new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL}, new Boolean[] {true
                }),
        IS_KYC_FOR_NFT_WITH_ALIAS(
                "isKycGranted", new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE("isTokenAddress", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE_NFT("isTokenAddress", new Address[] {NFT_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC(
                "getTokenDefaultKyc", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC_NFT("getTokenDefaultKyc", new Address[] {NFT_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_TYPE("getType", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Long[] {0L}),
        GET_TOKEN_TYPE_FOR_NFT("getType", new Address[] {NFT_ADDRESS_HISTORICAL}, new Long[] {1L}),
        GET_TOKEN_DEFAULT_FREEZE(
                "getTokenDefaultFreeze", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_FREEZE_FOR_NFT(
                "getTokenDefaultFreeze", new Address[] {NFT_ADDRESS_HISTORICAL}, new Boolean[] {true}),
        GET_TOKEN_ADMIN_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 1L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 4L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 8L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 16L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 1L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 4L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 8L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 16L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 1L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_FREEZE_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 4L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_WIPE_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 8L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_SUPPLY_KEY_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 16L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_ADMIN_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 1L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_FREEZE_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 4L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_WIPE_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 8L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_SUPPLY_KEY_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 16L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 2L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 32L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_CONTRACT_ADDRESS(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS_HISTORICAL, 64L},
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 2L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 32L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_ED25519_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY_HISTORICAL, 64L},
                new Object[] {false, Address.ZERO, ED25519_KEY, new byte[0], Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 2L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 32L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_ECDSA_KEY(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY_HISTORICAL, 64L},
                new Object[] {false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO}),
        GET_TOKEN_KYC_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 2L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_FEE_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 32L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_TOKEN_PAUSE_KEY_FOR_NFT_WITH_DELEGATABLE_CONTRACT_ID(
                "getTokenKeyPublic",
                new Object[] {NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID_HISTORICAL, 64L},
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FIXED_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[] {100L, FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, false, false, SENDER_ALIAS_HISTORICAL},
                    new Object[0],
                    new Object[0]
                }),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_FRACTIONAL_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[0], new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS_HISTORICAL}, new Object[0]
                }),
        GET_CUSTOM_FEES_FOR_TOKEN_WITH_ROYALTY_FEE(
                "getCustomFeesForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[0],
                    new Object[0],
                    new Object[] {20L, 10L, 100L, FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}
                }),
        GET_TOKEN_EXPIRY(
                "getExpiryInfoForToken",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY_HISTORICAL},
                new Object[] {1000L, AUTO_RENEW_ACCOUNT_ADDRESS_HISTORICAL, 8_000_000L}),
        HTS_GET_APPROVED(
                "htsGetApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Object[] {SPENDER_ALIAS_HISTORICAL}),
        HTS_ALLOWANCE(
                "htsAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
                new Object[] {13L}),
        HTS_IS_APPROVED_FOR_ALL(
                "htsIsApprovedForAll",
                new Object[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
                new Object[] {true}),
        GET_FUNGIBLE_TOKEN_INFO(
                "getInformationForFungibleToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    new Object[] {
                        FUNGIBLE_HBAR_TOKEN_AND_KEYS_HISTORICAL,
                        12345L,
                        false,
                        false,
                        true,
                        new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS_HISTORICAL},
                        LEDGER_ID
                    },
                    12
                }),
        GET_NFT_INFO("getInformationForNonFungibleToken", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Object[] {
            new Object[] {
                NFT_HBAR_TOKEN_AND_KEYS_HISTORICAL,
                2L,
                false,
                false,
                true,
                new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS_HISTORICAL},
                LEDGER_ID
            },
            1L,
            OWNER_ADDRESS_HISTORICAL,
            1475067194L,
            "NFT_METADATA_URI".getBytes(),
            SPENDER_ADDRESS_HISTORICAL
        }),
        GET_INFORMATION_FOR_TOKEN_FUNGIBLE(
                "getInformationForToken", new Object[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Object[] {
                    FUNGIBLE_HBAR_TOKEN_AND_KEYS_HISTORICAL,
                    12345L,
                    false,
                    false,
                    true,
                    new Object[] {100L, 10L, 1L, 1000L, true, SENDER_ALIAS_HISTORICAL},
                    LEDGER_ID
                }),
        GET_INFORMATION_FOR_TOKEN_NFT("getInformationForToken", new Object[] {NFT_ADDRESS_HISTORICAL}, new Object[] {
            NFT_HBAR_TOKEN_AND_KEYS_HISTORICAL,
            2L,
            false,
            false,
            true,
            new Object[] {0L, 0L, 0L, 0L, false, SENDER_ALIAS_HISTORICAL},
            LEDGER_ID
        });

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }
}
