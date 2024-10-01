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

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @Test
    void parseEd25519PublicKey() {
        final var domainBuilder = new DomainBuilder();
        final var rawKey = domainBuilder.key(KeyCase.ED25519);
        final var parsedKey = Utils.parseKey(rawKey);

        assertThat(parsedKey).isNotNull();
        assertThat(parsedKey.hasEd25519()).isTrue();
    }

    @Test
    void parseEcdsaSecp256k1PublicKey() {
        final var domainBuilder = new DomainBuilder();
        final var rawKey = domainBuilder.key(KeyCase.ECDSA_SECP256K1);
        final var parsedKey = Utils.parseKey(rawKey);

        assertThat(parsedKey).isNotNull();
        assertThat(parsedKey.hasEcdsaSecp256k1()).isTrue();
    }

    @Test
    void parseInvalidKey() {
        byte[] invalidKeyBytes = new byte[] {};

        Key parsedKey = Utils.parseKey(invalidKeyBytes);

        assertNull(parsedKey);
    }

    @Test
    void parseThrowsExceptionAndReturnsNull() {
        byte[] invalidKeyBytes = new byte[] {0x01, 0x02, 0x03, 0x04};

        Key parsedKey = Utils.parseKey(invalidKeyBytes);

        assertNull(parsedKey);
    }
}
