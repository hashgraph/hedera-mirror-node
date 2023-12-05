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
import java.util.Set;
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
    private static final String EVM_VERSION_34 = EVM_VERSION;
    private static final String EVM_VERSION_30 = "v0.30";
    private static final String EVM_VERSION_38 = "v0.38";
    private static final String ERC = "ERC";
    private static final String HTS = "HTS";
    private static final String EXCHANGE_RATE = "exchangeRate";
    private static final String PRNG = "PRNG";
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

        // given
        TreeMap<String, Long> evmVersions = new TreeMap<>();
        evmVersions.put(EVM_VERSION_30, 1000L);
        evmVersions.put(EVM_VERSION_34, 2000L);
        evmVersions.put(EVM_VERSION_38, 3000L);
        properties.setEvmVersions(evmVersions);

        String result = properties.getEvmVersionForBlock(blockNumber);
        assertThat(result).isEqualTo(expectedEvmVersion);
    }

    @ParameterizedTest
    @MethodSource("blockNumberAndPrecompilesProvider")
    void getPrecompilesAvailableAtBlock(Long blockNumber, String evmVersion, List<String> expectedPrecompiles) {

        // given
        TreeMap<String, Long> systemPrecompiles = new TreeMap<>();
        systemPrecompiles.put(HTS, 800L);
        systemPrecompiles.put(ERC, 2100L);
        systemPrecompiles.put(EXCHANGE_RATE, 3100L);
        systemPrecompiles.put(PRNG, 4100L);

        Map<String, Set<String>> evmPrecompiles = new HashMap<>();

        evmPrecompiles.put(EVM_VERSION_30, Set.of(HTS));
        evmPrecompiles.put(EVM_VERSION_34, Set.of(HTS, ERC));
        evmPrecompiles.put(EVM_VERSION_38, Set.of(HTS, ERC, EXCHANGE_RATE, PRNG));

        properties.setSystemPrecompiles(systemPrecompiles);
        properties.setEvmPrecompiles(evmPrecompiles);

        Set<String> precompilesAvailableResult = properties.getPrecompilesAvailableAtBlock(blockNumber, evmVersion);

        assertThat(precompilesAvailableResult).containsExactlyInAnyOrderElementsOf(expectedPrecompiles);
    }

    @ParameterizedTest
    @MethodSource("evmWithPrecompilesProvider")
    void testGetEvmWithPrecompiles(String evmVersion, Set<String> precompiles, String expected) {
        String result = MirrorNodeEvmProperties.getEvmWithPrecompiles(evmVersion, precompiles);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> blockNumberToEvmVersionProvider() {
        return Stream.of(
                Arguments.of(1L, EVM_VERSION_30),
                Arguments.of(800L, EVM_VERSION_30),
                Arguments.of(999L, EVM_VERSION_30),
                Arguments.of(1000L, EVM_VERSION_30),
                Arguments.of(1999L, EVM_VERSION_30),
                Arguments.of(2000L, EVM_VERSION_34),
                Arguments.of(2999L, EVM_VERSION_34),
                Arguments.of(3000L, EVM_VERSION_38),
                Arguments.of(5000L, EVM_VERSION_38));
    }

    private static Stream<Arguments> blockNumberAndPrecompilesProvider() {
        return Stream.of(
                Arguments.of(1L, EVM_VERSION_30, Collections.EMPTY_LIST),
                Arguments.of(500L, EVM_VERSION_30, Collections.EMPTY_LIST),
                Arguments.of(800L, EVM_VERSION_30, List.of(HTS)),
                Arguments.of(1099L, EVM_VERSION_30, List.of(HTS)),
                Arguments.of(1100L, EVM_VERSION_30, List.of(HTS)),
                Arguments.of(2000L, EVM_VERSION_34, List.of(HTS)),
                Arguments.of(3000L, EVM_VERSION_38, Arrays.asList(HTS, ERC)),
                Arguments.of(3100L, EVM_VERSION_38, Arrays.asList(HTS, ERC, EXCHANGE_RATE)),
                Arguments.of(4000L, EVM_VERSION_38, Arrays.asList(HTS, ERC, EXCHANGE_RATE)),
                Arguments.of(4100L, EVM_VERSION_38, Arrays.asList(HTS, ERC, EXCHANGE_RATE, PRNG)),
                Arguments.of(5000L, EVM_VERSION_38, Arrays.asList(HTS, ERC, EXCHANGE_RATE, PRNG)));
    }

    private static Stream<Arguments> evmWithPrecompilesProvider() {
        return Stream.of(
                Arguments.of(EVM_VERSION_30, Set.of(HTS), EVM_VERSION_30 + "-" + HTS),
                Arguments.of(EVM_VERSION_30, Set.of(ERC, HTS), EVM_VERSION_30 + "-" + HTS + "," + ERC),
                Arguments.of(
                        EVM_VERSION_30,
                        Set.of(HTS, ERC, EXCHANGE_RATE),
                        EVM_VERSION_30 + "-" + HTS + "," + ERC + "," + EXCHANGE_RATE),
                Arguments.of(EVM_VERSION_30, Set.of(HTS, ERC, EXCHANGE_RATE, PRNG), EVM_VERSION_30),
                Arguments.of(EVM_VERSION_34, Set.of(HTS), EVM_VERSION_34),
                Arguments.of(EVM_VERSION_34, Set.of(HTS, ERC), EVM_VERSION_34),
                Arguments.of(EVM_VERSION_38, Set.of(HTS, ERC, EXCHANGE_RATE), EVM_VERSION_38));
    }
}
