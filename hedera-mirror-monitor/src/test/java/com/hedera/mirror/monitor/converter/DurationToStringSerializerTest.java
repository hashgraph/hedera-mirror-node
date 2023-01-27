package com.hedera.mirror.monitor.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DurationToStringSerializerTest {

    private final DurationToStringSerializer serializer = new DurationToStringSerializer();

    @Mock
    private JsonGenerator jsonGenerator;

    @DisplayName("Serialize Duration to String")
    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("testCases")
    void serialize(Long input, String text) throws Exception {
        Duration duration = input != null ? Duration.ofSeconds(input) : null;
        serializer.serialize(duration, jsonGenerator, null);
        verify(jsonGenerator).writeString(text);
    }

    @DisplayName("Convert Duration to String")
    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("testCases")
    void convert(Long input, String text) {
        Duration duration = input != null ? Duration.ofSeconds(input) : null;
        assertThat(DurationToStringSerializer.convert(duration)).isEqualTo(text);
    }

    @SuppressWarnings("unused")
    private static String[][] testCases() {
        String[][] testCases = {
                {"0", "0s"},
                {"1", "1s"},
                {"60", "1m"},
                {"61", "1m1s"},
                {"3600", "1h"},
                {"3660", "1h1m"},
                {"3661", "1h1m1s"},
                {"86400", "1d"},
                {"90000", "1d1h"},
                {"90060", "1d1h1m"},
                {"90061", "1d1h1m1s"}
        };
        return testCases;
    }
}
