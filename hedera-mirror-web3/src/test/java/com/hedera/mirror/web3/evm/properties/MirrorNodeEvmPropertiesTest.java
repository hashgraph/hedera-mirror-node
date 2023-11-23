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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class MirrorNodeEvmPropertiesTest extends Web3IntegrationTest {
    private static final String EVM_VERSION = "v0.34";
    private static final int MAX_REFUND_PERCENT = 100;
    private static final int MAX_CUSTOM_FEES_ALLOWED = 10;
    private static final Address FUNDING_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000062");
    private static final Bytes32 CHAIN_ID = Bytes32.fromHexString("0x0128");

    private final MirrorNodeEvmProperties properties;

    @BeforeEach
    void setup() {
        properties.getEvmPrecompiles().clear();
        properties.getSystemPrecompiles().clear();
        properties.getEvmVersions().clear();
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
    void getEvmVersionForBlock(Long blockNumber, String expectedEvmVersion) {

        //given
        TreeMap<String, Long> evmVersions = new TreeMap<>();
        evmVersions.put("0.30.0", 1000L);
        evmVersions.put("0.34.0", 2000L);
        evmVersions.put("0.38.0", 3000L);
        properties.setEvmVersions(evmVersions);

        String result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    @ParameterizedTest
    @MethodSource("blockNumberAndPrecompilesProvider")
    void getPrecompilesAvailableAtBlock(Long blockNumber, String evmVersion, List<String> expectedPrecompiles) {

        //given
        TreeMap<String, Long> systemPrecompiles = new TreeMap<>();
        systemPrecompiles.put("HTS", 800L);
        systemPrecompiles.put("ERC", 2100L);
        systemPrecompiles.put("exchangeRate", 3100L);
        systemPrecompiles.put("PRNG", 4100L);

        Map<String, List<String>> evmPrecompiles = new HashMap<>();

        evmPrecompiles.put("0.30.0", List.of("HTS"));
        evmPrecompiles.put("0.34.0", Arrays.asList("HTS", "ERC"));
        evmPrecompiles.put("0.38.0", Arrays.asList("HTS", "ERC", "exchangeRate", "PRNG"));

        properties.setSystemPrecompiles(systemPrecompiles);
        properties.setEvmPrecompiles(evmPrecompiles);

        List<String> precompilesAvailableResult = properties.getPrecompilesAvailableAtBlock(blockNumber,
                evmVersion);

        assertThat(precompilesAvailableResult).containsExactlyInAnyOrderElementsOf(expectedPrecompiles);
    }

    private static Stream<Arguments> blockNumberToEvmVersionProvider() {
        return Stream.of(
                Arguments.of(1L, "0.30.0"),
                Arguments.of(800L, "0.30.0"),
                Arguments.of(999L, "0.30.0"),
                Arguments.of(1000L, "0.30.0"),
                Arguments.of(1999L, "0.30.0"),
                Arguments.of(2000L, "0.34.0"),
                Arguments.of(2999L, "0.34.0"),
                Arguments.of(3000L, "0.38.0"),
                Arguments.of(5000L, "0.38.0")
        );
    }

    private static Stream<Arguments> blockNumberAndPrecompilesProvider() {
        return Stream.of(
                Arguments.of(1L, "0.30.0", Collections.EMPTY_LIST),
                Arguments.of(500L, "0.30.0", Collections.EMPTY_LIST),
                Arguments.of(800L, "0.30.0", List.of("HTS")),
                Arguments.of(1099L, "0.30.0", List.of("HTS")),
                Arguments.of(1100L, "0.30.0", List.of("HTS")),
                Arguments.of(2000L, "0.34.0", List.of("HTS")),
                Arguments.of(3000L, "0.38.0", Arrays.asList("HTS", "ERC")),
                Arguments.of(3100L, "0.38.0", Arrays.asList("HTS", "ERC", "exchangeRate")),
                Arguments.of(4000L, "0.38.0", Arrays.asList("HTS", "ERC", "exchangeRate")),
                Arguments.of(4100L, "0.38.0", Arrays.asList("HTS", "ERC", "exchangeRate", "PRNG")),
                Arguments.of(5000L, "0.38.0", Arrays.asList("HTS", "ERC", "exchangeRate", "PRNG"))
        );
    }
}
