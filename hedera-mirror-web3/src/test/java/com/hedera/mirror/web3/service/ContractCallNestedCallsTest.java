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
        if (contractFunctions.shouldFail) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(InvalidTransactionException.class);
        } else {
            contractCallService.processCall(serviceParameters);
        }
    }

    @RequiredArgsConstructor
    enum NestedCallsContractFunctions {
        //        MINT_FUNGIBLE_TOKEN(
        //                "mintTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 100L, new byte[0][0], TREASURY_ADDRESS}),
        //        MINT_NFT("mintTokenGetTotalSupplyAndBalanceOfTreasury", new Object[] {
        //            NFT_ADDRESS, 0L, new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}, OWNER_ADDRESS
        //        }),
        //        BURN_FUNGIBLE_TOKEN(
        //                "burnTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 12L, new long[0], TREASURY_ADDRESS}),
        //        BURN_NFT(
        //                "burnTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NFT_ADDRESS, 0L, new long[] {1L}, OWNER_ADDRESS}),
        //        WIPE_FUNGIBLE_TOKEN(
        //                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 10L, new long[0], SENDER_ADDRESS}),
        //        WIPE_FUNGIBLE_TOKEN_WITH_ALIAS(
        //                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, 10L, new long[0], SENDER_ALIAS}),
        //        WIPE_NFT(
        //                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, 0L, new long[] {1L},
        // SENDER_ADDRESS}),
        //        WIPE_NFT_ALIAS(
        //                "wipeTokenGetTotalSupplyAndBalanceOfTreasury",
        //                new Object[] {NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY, 0L, new long[] {1L},
        // SENDER_ALIAS}),
        //        PAUSE_UNPAUSE_FUNGIBLE_TOKEN(
        //                "pauseTokenGetPauseStatusUnpauseGetPauseStatus",
        //                new Object[] {FUNGIBLE_TOKEN_ADDRESS}),
        //        FREEZE_UNFREEZE_FUNGIBLE_TOKEN(
        //                "freezeTokenGetPauseStatusUnpauseGetPauseStatus",
        //                        new Object[] {NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS}),
        //        PAUSE_UNPAUSE_NFT(
        //                "pauseTokenGetPauseStatusUnpauseGetPauseStatus",
        //                new Object[] {NFT_ADDRESS}),
        //        FREEZE_UNFREEZE_NFT(
        //                "freezeTokenGetPauseStatusUnpauseGetPauseStatus",
        //                new Object[] {NFT_ADDRESS, SPENDER_ADDRESS}),
        //        ASSOCIATE_DISSOCIATE_TRANSFER_FUNGIBLE_TOKEN_FAIL(
        //                "associateTokenDissociateFailTransfer",
        //                        new Object[] {FUNGIBLE_TOKEN_ADDRESS, TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS,
        // BigInteger.ONE, BigInteger.ZERO}, true),
        //        ASSOCIATE_DISSOCIATE_TRANSFER_NFT_FAIL(
        //                "associateTokenDissociateFailTransfer",
        //                new Object[] {NFT_ADDRESS, TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS, BigInteger.ZERO,
        // BigInteger.ONE}, true),
        APPROVE_GET_ALLOWANCE(
                "approveTokenGetAllowance",
                new Object[] {FUNGIBLE_TOKEN_ADDRESS, OWNER_ADDRESS, BigInteger.ONE, BigInteger.ZERO},
                false);
        private final String name;
        private final Object[] functionParameters;
        private final boolean shouldFail;
    }
}
