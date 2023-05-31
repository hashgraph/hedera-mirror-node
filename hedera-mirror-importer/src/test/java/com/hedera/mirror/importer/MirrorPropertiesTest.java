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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MirrorPropertiesTest {

    @ParameterizedTest(name = "Network {2} is canonical network {0}, with prefix {1}")
    @CsvSource({
        "testnet, , testnet",
        "testnet, , testnet-",
        "testnet, 2023-01, teSTnet-2023-01",
        "testnet, someprefix, testnet-someprefix",
        "mainnet, , mainnet",
        "mainnet, 2023-01, mainnet-2023-01",
        "mainnet, someprefix, maiNNet-someprefix",
        "previewnet, , previewnet",
        "previewnet, 2025-04, Previewnet-2025-04",
        "previewnet, abcdef, previewnet-abcdef",
        "demo, , deMo",
        "demo, 2023-01, demo-2023-01",
        "demo, someprefix, demo-someprefix",
        "other, , other",
        "other, 2050-02, other-2050-02",
        "other, world, othER-world"
    })
    void verifyCanonicalNetworkWithPrefix(String expectedHederaNetwork, String expectedPrefix, String networkName) {

        var properties = new MirrorProperties();
        properties.setNetwork(networkName);
        assertThat(properties.getNetwork()).isEqualTo(expectedHederaNetwork);
        assertThat(properties.getNetworkPrefix()).isEqualTo(expectedPrefix);
    }

    @ParameterizedTest(name = "Network {2} is non-canonical network {0}, with prefix {1}")
    @CsvSource({
        "integration, , integration",
        "integration, 2023-01, integration-2023-01",
        "dev, , dev",
        "dev, 2025-02, dev-2025-02"
    })
    void verifyNonCanonicalNetworkWithPrefix(String expectedNetwork, String expectedPrefix, String networkName) {

        var properties = new MirrorProperties();
        properties.setNetwork(networkName);
        assertThat(properties.getNetwork()).isEqualTo(expectedNetwork);
        assertThat(properties.getNetworkPrefix()).isEqualTo(expectedPrefix);
    }

    @Test
    void verifySetNetworkPropertyValidation() {
        var properties = new MirrorProperties();
        assertThat(properties.getNetwork()).isEqualTo(HederaNetwork.DEMO); // Default
        assertThat(properties.getNetworkPrefix()).isNull();

        assertThrows(NullPointerException.class, () -> HederaNetwork.getBucketName(null));
        assertThrows(NullPointerException.class, () -> HederaNetwork.isAllowAnonymousAccess(null));
    }
}
