/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.converter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Range;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RangeFromStringConverterTest {

    @ParameterizedTest(name = "Convert \"{0}\" to Range")
    @CsvSource(
            delimiterString = "#",
            textBlock =
                    """
            [1,100)# [1..100)
            [1,100]# [1..100]
            [1,)# [1..+∞)
            (,100]# (-∞..100]
            (,)# (-∞..+∞)
            """)
    void testConverter(String source, String expected) {
        var converter = new RangeFromStringConverter();
        Range<Long> range = converter.convert(source);
        assertThat(range).hasToString(expected);
    }

    @ParameterizedTest(name = "Convert \"{0}\" to Range")
    @NullAndEmptySource
    void testInvalidSource(String source) {
        var converter = new RangeFromStringConverter();
        assertThat(converter.convert(source)).isNull();
    }

    @ParameterizedTest(name = "Fail to convert \"{0}\" to Range")
    @ValueSource(strings = {"bad", "[1,100}", "[A,$)", "12,13", ",", "[)"})
    void testConverterFailures(String source) {
        var converter = new RangeFromStringConverter();
        assertThrows(IllegalArgumentException.class, () -> converter.convert(source));
    }
}
