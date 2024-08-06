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

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallNestedCallsHistoricalTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(NestedEthCallContractFunctionsNegativeCases.class)
    void failedNestedCallWithHardcodedResult(final NestedEthCallContractFunctionsNegativeCases func) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(func.name, NESTED_CALLS_ABI_PATH, func.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, NESTED_ETH_CALLS_CONTRACT_ADDRESS, ETH_CALL, 0L, func.block);

        final var successfulResponse =
                functionEncodeDecoder.encodedResultFor(func.name, NESTED_CALLS_ABI_PATH, func.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Getter
    @RequiredArgsConstructor
    enum NestedEthCallContractFunctionsNegativeCases implements ContractFunctionProviderEnum {
        GET_TOKEN_INFO_HISTORICAL(
                "nestedGetTokenInfoAndHardcodedResult",
                new Object[] {NFT_ADDRESS_HISTORICAL},
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        HTS_GET_APPROVED_HISTORICAL(
                "nestedHtsGetApprovedAndHardcodedResult",
                new Object[] {NFT_ADDRESS_HISTORICAL, 1L},
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1))),
        MINT_TOKEN_HISTORICAL(
                "nestedMintTokenAndHardcodedResult",
                new Object[] {
                    NFT_ADDRESS_HISTORICAL,
                    0L,
                    new byte[][] {ByteString.copyFromUtf8("firstMeta").toByteArray()}
                },
                new Object[] {"hardcodedResult"},
                BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
        private final BlockType block;
    }
}
