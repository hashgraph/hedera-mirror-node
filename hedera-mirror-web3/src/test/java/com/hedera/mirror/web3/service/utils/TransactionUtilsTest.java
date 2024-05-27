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

package com.hedera.mirror.web3.service.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.web3.evm.utils.TransactionUtils;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionUtilsTest {

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
    void testIsValidEthHash(String hash, boolean expected) {
        assertEquals(expected, TransactionUtils.isValidEthHash(hash));
    }

    @ParameterizedTest
    @MethodSource("provideTransactionIds")
    void testIsValidTransactionId(String transactionId, boolean expected) {
        assertEquals(expected, TransactionUtils.isValidTransactionId(transactionId));
    }

    @ParameterizedTest
    @MethodSource("provideTransactionIds")
    void testParseTransactionId(String transactionId, boolean isValidTransactionId) {
        if (!isValidTransactionId) {
            final var expectedExceptionType = IllegalArgumentException.class;
            final var expectedMessage = transactionId == null ?
                    "Transaction ID cannot be null" :
                    "Invalid Transaction ID. Please use \"shard.realm.num-sss-nnn\" format where sss are seconds and nnn are nanoseconds";

            assertThatThrownBy(() -> TransactionUtils.parseTransactionId(transactionId))
                    .isInstanceOf(expectedExceptionType)
                    .hasMessageContaining(expectedMessage);
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

        TransactionID parsedTransactionId = TransactionUtils.parseTransactionId(transactionId);

        assertEquals(shard, parsedTransactionId.getAccountID().getShardNum());
        assertEquals(realm, parsedTransactionId.getAccountID().getRealmNum());
        assertEquals(num, parsedTransactionId.getAccountID().getAccountNum());
        assertEquals(seconds, parsedTransactionId.getTransactionValidStart().getSeconds());
        assertEquals(nanos, parsedTransactionId.getTransactionValidStart().getNanos());
    }

    @Test
    void assertUtilityClassWellDefined() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(TransactionUtils.class.getModifiers()));
        assertEquals(1, TransactionUtils.class.getDeclaredConstructors().length);

        final Constructor<?> constructor = ((Class<?>) TransactionUtils.class).getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        final Stream<Method> methods = Arrays.stream(TransactionUtils.class.getDeclaredMethods());
        assertTrue(methods.allMatch(m -> Modifier.isStatic(m.getModifiers())));

        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class);
    }
}
