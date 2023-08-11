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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallNestedCallsTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(NestedCallsContractFunctions.class)
    void nestedCallsTest(NestedCallsContractFunctions contractFunctions) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                contractFunctions.name, NESTED_ETH_CALLS_ABI_PATH, contractFunctions.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L);
        if (contractFunctions.expectedErrorMessage != null) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(InvalidTransactionException.class)
                    .satisfies(ex -> {
                        InvalidTransactionException exception = (InvalidTransactionException) ex;
                        assertEquals(exception.getDetail(), contractFunctions.expectedErrorMessage);
                    });
        } else {
            contractCallService.processCall(serviceParameters);
        }
    }

    @RequiredArgsConstructor
    enum NestedCallsContractFunctions {
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
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 10L, new long[0], SENDER_ADDRESS},
                null),
        WIPE_FUNGIBLE_TOKEN_WITH_ALIAS(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 10L, new long[0], SENDER_ALIAS},
                null),
        WIPE_NFT(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, 0L, new long[] {1L}, SENDER_ADDRESS},
                null),
        WIPE_NFT_ALIAS(
                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, 0L, new long[] {1L}, SENDER_ALIAS},
                null),
        PAUSE_UNPAUSE_FUNGIBLE_TOKEN(
                "pauseTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {FUNGIBLE_TOKEN_ADDRESS}, null),
        FREEZE_UNFREEZE_FUNGIBLE_TOKEN(
                "freezeTokenGetPauseStatusUnpauseGetPauseStatus",
                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS},
                null),
        PAUSE_UNPAUSE_NFT("pauseTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {NFT_ADDRESS}, null),
        FREEZE_UNFREEZE_NFT(
                "freezeTokenGetPauseStatusUnpauseGetPauseStatus", new Object[] {NFT_ADDRESS, SPENDER_ADDRESS}, null),
        ASSOCIATE_DISSOCIATE_TRANSFER_FUNGIBLE_TOKEN_FAIL(
                "associateTokenDissociateFailTransfer",
                new Object[] {
                    TREASURY_TOKEN_ADDRESS,
                    NOT_ASSOCIATED_SPENDER_ADDRESS,
                    NESTED_ETH_CALLS_CONTRACT_ADDRESS,
                    BigInteger.ONE,
                    BigInteger.ZERO
                },
                "IERC20: failed to transfer"),
        ASSOCIATE_DISSOCIATE_TRANSFER_NFT_FAIL(
                "associateTokenDissociateFailTransfer",
                new Object[] {
                    NFT_TRANSFER_ADDRESS, FUNGIBLE_TOKEN_ADDRESS, OWNER_ADDRESS, BigInteger.ZERO, BigInteger.ONE
                },
                "IERC721: failed to transfer"),
        APPROVE_FUNGIBLE_TOKEN_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS, OWNER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {NFT_ADDRESS, SPENDER_ADDRESS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_FROM_GET_ALLOWANCE(
                "approveTokenTransferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_FUNGIBLE_TOKEN_TRANSFER_GET_ALLOWANCE(
                "approveTokenTransferGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                null),
        APPROVE_NFT_TRANSFER_GET_ALLOWANCE(
                "approveTokenTransferGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ADDRESS, BigInteger.ZERO, BigInteger.ONE},
                null),
        APPROVE_CRYPTO_TRANSFER_FUNGIBLE_GET_ALLOWANCE(
                "approveTokenCryptoTransferGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, NESTED_ETH_CALLS_CONTRACT_ADDRESS, SPENDER_ADDRESS, 1L, false},
                null),
        APPROVE_CRYPTO_TRANSFER_NFT_GET_ALLOWANCE(
                "approveTokenCryptoTransferGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, NESTED_ETH_CALLS_CONTRACT_ADDRESS, SPENDER_ADDRESS, 1L, true},
                null),
        APPROVE_FOR_ALL_TRANSFER_FROM_NFT_GET_ALLOWANCE(
                "approveForAllTokenTransferFromGetAllowance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ADDRESS, 1L},
                null),
        APPROVE_FOR_ALL_TRANSFER_NFT_GET_ALLOWANCE(
                "approveForAllTokenTransferGetAllowance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ADDRESS, 1L},
                null),
        APPROVE_FOR_ALL_CRYPTO_TRANSFER_NFT_GET_ALLOWANCE(
                "approveForAllCryptoTransferGetAllowance",
                new Object[] {NFT_TRANSFER_ADDRESS, NESTED_ETH_CALLS_CONTRACT_ADDRESS, SPENDER_ADDRESS, 1L, true},
                null),
        TRANSFER_NFT_GET_ALLOWANCE_OWNER_OF(
                "transferFromNFTGetAllowance", new Object[] {NFT_TRANSFER_ADDRESS, 1L}, null),
        TRANSFER_FUNGIBLE_TOKEN_GET_BALANCE(
                "transferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                null),
        TRANSFER_NFT_GET_OWNER(
                "transferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, SPENDER_ADDRESS, BigInteger.ZERO, BigInteger.ONE},
                null),
        CRYPTO_TRANSFER_FUNFIBLE_TOKEN_GET_OWNER(
                "cryptoTransferFromGetAllowanceGetBalance",
                new Object[] {TREASURY_TOKEN_ADDRESS, NESTED_ETH_CALLS_CONTRACT_ADDRESS, SPENDER_ADDRESS, 1L, false},
                null),
        CRYPTO_TRANSFER_NFT_GET_OWNER(
                "cryptoTransferFromGetAllowanceGetBalance",
                new Object[] {NFT_TRANSFER_ADDRESS, NESTED_ETH_CALLS_CONTRACT_ADDRESS, SPENDER_ADDRESS, 1L, true},
                null);
        private final String name;
        private final Object[] functionParameters;
        private final String expectedErrorMessage;
    }
}
