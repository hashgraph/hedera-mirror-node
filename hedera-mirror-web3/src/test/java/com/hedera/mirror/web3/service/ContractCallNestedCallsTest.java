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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.utils.EntityIdUtils;
import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ContractCallNestedCallsTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTestEthCall(NestedEthCallContractFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, NESTED_ETH_CALLS_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, NESTED_ETH_CALLS_ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTestEthEstimateGas(NestedEthCallContractFunctions contractFunc) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, NESTED_ETH_CALLS_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @RequiredArgsConstructor
    enum NestedEthCallContractFunctions {
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    1L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    2L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    4L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    8L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}
                        }
                    },
                    16L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}
                        }
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}
                        }
                    },
                    32L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}
                        }
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}
                        }
                    },
                    64L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}
                        }
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    1L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {1, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    2L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {2, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    4L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {4, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}}
                    },
                    8L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}}
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {8, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}}
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}
                        }
                    },
                    16L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {16, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}
                        }
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}
                        }
                    },
                    32L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {32, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}
                        }
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}
                        }
                    },
                    64L
                },
                new Object[] {false, CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_ED25519_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}
                        }
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, NEW_ED25519_KEY, new byte[0], Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_ECDSA_KEY(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}}
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, new byte[0], NEW_ECDSA_KEY, Address.ZERO}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_DELEGATE_CONTRACT_ID(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {64, new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}
                        }
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], CONTRACT_ADDRESS}),
        UPDATE_TOKEN_EXPIRY_AND_GET_TOKEN_EXPIRY(
                "updateTokenExpiryAndGetUpdatedTokenExpiry",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new TokenExpiryWrapper(
                            4_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 10_000L)
                },
                new Object[] {4_000_000_000L, SENDER_ADDRESS, 10_000L
                }), // 4_000_000_000L in order to fit in uint32 until there is a support for int64 in EvmEncodingFacade
        // to match the Expiry struct in IHederaTokenService
        UPDATE_NFT_TOKEN_EXPIRY_AND_GET_TOKEN_EXPIRY(
                "updateTokenExpiryAndGetUpdatedTokenExpiry",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new TokenExpiryWrapper(
                            4_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(SENDER_ADDRESS), 10_000L)
                },
                new Object[] {4_000_000_000L, SENDER_ADDRESS, 10_000L
                }), // 4_000_000_000L in order to fit in uint32 until there is a support for int64 in EvmEncodingFacade
        // to match the Expiry struct in IHederaTokenService
        UPDATE_TOKEN_INFO_AND_GET_TOKEN_INFO_SYMBOL(
                "updateTokenInfoAndGetUpdatedTokenInfoSymbol",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getSymbol()}),
        UPDATE_NFT_TOKEN_INFO_AND_GET_TOKEN_INFO_SYMBOL(
                "updateTokenInfoAndGetUpdatedTokenInfoSymbol",
                new Object[] {NFT_TRANSFER_ADDRESS, NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getSymbol()}),
        UPDATE_TOKEN_INFO_AND_GET_TOKEN_INFO_NAME(
                "updateTokenInfoAndGetUpdatedTokenInfoName",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getName()}),
        UPDATE_NFT_TOKEN_INFO_AND_GET_TOKEN_INFO_NAME(
                "updateTokenInfoAndGetUpdatedTokenInfoName",
                new Object[] {NFT_TRANSFER_ADDRESS, NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getName()}),
        UPDATE_TOKEN_INFO_AND_GET_TOKEN_INFO_MEMO(
                "updateTokenInfoAndGetUpdatedTokenInfoMemo",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getMemo()}),
        UPDATE_NFT_TOKEN_INFO_AND_GET_TOKEN_INFO_MEMO(
                "updateTokenInfoAndGetUpdatedTokenInfoMemo",
                new Object[] {NFT_TRANSFER_ADDRESS, NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getMemo()}),
        UPDATE_TOKEN_INFO_AND_GET_TOKEN_INFO_AUTO_RENEW_PERIOD(
                "updateTokenInfoAndGetUpdatedTokenInfoAutoRenewPeriod",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getExpiry().autoRenewPeriod()}),
        UPDATE_NFT_TOKEN_INFO_AND_GET_TOKEN_INFO_AUTO_RENEW_PERIOD(
                "updateTokenInfoAndGetUpdatedTokenInfoAutoRenewPeriod",
                new Object[] {NFT_TRANSFER_ADDRESS, NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE},
                new Object[] {
                    NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE.getExpiry().autoRenewPeriod()
                }),
        DELETE_TOKEN_AND_GET_TOKEN_INFO_IS_DELETED(
                "deleteTokenAndGetTokenInfoIsDeleted",
                new Object[] {UNPAUSED_FUNGIBLE_TOKEN_ADDRESS},
                new Object[] {true}),
        DELETE_NFT_TOKEN_AND_GET_TOKEN_INFO_IS_DELETED(
                "deleteTokenAndGetTokenInfoIsDeleted", new Object[] {NFT_TRANSFER_ADDRESS}, new Object[] {true}),
        CREATE_FUNGIBLE_TOKEN_WITH_KEYS(
                "createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {FUNGIBLE_TOKEN_WITH_KEYS, 10L, 10},
                new Object[] {true, true, true}),
        CREATE_FUNGIBLE_TOKEN_NO_KEYS(
                "createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {FUNGIBLE_TOKEN, 10L, 10},
                new Object[] {false, false, true}),
        CREATE_NON_FUNGIBLE_TOKEN_WITH_KEYS(
                "createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {NON_FUNGIBLE_TOKEN_WITH_KEYS, 10L, 10},
                new Object[] {true, true, true}),
        CREATE_NON_FUNGIBLE_TOKEN_NO_KEYS(
                "createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {NON_FUNGIBLE_TOKEN, 10L, 10},
                new Object[] {false, false, true});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }
}
