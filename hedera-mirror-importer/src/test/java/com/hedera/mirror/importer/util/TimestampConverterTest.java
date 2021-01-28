package com.hedera.mirror.importer.util;

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

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TimestampConverterTest {
    static Stream<Arguments> toInstantByNumbersPositiveSource() throws Throwable {
        return Stream.of(
                Arguments.of(1546300799, 999_999_999, new int[] {2018, 12, 31, 23, 59, 59, 999_999_999})
                , Arguments.of(0, 0, new int[] {1970, 1, 1, 0, 0, 0, 0})
        );
    }

    static Stream<Arguments> toInstantByRegexPositiveSource() throws Throwable {
        return Stream.of(
                Arguments
                        .of(1580796490, 0, "^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-" +
                                "(?<second>\\d+)", "2020-02-04-06-08-10")
                , Arguments
                        .of(1580796490, 120_000_000, "^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-" +
                                "(?<minute>\\d+)-(?<second>\\d+)-(?<subsecond>\\d+)", "2020-02-04-06-08-10-12")
        );
    }

    static Stream<Arguments> toInstantByNumbersNegativeSource() throws Throwable {
        return Stream.of(
                Arguments.of(new int[] {1969, 0, 0, 14, 61, 61, 1_000_000_000})
                , Arguments.of(new int[] {2000, 0, 1, 0, 0, 0, 0})
                , Arguments.of(new int[] {2000, 13, 1, 0, 0, 0, 0})
                , Arguments.of(new int[] {2000, 1, 0, 0, 0, 0, 0})
                , Arguments.of(new int[] {2000, 1, 32, 0, 0, 0, 0})
                , Arguments.of(new int[] {2000, 1, 1, 0, 0, 0, -1})
                , Arguments.of(new int[] {2000, 1, 1, 0, 0, 0, 1_000_000_000})
        );
    }

    static Stream<Arguments> toInstantByRegexNegativeSource() throws Throwable {
        return Stream.of(
                Arguments
                        .of("^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-(?<second>\\d+)"
                                , "2020-0-4-6-8-10")
                , Arguments
                        .of("^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-(?<second>\\d+)" +
                                "-(?<subSecond>\\d+)", "2020-02-04-06-08-10-12")
        );
    }

    private TimestampConverter getCut() {
        return new TimestampConverter();
    }

    @ParameterizedTest(name = "toInstantByNumbersPositive({0},{1},{2})")
    @MethodSource("toInstantByNumbersPositiveSource")
    public void toInstantByNumbersPositive(long expectedSeconds, long expectedNanos, int[] args) {
        var cut = getCut();
        var inst = cut.toInstant(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        assertAll(() -> assertEquals(expectedSeconds, inst.getEpochSecond()),
                () -> assertEquals(expectedNanos, inst.getNano())
        );
    }

    @ParameterizedTest(name = "toInstantByRegexPositive({0},{1},{2},{3})")
    @MethodSource("toInstantByRegexPositiveSource")
    public void toInstantByRegexPositive(long expectedSeconds, long expectedNanos, String pattern,
                                         String value) {
        var cut = getCut();
        var p = Pattern.compile(pattern);
        var m = p.matcher(value);
        assertTrue(m.find());
        var inst = cut.toInstant(m);
        assertAll(() -> assertEquals(expectedSeconds, inst.getEpochSecond()),
                () -> assertEquals(expectedNanos, inst.getNano())
        );
    }

    @ParameterizedTest(name = "toInstantByNumbersNegative({0})")
    @MethodSource("toInstantByNumbersNegativeSource")
    public void toInstantByNumbersNegative(int[] args) {
        var cut = getCut();
        assertThrows(IllegalArgumentException.class,
                () -> cut.toInstant(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
        );
    }

    @ParameterizedTest(name = "toInstantByRegexNegative({0},{1})")
    @MethodSource("toInstantByRegexNegativeSource")
    public void toInstantByRegexNegative(String pattern, String value) {
        var cut = getCut();
        var p = Pattern.compile(pattern);
        var m = p.matcher(value);
        assertTrue(m.find());
        assertThrows(IllegalArgumentException.class,
                () -> cut.toInstant(m)
        );
    }
}
