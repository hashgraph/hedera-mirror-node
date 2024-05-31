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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionHashParameterTest {

    private static Stream<Arguments> provideEthHashes() {
        return Stream.of(
                Arguments.of("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("0xGHIJKL", false),
                Arguments.of(null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideEthHashes")
    void testParseTransactionHash(String hash, boolean isValidHash) {
        if (!isValidHash) {
            final var parameter = assertDoesNotThrow(() -> TransactionHashParameter.valueOf(hash));
            assertNull(parameter);
            return;
        }

        final var parameter = assertDoesNotThrow(() -> TransactionHashParameter.valueOf(hash));
        assertNotNull(parameter);
        assertInstanceOf(TransactionHashParameter.class, parameter);
        assertEquals(Bytes.fromHexString(hash), parameter.hash());
    }
}
