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

package com.hedera.mirror.web3.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EvmPropertiesInitializerTest {

    @Mock
    MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private EvmPropertiesInitializer evmPropertiesInitializer;

    @BeforeEach
    void setup() {
        evmPropertiesInitializer = new EvmPropertiesInitializer(mirrorNodeEvmProperties);
    }

    @Test
    void testCreateEvmVersionToPrecompilesMapHappyPath() {
        // given
        Map<String, Long> systemPrecompiles = Map.of(
                "exchangeRate", 1200L, // Released at block 1200
                "PRNG", 1400L // Released at block 1400
                );
        Map<String, Long> evmVersions = Map.of(
                "0.30.0", 1000L, // EVM version released at block 1000
                "0.34.0", 2000L, // EVM version released at block 2000
                "0.38.0", 3000L // EVM version released at block 3000
                );

        Map<String, Set<String>> resultMap =
                evmPropertiesInitializer.createEvmVersionToPrecompilesMap(systemPrecompiles, evmVersions);

        assertNotNull(resultMap);
        assertEquals(3, resultMap.size());
        assertTrue(resultMap.get("0.30.0").containsAll(List.of("exchangeRate", "PRNG")));
        assertTrue(resultMap.get("0.34.0").containsAll(List.of("exchangeRate", "PRNG")));
        assertTrue(resultMap.get("0.38.0").containsAll(List.of("exchangeRate", "PRNG")));
    }

    @Test
    void testCreateEvmVersionToPrecompilesMapNoEvmVersions() {
        Map<String, Set<String>> resultMap = evmPropertiesInitializer.createEvmVersionToPrecompilesMap(null, null);

        assertEquals(0, resultMap.size());
    }
}
