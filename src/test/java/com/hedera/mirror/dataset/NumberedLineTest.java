package com.hedera.mirror.dataset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class NumberedLineTest {
    static Stream<Arguments> ctorToStringSource() throws Throwable {
        return Stream.of(
                Arguments.of("1:abcdef", 1, "abcdef")
                ,Arguments.of("-10:", -10, "")
        );
    }
    @ParameterizedTest(name="ctorToString({0},{1},{2})")
    @MethodSource("ctorToStringSource")
    public void ctorToString(final String expectedString, final int lineNumber, final String value) {
        final var cut = new NumberedLine(lineNumber, value);
        assertAll(
                () -> assertEquals(lineNumber, cut.getLineNumber())
                ,() -> assertEquals(value, cut.getValue())
                ,() -> assertEquals(expectedString, cut.toString())
        );
    }
}