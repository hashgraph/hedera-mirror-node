package com.hedera.mirror.monitor.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringToInstantDeserializerTest {

    private final StringToInstantDeserializer stringToInstantDeserializer = new StringToInstantDeserializer();

    @Mock
    private JsonParser jsonParser;

    @Test
    void deserialize() throws Exception {
        Instant now = Instant.now();
        when(jsonParser.getValueAsString()).thenReturn(now.getEpochSecond() + "." + now.getNano());
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, null);
        assertThat(instant).isNotNull().isEqualTo(now);
    }

    @Test
    void deserializeNull() throws Exception {
        when(jsonParser.getValueAsString()).thenReturn(null);
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, null);
        assertThat(instant).isNull();
    }

    @DisplayName("Deserialize String to Instant")
    @ParameterizedTest(name = "with {0}")
    @ValueSource(strings = {"", "foo.bar", "1"})
    void deserializeInvalid(String input) throws Exception {
        when(jsonParser.getValueAsString()).thenReturn(input);
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, null);
        assertThat(instant).isNull();
    }
}
