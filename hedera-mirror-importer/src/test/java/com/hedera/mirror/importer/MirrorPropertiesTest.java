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

package com.hedera.mirror.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class MirrorPropertiesTest {

    private static final String MIXED_CASE_NETWORK_PREFIX = "NetworkPrefiX";
    private static final String LOWER_CASE_NETWORK_PREFIX = MIXED_CASE_NETWORK_PREFIX.toLowerCase();

    private MirrorProperties mirrorProperties;

    @BeforeEach
    void setup() {
        mirrorProperties = new MirrorProperties();
    }

    @ParameterizedTest(name = "Network prefix overrides all network types: {0}")
    @EnumSource(value = HederaNetwork.class)
    void networkPrefixDefined(HederaNetwork network) {
        mirrorProperties.setNetworkPrefix(MIXED_CASE_NETWORK_PREFIX);
        mirrorProperties.setNetwork(network);
        assertEquals(LOWER_CASE_NETWORK_PREFIX, mirrorProperties.getNetworkPrefix());
    }

    @ParameterizedTest(name = "Default to lowercase network name: {0}")
    @EnumSource(value = HederaNetwork.class, mode = Mode.EXCLUDE, names = "OTHER")
    void networkPrefixUndefined(HederaNetwork network) {
        mirrorProperties.setNetwork(network);
        assertEquals(network.name().toLowerCase(), mirrorProperties.getNetworkPrefix());
    }

    @Test
    void networkOtherRequiresPrefix() {
        mirrorProperties.setNetwork(HederaNetwork.OTHER);
        assertThrows(
                InvalidConfigurationException.class,
                () -> mirrorProperties.getNetworkPrefix(),
                "InvalidConfigurationException was expected");
    }
}
