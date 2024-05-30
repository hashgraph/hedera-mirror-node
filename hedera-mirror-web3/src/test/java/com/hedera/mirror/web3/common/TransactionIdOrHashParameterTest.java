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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionIdOrHashParameterTest {

    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)-(\\d{1,19})-(\\d{1,9})$");

    private static Stream<Arguments> provideEthHashes() {
        return Stream.of(
                Arguments.of("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("0xGHIJKL", false),
                Arguments.of(null, false)
        );
    }

    private static Stream<Arguments> provideTransactionIds() {
        return Stream.of(
                Arguments.of("0.0.3-1234567890-123", true),
                Arguments.of("0.0.3-1234567890-1234567890", false),
                Arguments.of("0.0.3-12345678901234567890-123", false),
                Arguments.of(null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideEthHashes")
    void testParseTransactionHash(String hash, boolean isValidHash) {
        if (!isValidHash) {
            assertThrows(InvalidParametersException.class, () -> TransactionIdOrHashParameter.valueOf(hash));
            return;
        }

        final var parameter = assertDoesNotThrow(() -> TransactionIdOrHashParameter.valueOf(hash));
        assertNotNull(parameter);
        assertInstanceOf(TransactionHashParameter.class, parameter);
        assertEquals(Bytes.fromHexString(hash), ((TransactionHashParameter) parameter).hash());
    }

    @ParameterizedTest
    @MethodSource("provideTransactionIds")
    void testParseTransactionId(String transactionId, boolean isValidTransactionId) {
        if (!isValidTransactionId) {
            assertThrows(InvalidParametersException.class, () -> TransactionIdOrHashParameter.valueOf(transactionId));
            return;
        }

        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(transactionId);
        assertTrue(matcher.matches());
        assertEquals(5, matcher.groupCount());

        final long shard = Integer.parseInt(matcher.group(1));
        final long realm = Integer.parseInt(matcher.group(2));
        final long num = Integer.parseInt(matcher.group(3));
        final long seconds = Long.parseLong(matcher.group(4));
        final int nanos = Integer.parseInt(matcher.group(5));

        final var parameter = assertDoesNotThrow(() -> TransactionIdOrHashParameter.valueOf(transactionId));
        assertNotNull(parameter);
        assertInstanceOf(TransactionIdParameter.class, parameter);

        final var transactionIdParameter = ((TransactionIdParameter) parameter);
        assertEquals(shard, transactionIdParameter.payerAccountId().getShard());
        assertEquals(realm, transactionIdParameter.payerAccountId().getRealm());
        assertEquals(num, transactionIdParameter.payerAccountId().getNum());
        assertEquals(seconds, transactionIdParameter.validStart().getEpochSecond());
        assertEquals(nanos, transactionIdParameter.validStart().getNano());
    }
}
