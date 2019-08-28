package com.hedera.mirror.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TimestampConverterTest {
    private TimestampConverter getCut() {
        return new TimestampConverter();
    }

    static Stream<Arguments> toInstantByNumbersPositiveSource() throws Throwable {
        return Stream.of(
                Arguments.of(1546300799, 999_999_999, new int[]{2018, 12, 31, 23, 59, 59, 999_999_999})
                ,Arguments.of(0, 0, new int[]{1970, 1, 1, 0, 0, 0, 0})
        );
    }

    @ParameterizedTest(name="toInstantByNumbersPositive({0},{1},{2})")
    @MethodSource("toInstantByNumbersPositiveSource")
    public void toInstantByNumbersPositive(final long expectedSeconds, final long expectedNanos, final int[] args) {
        final var cut = getCut();
        final var inst = cut.toInstant(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        assertAll(() -> assertEquals(expectedSeconds, inst.getEpochSecond()),
                () -> assertEquals(expectedNanos, inst.getNano())
        );
    }

    static Stream<Arguments> toInstantByRegexPositiveSource() throws Throwable {
        return Stream.of(
                Arguments.of(1580796490, 0, "^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-(?<second>\\d+)", "2020-02-04-06-08-10")
                ,Arguments.of(1580796490, 120_000_000, "^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-(?<second>\\d+)-(?<subsecond>\\d+)", "2020-02-04-06-08-10-12")
        );
    }

    @ParameterizedTest(name="toInstantByRegexPositive({0},{1},{2},{3})")
    @MethodSource("toInstantByRegexPositiveSource")
    public void toInstantByRegexPositive(final long expectedSeconds, final long expectedNanos, final String pattern,
                                         final String value) {
        final var cut = getCut();
        final var p = Pattern.compile(pattern);
        final var m = p.matcher(value);
        assertTrue(m.find());
        final var inst = cut.toInstant(m);
        assertAll(() -> assertEquals(expectedSeconds, inst.getEpochSecond()),
                () -> assertEquals(expectedNanos, inst.getNano())
        );
    }

    static Stream<Arguments> toInstantByNumbersNegativeSource() throws Throwable {
        return Stream.of(
                Arguments.of(new int[]{1969, 0, 0, 14, 61, 61, 1_000_000_000})
                ,Arguments.of(new int[]{2000, 0, 1, 0, 0, 0, 0})
                ,Arguments.of(new int[]{2000, 13, 1, 0, 0, 0, 0})
                ,Arguments.of(new int[]{2000, 1, 0, 0, 0, 0, 0})
                ,Arguments.of(new int[]{2000, 1, 32, 0, 0, 0, 0})
                ,Arguments.of(new int[]{2000, 1, 1, 0, 0, 0, -1})
                ,Arguments.of(new int[]{2000, 1, 1, 0, 0, 0, 1_000_000_000})
        );
    }

    @ParameterizedTest(name="toInstantByNumbersNegative({0})")
    @MethodSource("toInstantByNumbersNegativeSource")
    public void toInstantByNumbersNegative(final int[] args) {
        final var cut = getCut();
        assertThrows(IllegalArgumentException.class,
                () -> cut.toInstant(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
        );
    }

    static Stream<Arguments> toInstantByRegexNegativeSource() throws Throwable {
        return Stream.of(
                Arguments.of("^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-(?<second>\\d+)", "2020-0-4-6-8-10")
                ,Arguments.of("^(?<year>\\d+)-(?<month>\\d+)-(?<day>\\d+)-(?<hour>\\d+)-(?<minute>\\d+)-(?<second>\\d+)-(?<subSecond>\\d+)", "2020-02-04-06-08-10-12")
        );
    }

    @ParameterizedTest(name="toInstantByRegexNegative({0},{1})")
    @MethodSource("toInstantByRegexNegativeSource")
    public void toInstantByRegexNegative(final String pattern, final String value) {
        final var cut = getCut();
        final var p = Pattern.compile(pattern);
        final var m = p.matcher(value);
        assertTrue(m.find());
        assertThrows(IllegalArgumentException.class,
                () -> cut.toInstant(m)
        );
    }
}