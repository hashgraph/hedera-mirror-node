package com.hedera.datagenerator.common;

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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UtilityTest {

    @Test
    void getMemo() {
        assertThat(Utility.getMemo("foo")).isNotBlank().matches("^\\d+ foo$");
    }

    @Test
    void getMessage() {
        assertThat(Utility.generateMessage(50))
                .hasSize(50)
                .asString(StandardCharsets.US_ASCII)
                .matches("^\\d+ \\w+$");
    }

    @Test
    void getMessageOnlyTimestamp() {
        assertThat(Utility.generateMessage(0))
                .isNotEmpty()
                .asString(StandardCharsets.US_ASCII)
                .matches("^\\d+ $");
    }

    @Test
    void getTimestamp() {
        byte[] message = Utility.generateMessage(50);
        Instant now = Instant.now();
        assertThat(Utility.getTimestamp(message)).isNotNull().isBetween(now.minusSeconds(1L), now.plusSeconds(1L));
    }

    @DisplayName("Convert bytes to Instant")
    @ParameterizedTest(name = "with {0}")
    @ValueSource(strings = {"null", "", "1", "1 foo", " ", "foo bar"})
    void getTimestampInvalid(String input) {
        byte[] bytes = input != null ? input.getBytes(StandardCharsets.US_ASCII) : null;
        assertThat(Utility.getTimestamp(bytes)).isNull();
    }

    @Test
    void getTimestampNull() {
        assertThat(Utility.getTimestamp(null)).isNull();
    }
}
