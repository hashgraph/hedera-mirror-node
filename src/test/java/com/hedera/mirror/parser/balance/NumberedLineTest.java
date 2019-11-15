package com.hedera.mirror.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class NumberedLineTest {
    static Stream<Arguments> ctorToStringSource() throws Throwable {
        return Stream.of(
                Arguments.of("1:abcdef", 1, "abcdef")
                , Arguments.of("-10:", -10, "")
        );
    }

    @ParameterizedTest(name = "ctorToString({0},{1},{2})")
    @MethodSource("ctorToStringSource")
    public void ctorToString(final String expectedString, final int lineNumber, final String value) {
        final var cut = new NumberedLine(lineNumber, value);
        assertAll(
                () -> assertEquals(lineNumber, cut.getLineNumber())
                , () -> assertEquals(value, cut.getValue())
                , () -> assertEquals(expectedString, cut.toString())
        );
    }
}
