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

import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallServiceERCTokenTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(ErcContractReadOnlyFunctions.class)
    void ercReadOnlyPrecompileOperationsTest(ErcContractReadOnlyFunctions ercFunction) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ErcContractModificationFunctions.class)
    void supportedErcModificationPrecompileOperationsTest(ErcContractModificationFunctions ercFunction) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)))
                .as("result must be within 5-20% bigger than the gas used from the first call")
                .isGreaterThanOrEqualTo((long) (expectedGasUsed * 1.05)) // expectedGasUsed value increased by 5%
                .isCloseTo(expectedGasUsed, Percentage.withPercentage(20)); // Maximum percentage
    }

    @Test
    void metadataOf() {
        final var functionHash = functionEncodeDecoder.functionHashFor("tokenURI", ERC_ABI_PATH, NFT_ADDRESS, 1L);
        final var serviceParameters = serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThat(contractCallService.processCall(serviceParameters)).isNotEqualTo(Address.ZERO.toString());
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "delegateTransfer", ERC_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L);
        final var serviceParameters = serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @RequiredArgsConstructor
    public enum ErcContractReadOnlyFunctions {
        GET_APPROVED_EMPTY_SPENDER("getApproved", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Boolean[] {true}),
        IS_APPROVE_FOR_ALL_WITH_ALIAS(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS}, new Boolean[] {true}),
        ALLOWANCE_OF(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Long[] {13L}),
        ALLOWANCE_OF_WITH_ALIAS(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS}, new Long[] {13L}),
        GET_APPROVED("getApproved", new Object[] {NFT_ADDRESS, 1L}, new Address[] {SPENDER_ADDRESS}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"HBAR"}),
        BALANCE_OF("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Long[] {12L}),
        BALANCE_OF_WITH_ALIAS("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Long[] {12L}),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 1L}, new Address[] {OWNER_ADDRESS}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    public enum ErcContractModificationFunctions {
        APPROVE("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L}),
        DELETE_ALLOWANCE_NFT("approve", new Object[] {NFT_ADDRESS, Address.ZERO, 1L}),
        APPROVE_NFT("approve", new Object[] {NFT_ADDRESS, SPENDER_ADDRESS, 1L}),
        APPROVE_WITH_ALIAS("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 2L}),
        TRANSFER("transfer", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L}),
        TRANSFER_FROM("transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS, 2L}),
        TRANSFER_FROM_NFT("transferFromNFT", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ADDRESS, 1L}),
        TRANSFER_WITH_ALIAS("transfer", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_WITH_ALIAS(
                "transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_NFT_WITH_ALIAS(
                "transferFromNFT", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L});
        private final String name;
        private final Object[] functionParameters;
    }
}
