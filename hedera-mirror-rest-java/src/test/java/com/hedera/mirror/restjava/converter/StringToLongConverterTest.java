/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StringToLongConverterTest {

    @ParameterizedTest(name = "Convert \"{0}\" to Long")
    @CsvSource(delimiter = ',', textBlock = """
            1, 1
            0, 0
            0.0.2, 2
            """)
    void testConverter(String source, Long expected) {
        var converter = new StringToLongConverter();
        Long actual = converter.convert(source);
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Convert \"{0}\" to Long")
    @NullAndEmptySource
    void testInvalidSource(String source) {
        var converter = new StringToLongConverter();
        assertThat(converter.convert(source)).isNull();
    }

    @ParameterizedTest(name = "Fail to convert \"{0}\" to Long")
    @ValueSource(strings = {"bad", "1.557", "5444.0"})
    void testConverterFailures(String source) {
        var converter = new StringToLongConverter();
        assertThrows(NumberFormatException.class, () -> converter.convert(source));
    }
}
