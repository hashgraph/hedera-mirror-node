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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void deserializeEmpty() throws Exception {
        when(jsonParser.getValueAsString()).thenReturn("");
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, null);
        assertThat(instant).isNull();
    }

    @Test
    void deserializeInvalid() throws Exception {
        when(jsonParser.getValueAsString()).thenReturn("foo.bar");
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, null);
        assertThat(instant).isNull();
    }

    @Test
    void deserializeMissing() throws Exception {
        when(jsonParser.getValueAsString()).thenReturn("1");
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, null);
        assertThat(instant).isNull();
    }
}
