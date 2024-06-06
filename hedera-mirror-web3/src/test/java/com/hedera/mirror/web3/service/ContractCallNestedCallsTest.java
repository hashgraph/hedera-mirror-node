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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader.EXCHANGE_RATE_ENTITY_ID;
import static com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader.FEE_SCHEDULE_ENTITY_ID;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
class ContractCallNestedCallsTest extends Web3IntegrationTest {

    // The block numbers lower than EVM v0.34 are considered part of EVM v0.30 which includes all precompiles
    private static final long EVM_V_34_BLOCK = 50L;

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.json")
    private Path NESTED_CALLS_ABI_PATH;

    // Contract addresses
    private static final Address PRECOMPILE_TEST_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256));
    private static final Address NESTED_ETH_CALLS_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1262));

    // Account addresses
    private static final Address AUTO_RENEW_ACCOUNT_ADDRESS = toAddress(EntityId.of(0, 0, 740));
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1043));
    private static final Address SENDER_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1014));
    private static final Address OWNER_ADDRESS = toAddress(EntityId.of(0, 0, 1044));

    // Token addresses
    private static final Address NFT_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1063));
    private static final Address NFT_TRANSFER_ADDRESS = toAddress(EntityId.of(0, 0, 1051));
    private static final Address UNPAUSED_FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1052));

    private static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    private static final byte[] NEW_ECDSA_KEY = new byte[] {
        2, 64, 59, -126, 81, -22, 0, 35, 67, -70, 110, 96, 109, 2, -8, 111, -112, -100, -87, -85, 66, 36, 37, -97, 19,
        68, -87, -110, -13, -115, 74, 86, 90
    };
    private static final byte[] NEW_ED25519_KEY = new byte[] {
        -128, -61, -12, 63, 3, -45, 108, 34, 61, -2, -83, -48, -118, 20, 84, 85, 85, 67, -125, 46, 49, 26, 17, -116, 27,
        25, 38, -95, 50, 77, 40, -38
    };

    // Token Wrappers
    private static final TokenCreateWrapper FUNGIBLE_TOKEN = getToken(false, OWNER_ADDRESS, List.of());
    private static final TokenCreateWrapper FUNGIBLE_TOKEN_WITH_KEYS = getToken(
            true,
            OWNER_ADDRESS,
            List.of(new TokenKeyWrapper(
                    0b1111111,
                    new KeyValueWrapper(
                            false,
                            contractIdFromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe()),
                            new byte[] {},
                            new byte[] {},
                            null))));
    private static final TokenCreateWrapper FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE =
            getToken(false, SENDER_ADDRESS, List.of());
    private static final TokenCreateWrapper FUNGIBLE_TOKEN_INHERIT_KEYS = getToken(
            true,
            OWNER_ADDRESS,
            List.of(new TokenKeyWrapper(
                    0b1111111, new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null))));
    private static final TokenCreateWrapper NON_FUNGIBLE_TOKEN = getToken(
            false,
            OWNER_ADDRESS,
            List.of(new TokenKeyWrapper(
                    0b1111101,
                    new KeyValueWrapper(
                            false,
                            contractIdFromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe()),
                            new byte[] {},
                            new byte[] {},
                            null))));
    private static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_WITH_KEYS = getToken(
            true,
            OWNER_ADDRESS,
            List.of(new TokenKeyWrapper(
                    0b1111111,
                    new KeyValueWrapper(
                            false,
                            contractIdFromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe()),
                            new byte[] {},
                            new byte[] {},
                            null))));
    private static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_EXPIRY_IN_UINT32_RANGE =
            getToken(false, SENDER_ADDRESS, List.of());
    private static final TokenCreateWrapper NON_FUNGIBLE_TOKEN_INHERIT_KEYS = getToken(
            true,
            OWNER_ADDRESS,
            List.of(new TokenKeyWrapper(
                    0b1111111, new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null))));

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.bin")
    private Path NESTED_CALLS_CONTRACT_BYTES_PATH;

    private static RecordFile recordFileBeforeEvm34;

    private final FunctionEncodeDecoder functionEncodeDecoder;

    private final ContractCallService contractCallService;

    private final MirrorEvmTxProcessor processor;

    @BeforeEach
    void setup() {
        historicalBlocksPersist();
        feeSchedulesPersist();
        fileDataPersist();
    }

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctions.class)
    void nestedPrecompileTokenFunctionsTestEthCall(NestedEthCallContractFunctions contractFunc) {
        final var ownerEntityId = ownerEntityPersist();
        final var nftEntityId = nftPersist(NFT_TRANSFER_ADDRESS, AUTO_RENEW_ACCOUNT_ADDRESS, ownerEntityId);

        tokenAccountPersist(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        final var senderEntityId = senderEntityPersist();
        fungibleTokenPersist(senderEntityId, UNPAUSED_FUNGIBLE_TOKEN_ADDRESS, AUTO_RENEW_ACCOUNT_ADDRESS);
        nestedEthCallsContractPersist();
        autoRenewAccountPersist();
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, NESTED_CALLS_ABI_PATH, contractFunc.functionParameters);
        final var value =
                switch (contractFunc) {
                    case CREATE_FUNGIBLE_TOKEN_WITH_KEYS,
                            CREATE_FUNGIBLE_TOKEN_NO_KEYS,
                            CREATE_FUNGIBLE_TOKEN_INHERIT_KEYS,
                            CREATE_NON_FUNGIBLE_TOKEN_WITH_KEYS,
                            CREATE_NON_FUNGIBLE_TOKEN_NO_KEYS,
                            CREATE_NON_FUNGIBLE_TOKEN_INHERIT_KEYS -> 10000 * 100_000_000L;
                    default -> 0L;
                };
        final var serviceParameters = serviceParametersForExecution(
                functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, value, BlockType.LATEST);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                contractFunc.name, NESTED_CALLS_ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctions.class)
    void nestedReadOnlyTokenFunctionsTestEthEstimateGas(NestedEthCallContractFunctions contractFunc) {
        autoRenewAccountPersist();
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunc.name, NESTED_CALLS_ABI_PATH, contractFunc.functionParameters);
        final var value =
                switch (contractFunc) {
                    case CREATE_FUNGIBLE_TOKEN_WITH_KEYS,
                            CREATE_FUNGIBLE_TOKEN_NO_KEYS,
                            CREATE_FUNGIBLE_TOKEN_INHERIT_KEYS,
                            CREATE_NON_FUNGIBLE_TOKEN_WITH_KEYS,
                            CREATE_NON_FUNGIBLE_TOKEN_NO_KEYS,
                            CREATE_NON_FUNGIBLE_TOKEN_INHERIT_KEYS -> 3070 * 100_000_000L;
                    default -> 0L;
                };
        final var serviceParameters = serviceParametersForExecution(
                functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, value, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(Bytes.fromHexString(contractCallService.processCall(serviceParameters))
                        .toLong())
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctionsNegativeCases.class)
    void failedNestedCallWithHardcodedResult(final NestedEthCallContractFunctionsNegativeCases func) {
        nestedEthCallsContractPersist();
        final var functionHash =
                functionEncodeDecoder.functionHashFor(func.name, NESTED_CALLS_ABI_PATH, func.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L, func.block);

        final var successfulResponse =
                functionEncodeDecoder.encodedResultFor(func.name, NESTED_CALLS_ABI_PATH, func.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @RequiredArgsConstructor
    enum NestedEthCallContractFunctions {
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            1,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    1L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            1,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            2,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    2L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            2,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            4,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    4L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            4,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            8,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    8L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            8,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            16,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    16L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            16,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            32,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    32L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            32,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new Object[] {
                        new Object[] {
                            64,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    64L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            64,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_ADMIN_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            1,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    1L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            1,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    1L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_KYC_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            2,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    2L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            2,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    2L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FREEZE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            4,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    4L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            4,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    4L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_WIPE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            8,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    8L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            8,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    8L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_SUPPLY_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            16,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    16L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            16,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    16L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_FEE_SCHEDULE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            32,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    32L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            32,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    32L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_NFT_TOKEN_KEYS_AND_GET_TOKEN_KEY_PAUSE_KEY_CONTRACT_ADDRESS(
                "updateTokenKeysAndGetUpdatedTokenKey",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new Object[] {
                        new Object[] {
                            64,
                            new Object[] {
                                false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO
                            }
                        }
                    },
                    64L
                },
                new Object[] {false, PRECOMPILE_TEST_CONTRACT_ADDRESS, new byte[0], new byte[0], Address.ZERO}),
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
                        new Object[] {
                            64,
                            new Object[] {
                                false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS
                            }
                        }
                    },
                    64L
                },
                new Object[] {false, Address.ZERO, new byte[0], new byte[0], PRECOMPILE_TEST_CONTRACT_ADDRESS}),
        UPDATE_TOKEN_EXPIRY_AND_GET_TOKEN_EXPIRY(
                "updateTokenExpiryAndGetUpdatedTokenExpiry",
                new Object[] {
                    UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                    new TokenExpiryWrapper(
                            4_000_000_000L,
                            EntityIdUtils.accountIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS),
                            8_000_000L)
                },
                new Object[] {4_000_000_000L, AUTO_RENEW_ACCOUNT_ADDRESS, 8_000_000L
                }), // 4_000_000_000L in order to fit in uint32 until there is a support for int64 in EvmEncodingFacade
        // to match the Expiry struct in IHederaTokenService
        UPDATE_NFT_TOKEN_EXPIRY_AND_GET_TOKEN_EXPIRY(
                "updateTokenExpiryAndGetUpdatedTokenExpiry",
                new Object[] {
                    NFT_TRANSFER_ADDRESS,
                    new TokenExpiryWrapper(
                            4_000_000_000L,
                            EntityIdUtils.accountIdFromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS),
                            8_000_000L)
                },
                new Object[] {4_000_000_000L, AUTO_RENEW_ACCOUNT_ADDRESS, 8_000_000L
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
        CREATE_FUNGIBLE_TOKEN_INHERIT_KEYS(
                "createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {FUNGIBLE_TOKEN_INHERIT_KEYS, 10L, 10},
                new Object[] {true, true, true}),
        CREATE_FUNGIBLE_TOKEN_NO_KEYS(
                "createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {FUNGIBLE_TOKEN, 10L, 10},
                new Object[] {false, false, true}),
        CREATE_NON_FUNGIBLE_TOKEN_WITH_KEYS(
                "createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {NON_FUNGIBLE_TOKEN_WITH_KEYS, 10L, 10},
                new Object[] {true, true, true}),
        CREATE_NON_FUNGIBLE_TOKEN_INHERIT_KEYS(
                "createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {NON_FUNGIBLE_TOKEN_INHERIT_KEYS, 10L, 10},
                new Object[] {true, true, true}),
        CREATE_NON_FUNGIBLE_TOKEN_NO_KEYS(
                "createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus",
                new Object[] {NON_FUNGIBLE_TOKEN, 10L, 10},
                new Object[] {false, false, true});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    enum NestedEthCallContractFunctionsNegativeCases {
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

    private EntityId autoRenewAccountPersist() {
        final var autoRenewEntityId = fromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS.toArrayUnsafe());
        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId()).num(autoRenewEntityId.getNum()))
                .persist();
        return autoRenewEntityId;
    }

    private CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallType callType,
            final long value,
            final BlockType block) {
        var sender = block != BlockType.LATEST
                ? new HederaEvmAccount(SENDER_ADDRESS_HISTORICAL)
                : new HederaEvmAccount(SENDER_ADDRESS);

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    // Account persist
    private void tokenAccountPersist(
            final EntityId senderEntityId, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(senderEntityId.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
    }

    private EntityId ownerEntityPersist() {
        final var ownerEntityId = fromEvmAddress(OWNER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId()).num(ownerEntityId.getNum()))
                .persist();
        return ownerEntityId;
    }

    private void historicalBlocksPersist() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
    }

    private void feeSchedulesPersist() {
        final var feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(ContractCall)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build())))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(TokenCreate)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setConstant(7874923918408L)
                                                .setGas(2331415)
                                                .setMax(1000000000000000L)
                                                .build())
                                        .build()))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray()).entityId(FEE_SCHEDULE_ENTITY_ID))
                .persist();
    }

    private static TokenCreateWrapper getToken(
            boolean isFreezeDefault, Address senderAddress, List<TokenKeyWrapper> tokenKeyWrappers) {
        return new TokenCreateWrapper(
                true,
                "Test",
                "TST",
                EntityIdUtils.accountIdFromEvmAddress(senderAddress),
                "test",
                true,
                BigInteger.valueOf(10L),
                BigInteger.valueOf(10L),
                10_000_000L,
                isFreezeDefault,
                tokenKeyWrappers,
                new TokenExpiryWrapper(
                        4_000_000_000L, EntityIdUtils.accountIdFromEvmAddress(senderAddress), 8_000_000L));
    }

    private void fileDataPersist() {
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(2)
                        .setHbarEquiv(31)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                        .build())
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray()).entityId(EXCHANGE_RATE_ENTITY_ID))
                .persist();
    }

    private EntityId nftPersist(final Address nftAddress, final Address autoRenewAddress, final EntityId treasuryId) {
        final var nftEntityId = fromEvmAddress(nftAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(nftEntityId.getNum())
                        .type(TOKEN))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntityId.getId()).treasuryAccountId(treasuryId))
                .persist();

        return nftEntityId;
    }

    private void nestedEthCallsContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(NESTED_CALLS_CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
                        .evmAddress(contractEvmAddress)
                        .key(Key.newBuilder()
                                .setEd25519(ByteString.copyFrom(Arrays.copyOfRange(KEY_PROTO, 3, KEY_PROTO.length)))
                                .build()
                                .toByteArray())
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(contractBytes))
                .persist();
    }

    private void fungibleTokenPersist(
            final EntityId treasuryId, final Address tokenAddress, final Address autoRenewAddress) {
        final var tokenEntityId = fromEvmAddress(tokenAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(tokenEntityId.getNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN))
                .persist();

        domainBuilder
                .token()
                .customize(
                        t -> t.tokenId(tokenEntityId.getId()).treasuryAccountId(EntityId.of(0, 0, treasuryId.getId())))
                .persist();
    }

    private EntityId senderEntityPersist() {
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .balance(10000 * 100_000_000L))
                .persist();

        return senderEntityId;
    }

    private long gasUsedAfterExecution(final CallServiceParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }
}
