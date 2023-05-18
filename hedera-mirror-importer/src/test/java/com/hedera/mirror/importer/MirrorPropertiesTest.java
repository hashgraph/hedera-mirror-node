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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;

class MirrorPropertiesTest {

    @ParameterizedTest(name = "Network name is enum instance: {0}")
    @EnumSource(value = HederaNetwork.class)
    void verifyIsness(HederaNetwork hederaNetwork) {
        assertTrue(hederaNetwork.is(hederaNetwork.name()));
        assertTrue(hederaNetwork.is(hederaNetwork.name().toUpperCase()));
    }

    @ParameterizedTest(name = "OTHER network is names: {0}")
    @ValueSource(strings = {"Other", "integration", "My-Network-Name"})
    void verifyOtherIsness(String networkName) {
        assertTrue(HederaNetwork.OTHER.is(networkName));
    }

    @ParameterizedTest(name = "OTHER network is not names: {0}")
    @EnumSource(value = HederaNetwork.class, mode = Mode.EXCLUDE, names = "OTHER")
    void verifyOtherIsNotness(HederaNetwork network) {
        assertFalse(HederaNetwork.OTHER.is(network.name()));
    }
}
