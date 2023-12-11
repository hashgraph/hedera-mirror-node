/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.properties;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_34_START_BLOCK;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_38_START_BLOCK;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.GENESIS_BLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class MirrorNodeEvmPropertiesTest extends Web3IntegrationTest {
    private static final String EVM_VERSION = "v0.34";
    private static final String EVM_VERSION_34 = EVM_VERSION;
    private static final String EVM_VERSION_30 = "v0.30";
    private static final String EVM_VERSION_38 = "v0.38";
    private static final int MAX_REFUND_PERCENT = 100;
    private static final int MAX_CUSTOM_FEES_ALLOWED = 10;
    private static final Address FUNDING_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000062");
    private static final Bytes32 CHAIN_ID = Bytes32.fromHexString("0x0128");

    private final MirrorNodeEvmProperties properties;

    @BeforeEach
    void setup() {
        properties.getEvmVersions().clear();
    }

    @AfterEach
    void cleanup() {
        properties.setNetwork(HederaNetwork.TESTNET);
    }

    @Test
    void correctPropertiesEvaluation() {
        assertThat(properties.evmVersion()).isEqualTo(EVM_VERSION);
        assertThat(properties.dynamicEvmVersion()).isTrue();
        assertThat(properties.maxGasRefundPercentage()).isEqualTo(MAX_REFUND_PERCENT);
        assertThat(properties.fundingAccountAddress()).isEqualTo(FUNDING_ADDRESS);
        assertThat(properties.isRedirectTokenCallsEnabled()).isTrue();
        assertThat(properties.isLazyCreationEnabled()).isTrue();
        assertThat(properties.isCreate2Enabled()).isTrue();
        assertThat(properties.chainIdBytes32()).isEqualTo(CHAIN_ID);
        assertThat(properties.isLimitTokenAssociations()).isFalse();
        assertThat(properties.shouldAutoRenewAccounts()).isFalse();
        assertThat(properties.shouldAutoRenewContracts()).isFalse();
        assertThat(properties.shouldAutoRenewSomeEntityType()).isFalse();
        assertThat(properties.maxCustomFeesAllowed()).isEqualTo(MAX_CUSTOM_FEES_ALLOWED);
    }

    @ParameterizedTest
    @MethodSource("blockNumberToEvmVersionProvider")
    void getEvmVersionForBlockFromConfig(Long blockNumber, String expectedEvmVersion) {
        // given
        NavigableMap<Long, String> evmVersions = new TreeMap<>();
        evmVersions.put(GENESIS_BLOCK, EVM_VERSION_30);
        evmVersions.put(EVM_VERSION_34_START_BLOCK, EVM_VERSION_34);
        evmVersions.put(EVM_VERSION_38_START_BLOCK, EVM_VERSION_38);
        properties.setEvmVersions(evmVersions);

        String result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    @ParameterizedTest
    @MethodSource("blockNumberToEvmVersionProvider")
    void getEvmVersionForBlockFromHederaNetwork(Long blockNumber, String expectedEvmVersion) {
        // given
        properties.setNetwork(HederaNetwork.MAINNET);

        String result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    private static Stream<Arguments> blockNumberToEvmVersionProvider() {
        return Stream.of(
                Arguments.of(0L, EVM_VERSION_30),
                Arguments.of(1L, EVM_VERSION_30),
                Arguments.of(EVM_VERSION_34_START_BLOCK - 1, EVM_VERSION_30),
                Arguments.of(EVM_VERSION_34_START_BLOCK, EVM_VERSION_34),
                Arguments.of(EVM_VERSION_38_START_BLOCK - 1, EVM_VERSION_34),
                Arguments.of(EVM_VERSION_38_START_BLOCK, EVM_VERSION_38),
                Arguments.of(EVM_VERSION_38_START_BLOCK + 1000, EVM_VERSION_38));
    }
}
