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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.DYNAMIC_ETH_CALLS_CONTRACT_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_TRANSFER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_ASSOCIATED_SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.OWNER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.RECEIVER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TREASURY_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TREASURY_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.longValueOf;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallDynamicCallsTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(DynamicCallsContractFunctions.class)
    void dynamicCallsTestWithAliasSenderForEthCall(DynamicCallsContractFunctions contractFunctions) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunctions.name, DYNAMIC_ETH_CALLS_ABI_PATH, contractFunctions.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, ETH_CALL, 0L, BlockType.LATEST);
        if (contractFunctions.expectedErrorMessage != null) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .satisfies(ex -> {
                        MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                        assertEquals(exception.getDetail(), contractFunctions.expectedErrorMessage);
                    });
        } else {
            contractCallService.processCall(serviceParameters);
        }
    }

    @ParameterizedTest
    @EnumSource(DynamicCallsContractFunctions.class)
    void dynamicCallsTestWithAliasSenderForEstimateGas(DynamicCallsContractFunctions contractFunctions) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunctions.name, DYNAMIC_ETH_CALLS_ABI_PATH, contractFunctions.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, DYNAMIC_ETH_CALLS_CONTRACT_ALIAS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);
        if (contractFunctions.expectedErrorMessage != null) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .satisfies(ex -> {
                        MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                        assertEquals(exception.getDetail(), contractFunctions.expectedErrorMessage);
                    });
        } else {
            final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
            assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                    .as("result must be within 5-20% bigger than the gas used from the first call")
                    .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                    .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
        }
    }

    @RequiredArgsConstructor
    enum DynamicCallsContractFunctions {
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
                    toAddress(EntityId.of(0, 0, 1)), // Not persisted address
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
        GRANT_KYC_REVOKE_KYC_NFT("grantKycRevokeKyc", new Object[] {NFT_ADDRESS, SENDER_ALIAS}, null);
        private final String name;
        private final Object[] functionParameters;
        private final String expectedErrorMessage;
    }
}
