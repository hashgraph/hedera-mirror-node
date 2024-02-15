/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import java.util.Collections;
import java.util.Map;
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
    private static final String EVM_VERSION_34 = "v0.34";
    private static final String EVM_VERSION_30 = "v0.30";
    private static final String EVM_VERSION_38 = "v0.38";
    private static final String EVM_VERSION_46 = "v0.46";
    private static final String EVM_VERSION = EVM_VERSION_46;
    private static final int MAX_REFUND_PERCENT = 100;
    private static final int MAX_CUSTOM_FEES_ALLOWED = 10;
    private static final Address FUNDING_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000062");
    private static final Bytes32 CHAIN_ID = Bytes32.fromHexString("0x0128");

    private final MirrorNodeEvmProperties properties;

    @BeforeEach
    void setup() {
        properties.setEvmVersions(new TreeMap<>());
    }

    @AfterEach
    void cleanup() {
        properties.setNetwork(HederaNetwork.TESTNET);
        // Restore the default test values so that the changes here
        // would not affect other tests since MirrorNodeEvmProperties
        // is a singleton bean
        properties.setEvmVersions(createEvmVersionsMapCustom());
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
    @MethodSource("blockNumberToEvmVersionProviderMainnet")
    void getEvmVersionForBlockFromHederaNetwork(Long blockNumber, String expectedEvmVersion) {
        // given
        properties.setNetwork(HederaNetwork.MAINNET);

        String result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    @ParameterizedTest
    @MethodSource("blockNumberToEvmVersionProviderCustom")
    void getEvmVersionForBlockFromConfig(Long blockNumber, String expectedEvmVersion) {
        // given
        properties.setEvmVersions(createEvmVersionsMapCustom());

        String result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    private static NavigableMap<Long, String> createEvmVersionsMapCustom() {
        NavigableMap<Long, String> evmVersions = new TreeMap<>();
        evmVersions.put(0L, EVM_VERSION_30);
        evmVersions.put(50L, EVM_VERSION_34);
        evmVersions.put(100L, EVM_VERSION_38);
        return Collections.unmodifiableNavigableMap(evmVersions);
    }

    private static NavigableMap<Long, String> createEvmVersionsMapMainnet() {
        NavigableMap<Long, String> evmVersions = new TreeMap<>();
        evmVersions.put(0L, EVM_VERSION_30);
        evmVersions.put(44029066L, EVM_VERSION_34);
        evmVersions.put(49117794L, EVM_VERSION_38);
        return Collections.unmodifiableNavigableMap(evmVersions);
    }

    private static Stream<Arguments> blockNumberToEvmVersionProviderCustom() {
        return blockNumberToEvmVersionProvider(createEvmVersionsMapCustom());
    }

    private static Stream<Arguments> blockNumberToEvmVersionProviderMainnet() {
        return blockNumberToEvmVersionProvider(createEvmVersionsMapMainnet());
    }

    private static Stream<Arguments> blockNumberToEvmVersionProvider(NavigableMap<Long, String> evmVersions) {
        Stream.Builder<Arguments> argumentsBuilder = Stream.builder();

        Long firstKey = evmVersions.firstKey();
        // return default EVM version for key - 1 since none will be found
        argumentsBuilder.add(Arguments.of(firstKey - 1, EVM_VERSION));

        for (Map.Entry<Long, String> entry : evmVersions.entrySet()) {
            Long key = entry.getKey();
            String currentValue = entry.getValue();
            // Test the block number just before the key (key - 1) if it's not the first key
            if (!key.equals(firstKey)) {
                String lowerValue = evmVersions.lowerEntry(key).getValue();
                argumentsBuilder.add(Arguments.of(key - 1, lowerValue));
            }

            // test the exact key
            argumentsBuilder.add(Arguments.of(key, currentValue));

            // Test the next block number after the key (key + 1)
            argumentsBuilder.add(Arguments.of(key + 1, currentValue));
        }
        return argumentsBuilder.build();
    }
}
