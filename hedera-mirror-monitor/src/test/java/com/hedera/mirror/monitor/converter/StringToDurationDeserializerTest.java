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
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringToDurationDeserializerTest {

    private final StringToDurationDeserializer deserializer = new StringToDurationDeserializer();

    @Mock
    private JsonParser jsonParser;

    @DisplayName("Convert Duration to String")
    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            ",",
            "0s, 0",
            "1s, 1",
            "1m, 60",
            "1m1s, 61",
            "1h, 3600",
            "1h1m, 3660",
            "1h1m1s, 3661",
            "1d, 86400",
            "1d1h, 90000",
            "1d1h1m, 90060",
            "1d1h1m1s, 90061"
    })
    void deserialize(String input, Long expected) throws Exception {
        when(jsonParser.getValueAsString()).thenReturn(input);
        Duration duration = expected != null ? Duration.ofSeconds(expected) : null;
        assertThat(deserializer.deserialize(jsonParser, null)).isEqualTo(duration);
    }
}
