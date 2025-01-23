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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ByteArrayFromStringConverterTest {

    @ParameterizedTest(name = "Convert \"{0}\" to EntityId")
    @MethodSource("provideTestCases")
    void testConverter(String source, byte[] expected) {
        var converter = new ByteArrayFromStringConverter();
        assertThat(converter.convert(source)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Convert \"{0}\" to EntityId")
    @NullAndEmptySource
    void testInvalidSource(String source) {
        var converter = new ByteArrayFromStringConverter();
        assertThat(converter.convert(source)).isNull();
    }

    private static Stream<Arguments> provideTestCases() {
        return Stream.of(
                Arguments.of("0", new byte[] {0x30}),
                Arguments.of("01", new byte[] {0x30, 0x31}),
                Arguments.of("hashgraph", "hashgraph".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("", null),
                Arguments.of(" ", new byte[] {0x20}),
                Arguments.of("\t\n", new byte[] {0x09, 0x0a}));
    }
}
