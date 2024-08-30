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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServiceERCTokenTest extends ContractCallTestSetup {

    public static final String NON_STATIC_SUFFIX = "NonStatic";

    private static Stream<Arguments> ercContractFunctionArgumentsProviderHistoricalReadOnly() {
        List<BlockType> blockNumbers =
                List.of(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)), BlockType.of(String.valueOf(EVM_V_34_BLOCK)));

        return Arrays.stream(ErcContractReadOnlyFunctionsHistorical.values())
                .flatMap(ercFunction -> Stream.concat(
                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, true, blockNumber)),
                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, false, blockNumber))));
    }

    @ParameterizedTest
    @MethodSource("ercContractFunctionArgumentsProviderHistoricalReadOnly")
    void ercReadOnlyPrecompileHistoricalOperationsTest(
            final ErcContractReadOnlyFunctionsHistorical ercFunction,
            final boolean isStatic,
            final BlockType blockNumber) {
        final var functionName = ercFunction.getName(isStatic);
        final var functionHash =
                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, blockNumber);

        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);

        // Before the block the data did not exist yet
        if (blockNumber.number() < EVM_V_34_BLOCK) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void ercReadOnlyPrecompileHistoricalNotExistingBlockTest(final long blockNumber) {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "isApprovedForAll",
                ERC_ABI_PATH,
                NFT_ADDRESS_HISTORICAL,
                SENDER_ADDRESS_HISTORICAL,
                SPENDER_ADDRESS_HISTORICAL);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.of(String.valueOf(blockNumber)));
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
    public enum ErcContractReadOnlyFunctionsHistorical implements ContractFunctionProviderEnum {
        GET_APPROVED_EMPTY_SPENDER(
                "getApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll",
                new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
                new Boolean[] {true}),
        IS_APPROVE_FOR_ALL_WITH_ALIAS(
                "isApprovedForAll",
                new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL, SPENDER_ALIAS_HISTORICAL},
                new Boolean[] {true}),
        ALLOWANCE_OF(
                "allowance",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL
                },
                new Long[] {13L}),
        ALLOWANCE_OF_WITH_ALIAS(
                "allowance",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL, SPENDER_ALIAS_HISTORICAL},
                new Long[] {13L}),
        GET_APPROVED(
                "getApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Address[] {SPENDER_ALIAS_HISTORICAL}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new String[] {"HBAR"}),
        BALANCE_OF(
                "balanceOf",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
                new Long[] {12L}),
        BALANCE_OF_WITH_ALIAS(
                "balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}, new Long[] {12L
                }),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Address[] {OWNER_ADDRESS_HISTORICAL}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS_HISTORICAL, 2L}, new Address[] {Address.ZERO}),
        TOKEN_URI("tokenURI", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new String[] {"NFT_METADATA_URI"});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;

        public String getName(final boolean isStatic) {
            return isStatic ? name : name + NON_STATIC_SUFFIX;
        }
    }
}
