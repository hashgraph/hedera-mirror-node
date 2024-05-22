/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RangeBoundTest {

    @ParameterizedTest
    @CsvSource({
        "0.0.1000, eq, 0.0.1000",
        "5, eq, 5",
        "lt:-100, lt, -100",
        "eq:0.0.1000, eq, 0.0.1000",
        "EQ:0.0.1000, eq, 0.0.1000",
        "eq:5, eq, 5",
        "ne:5, ne, 5",
        "gt:abc, gt, abc",
        "gte:0.0.1000, gte, 0.0.1000",
        "lt:0.0.1000, lt, 0.0.1000",
        "lte:0.0.1000, lte, 0.0.1000",
    })
    void testConversion(String input, String expectedOperator, String expectedValue) {
        var rangeBound = RangeBound.valueOf(input);
        assertThat(rangeBound.operator()).hasToString(expectedOperator);
        assertThat(rangeBound.value()).isEqualTo(expectedValue);
    }

    @Test
    void testInvalidInput() {
        var rangeBound = RangeBound.valueOf("");
        assertThat(rangeBound.operator()).isNull();
        assertThat(rangeBound.value()).isNull();

        rangeBound = RangeBound.valueOf(null);
        assertThat(rangeBound.operator()).isNull();
        assertThat(rangeBound.value()).isNull();

        assertThrows(IllegalArgumentException.class, () -> RangeBound.valueOf("glt:0.0.1000"));
        assertThrows(IllegalArgumentException.class, () -> RangeBound.valueOf("gt:300:400"));
    }
}
