package com.hedera.mirror.web3.startup;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Map<String, List<String>> evmPrecompilesMap = createEvmVersionToPrecompilesMap(systemPrecompiles, evmVersions);
        mirrorNodeEvmProperties.setEvmPrecompiles(evmPrecompilesMap);
    }


    /**
     * Creates a mapping between EVM block numbers and lists of system precompiles release blocks.
     * This method determines the compatibility of system precompiles with each EVM based on their release block numbers.
     * Each system precompile is added to the list of precompiles for the EVM version with which it is compatible.
     *
     * @param systemPrecompiles A map where keys are names of system precompiles and values are the block numbers at which they were released.
     * @param evmVersions A map where keys are EVM block numbers and values are the block numbers at which these versions were released.
     * @return A map with keys as EVM block numbers and values as lists of system precompiles available up to those blocks.
     * Example usage based on the provided data:
     * {@code
     * Map<String, Long> systemPrecompiles = Map.of(
     *     "HTS", 800L,    // Released before the lowest EVM block number, so it goes to the lowest EVM block
     *     "ERC", 2100L,   // Released at block 2100, goes to EVM released at block 2000 ("0.34.0")
     *     "exchangeRate", 3100L, // Released at block 3100, goes to EVM released at block 3000 ("0.38.0")
     *     "PRNG", 4100L   // Released after all EVM blocks, so it goes to the highest EVM block ("0.38.0")
     * );
     * Map<String, Long> evmVersions = Map.of(
     *     "0.30.0", 1000L, // EVM version released at block 1000
     *     "0.34.0", 2000L, // EVM version released at block 2000
     *     "0.38.0", 3000L  // EVM version released at block 3000
     * );
     * Map<String, List<String>> evmPrecompilesMap = createEvmVersionToPrecompilesMap(systemPrecompiles, evmVersions);
     * }
     *
     * The resulting evmPrecompilesMap will be:
     * {
     *     "0.30.0" -> ["HTS"],
     *     "0.34.0" -> ["HTS","ERC"],
     *     "0.38.0" -> ["HTS","ERC","exchangeRate", "PRNG"]
     * }
     */
    public Map<String, List<String>> createEvmVersionToPrecompilesMap(Map<String, Long> systemPrecompiles, Map<String, Long> evmVersions) {
        if (CollectionUtils.isEmpty(evmVersions)) {
            return new HashMap<>();
        }

        TreeMap<Long, String> sortedEvmVersions = sortEvmVersions(evmVersions);
        Map<String, List<String>> evmPrecompilesMap = initializeEvmPrecompilesMap(sortedEvmVersions);

        String highestEvmVersion = sortedEvmVersions.lastEntry().getValue();

        // Map precompiles to evm
        for (Map.Entry<String, Long> systemPrecompileEntry : systemPrecompiles.entrySet()) {
            mapPrecompileToEvmVersion(systemPrecompileEntry, sortedEvmVersions, evmPrecompilesMap, highestEvmVersion);
        }

        return evmPrecompilesMap;
    }


    private TreeMap<Long, String> sortEvmVersions(Map<String, Long> evmVersions) {
        TreeMap<Long, String> sortedEvmVersions = new TreeMap<>();
        evmVersions.forEach((version, block) -> sortedEvmVersions.put(block, version));
        return sortedEvmVersions;
    }

    private Map<String, List<String>> initializeEvmPrecompilesMap(TreeMap<Long, String> sortedEvmVersions) {
        Map<String, List<String>> evmPrecompilesMap = new TreeMap<>();
        sortedEvmVersions.values().forEach(version -> evmPrecompilesMap.put(version, new ArrayList<>()));
        return evmPrecompilesMap;
    }


    private void mapPrecompileToEvmVersion(Map.Entry<String, Long> systemPrecompileEntry, TreeMap<Long, String> sortedEvmVersions, Map<String, List<String>> evmPrecompilesMap, String highestEvmVersion) {
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
