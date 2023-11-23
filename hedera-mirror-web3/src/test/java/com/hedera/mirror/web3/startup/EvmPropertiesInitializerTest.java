package com.hedera.mirror.web3.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EvmPropertiesInitializerTest {

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
                "HTS", 800L,    // Released before the lowest EVM block number
                "ERC", 2100L,   // Released at block 2100
                "exchangeRate", 3100L, // Released at block 3100
                "PRNG", 4100L   // Released after all EVM blocks
        );
        Map<String, Long> evmVersions = Map.of(
                "0.30.0", 1000L, // EVM version released at block 1000
                "0.34.0", 2000L, // EVM version released at block 2000
                "0.38.0", 3000L  // EVM version released at block 3000
        );

        Map<String, List<String>> resultMap = evmPropertiesInitializer.createEvmVersionToPrecompilesMap(systemPrecompiles, evmVersions);

        assertNotNull(resultMap);
        assertEquals(3, resultMap.size());
        assertTrue(resultMap.get("0.30.0").contains("HTS"));
        assertTrue(resultMap.get("0.34.0").containsAll(List.of("HTS", "ERC")));
        assertTrue(resultMap.get("0.38.0").containsAll(List.of("HTS", "ERC", "exchangeRate", "PRNG")));
    }

    @Test
    void testCreateEvmVersionToPrecompilesMapNoEvmVersions() {
        Map<String, List<String>> resultMap =
                evmPropertiesInitializer.createEvmVersionToPrecompilesMap(null, null);

        assertEquals(0, resultMap.size());
    }
}
