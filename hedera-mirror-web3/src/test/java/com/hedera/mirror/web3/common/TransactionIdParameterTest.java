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

package com.hedera.mirror.web3.common;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionIdParameterTest {

    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)-(\\d{1,19})-(\\d{1,9})$");

    private static Stream<Arguments> provideTransactionIds() {
        return Stream.of(
                Arguments.of("0.0.3-1234567890-123", true),
                Arguments.of("0.0.3--1234567890-123", false),
                Arguments.of("0.0.3-1234567890--123", false),
                Arguments.of("0.0.3-1234567890-1234567890", false),
                Arguments.of("0.0.3-12345678901234567890-123", false),
                Arguments.of("%d.0.3-1234567890-123".formatted(Long.MAX_VALUE), false),
                Arguments.of("0.%d.3-1234567890-123".formatted(Long.MAX_VALUE), false),
                Arguments.of("0.0.%d-1234567890-123".formatted(Long.MAX_VALUE), false),
                Arguments.of("0.0.3-%d-1234567890".formatted(Long.MAX_VALUE), false),
                Arguments.of("0.0.3-1234567890-%d".formatted(Integer.MAX_VALUE), false),
                Arguments.of(null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideTransactionIds")
    void testParseTransactionId(String transactionId, boolean isValidTransactionId) {
        if (!isValidTransactionId) {
            if (transactionId == null || !TRANSACTION_ID_PATTERN.matcher(transactionId).matches()) {
                final var parameter = assertDoesNotThrow(() -> TransactionIdParameter.valueOf(transactionId));
                assertNull(parameter);
                return;
            }
            assertThrows(InvalidParametersException.class, () -> TransactionIdParameter.valueOf(transactionId));
            return;
        }

        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(transactionId);
        assertTrue(matcher.matches());
        assertEquals(5, matcher.groupCount());

        final var parameter = assertDoesNotThrow(() -> TransactionIdParameter.valueOf(transactionId));
        assertNotNull(parameter);
        assertInstanceOf(TransactionIdParameter.class, parameter);
        assertEquals(parseLong(matcher.group(1)), parameter.payerAccountId().getShard());
        assertEquals(parseLong(matcher.group(2)), parameter.payerAccountId().getRealm());
        assertEquals(parseLong(matcher.group(3)), parameter.payerAccountId().getNum());
        assertEquals(parseLong(matcher.group(4)), parameter.validStart().getEpochSecond());
        assertEquals(parseInt(matcher.group(5)), parameter.validStart().getNano());
    }
}
