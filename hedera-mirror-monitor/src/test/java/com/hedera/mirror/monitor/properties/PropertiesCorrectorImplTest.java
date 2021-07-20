package com.hedera.mirror.monitor.properties;

/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PropertiesCorrectorImplTest {

    private final PropertiesCorrector propertiesCorrector = new PropertiesCorrectorImpl();

    @Test
    void propertiesWithLists() {
        Map<String, String> properties = new HashMap<>();
        properties.put("senderAccountId", "0.0.2");
        properties.put("transferTypes.0", "CRYPTO");
        properties.put("transferTypes.1", "TOKEN");
        properties.put("otherProperty.0", "TEST");
        properties.put("otherProperty.1", "TEST2");

        Map<String, Object> correctedProperties = propertiesCorrector.correctProperties(properties);
        assertThat(correctedProperties.keySet())
                .containsExactlyInAnyOrder("senderAccountId", "transferTypes", "otherProperty");

        assertThat(correctedProperties.get("senderAccountId")).isEqualTo("0.0.2");
        assertThat(correctedProperties.get("transferTypes")).isInstanceOf(List.class);
        assertThat((List) correctedProperties.get("transferTypes")).containsExactlyInAnyOrder("CRYPTO", "TOKEN");
        assertThat(correctedProperties.get("otherProperty")).isInstanceOf(List.class);
        assertThat((List) correctedProperties.get("otherProperty")).containsExactlyInAnyOrder("TEST", "TEST2");
    }
}
