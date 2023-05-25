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

    @ParameterizedTest(name = "Network with name/prefix {2} is enum instance {0}")
    @CsvSource({
        "TESTNET, , testnet",
        "TESTNET, , testnet-",
        "TESTNET, 2023-01, teSTnet-2023-01",
        "TESTNET, someprefix, testnet-someprefix",
        "MAINNET, , mainnet",
        "MAINNET, 2023-01, mainnet-2023-01",
        "MAINNET, someprefix, maiNNet-someprefix",
        "PREVIEWNET, , previewnet",
        "PREVIEWNET, 2025-04, Previewnet-2025-04",
        "PREVIEWNET, abcdef, previewnet-abcdef",
        "DEMO, , deMo",
        "DEMO, 2023-01, demo-2023-01",
        "DEMO, someprefix, demo-someprefix",
        "OTHER, , other",
        "OTHER, 2050-02, other-2050-02",
        "OTHER, world, othER-world"
    })
    void verifyNetworkWithPrefix(HederaNetwork expectedHederaNetwork, String expectedPrefix, String networkName) {
        assertThat(expectedHederaNetwork.is(networkName)).isTrue();

        var properties = new MirrorProperties();
        properties.setNetwork(networkName);
        var prefixOpt = properties.getNetworkPrefix();
        if (expectedPrefix == null) {
            assertThat(prefixOpt).isEmpty();
        } else {
            assertThat(prefixOpt).hasValue(expectedPrefix);
        }
    }

    @Test
    void verifySetNetworkPropetyValidation() {
        var properties = new MirrorProperties();
        assertThat(properties.getNetwork()).isEqualTo(HederaNetwork.DEMO.name().toLowerCase()); // Default

        properties.setNetwork(HederaNetwork.TESTNET.name());
        assertThat(properties.getNetwork())
                .isEqualTo(HederaNetwork.TESTNET.name().toLowerCase());

        assertThrows(IllegalArgumentException.class, () -> properties.setNetwork("bogusnetwork-and-prefix"));

        assertThrows(NullPointerException.class, () -> properties.setNetwork(null));
    }
}
