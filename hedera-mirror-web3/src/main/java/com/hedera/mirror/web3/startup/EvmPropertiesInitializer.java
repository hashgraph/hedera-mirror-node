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

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class EvmPropertiesInitializer implements ApplicationRunner {

    public EvmPropertiesInitializer(MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Map<String, Long> systemPrecompiles = mirrorNodeEvmProperties.getSystemPrecompiles();
        Map<String, Long> evmVersions = mirrorNodeEvmProperties.getEvmVersions();

        Map<String, Set<String>> evmPrecompilesMap = createEvmVersionToPrecompilesMap(systemPrecompiles, evmVersions);
        mirrorNodeEvmProperties.setEvmPrecompiles(evmPrecompilesMap);
    }

    /**
     * Creates a mapping between EVM block numbers and lists of system precompiles release blocks. This method
     * determines the compatibility of system precompiles with each EVM version based on their release block numbers.
     * Each system precompile is added to the list of precompiles for the EVM version with which it is compatible.
     *
     * <p>Example usage based on the provided data:</p>
     * <pre>
     * {@code
     * Map<String, Long> systemPrecompiles = Map.of(
     *     "exchangeRate", 1200L, // Released at block 1200, goes to EVM released at block 1000 ("0.30.0")
     *     "PRNG", 1800L   // Released at block 1800, goes to EVM released at block 1000 ("0.30.0")
     * );
     * Map<String, Long> evmVersions = Map.of(
     *     "0.30.0", 1000L, // EVM version released at block 1000
     *     "0.34.0", 2000L, // EVM version released at block 2000
     *     "0.38.0", 3000L  // EVM version released at block 3000
     * );
     * Map<String, List<String>> evmPrecompilesMap = createEvmVersionToPrecompilesMap(systemPrecompiles, evmVersions);
     * }
     * </pre>
     *
     * <p>The resulting {@code evmPrecompilesMap} will be:</p>
     * <pre>
     * {@code
     * {
     *     "0.30.0" -> ["exchangeRate", "PRNG"],
     *     "0.34.0" -> ["exchangeRate", "PRNG"],
     *     "0.38.0" -> ["exchangeRate", "PRNG"]
     * }
     * }
     * </pre>
     *
     * @param systemPrecompiles A map where keys are names of system precompiles and values are the block numbers at
     *                          which they were released.
     * @param evmVersions       A map where keys are EVM block numbers and values are the block numbers at which these
     *                          versions were released.
     * @return A map with keys as EVM block numbers and values as lists of system precompiles available up to those
     *         blocks.
     */
    public Map<String, Set<String>> createEvmVersionToPrecompilesMap(
            Map<String, Long> systemPrecompiles, Map<String, Long> evmVersions) {
        if (CollectionUtils.isEmpty(evmVersions)) {
            return new HashMap<>();
        }

        TreeMap<Long, String> sortedEvmVersions = sortEvmVersions(evmVersions);
        Map<String, Set<String>> evmPrecompilesMap = initializeEvmPrecompilesMap(sortedEvmVersions);

        // Map precompiles to evm
        for (Map.Entry<String, Long> systemPrecompileEntry : systemPrecompiles.entrySet()) {
            mapPrecompileToEvmVersion(systemPrecompileEntry, sortedEvmVersions, evmPrecompilesMap);
        }

        return evmPrecompilesMap;
    }

    private TreeMap<Long, String> sortEvmVersions(Map<String, Long> evmVersions) {
        TreeMap<Long, String> sortedEvmVersions = new TreeMap<>();
        evmVersions.forEach((version, block) -> sortedEvmVersions.put(block, version));
        return sortedEvmVersions;
    }

    private Map<String, Set<String>> initializeEvmPrecompilesMap(TreeMap<Long, String> sortedEvmVersions) {
        Map<String, Set<String>> evmPrecompilesMap = new TreeMap<>();
        sortedEvmVersions.values().forEach(version -> evmPrecompilesMap.put(version, new HashSet<>()));
        return evmPrecompilesMap;
    }

    private void mapPrecompileToEvmVersion(
            Map.Entry<String, Long> systemPrecompileEntry,
            TreeMap<Long, String> sortedEvmVersions,
            Map<String, Set<String>> evmPrecompilesMap) {
        Long precompileBlockNumber = systemPrecompileEntry.getValue();
        String precompileName = systemPrecompileEntry.getKey();

        for (Map.Entry<Long, String> evmVersionEntry : sortedEvmVersions.entrySet()) {
            Long evmBlockStart = evmVersionEntry.getKey();
            Long upperBoundForEvmVersion = calculateUpperBoundForEvmVersion(evmBlockStart, sortedEvmVersions);

            if (precompileBlockNumber < upperBoundForEvmVersion) {
                evmPrecompilesMap.get(evmVersionEntry.getValue()).add(precompileName);
            }
        }
    }

    private Long calculateUpperBoundForEvmVersion(Long currentEvmBlock, TreeMap<Long, String> sortedEvmVersions) {
        Map.Entry<Long, String> nextEvmVersion = sortedEvmVersions.higherEntry(currentEvmBlock);
        if (nextEvmVersion != null) {
            return nextEvmVersion.getKey();
        } else {
            // If there's no next version, this is the highest version and there's no upper bound.
            return Long.MAX_VALUE;
        }
    }
}
