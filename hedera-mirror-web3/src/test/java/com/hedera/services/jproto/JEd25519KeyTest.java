/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.jproto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JEd25519KeyTest {
    @Test
    void emptyJEd25519KeyTest() {
        JEd25519Key key1 = new JEd25519Key(null);
        assertTrue(key1.isEmpty());
        assertFalse(key1.isValid());

        JEd25519Key key2 = new JEd25519Key(new byte[0]);
        assertTrue(key2.isEmpty());
        assertFalse(key2.isValid());
    }

    @Test
    void nonEmptyJEd25519KeyTest() {
        JEd25519Key key = new JEd25519Key(new byte[1]);
        assertFalse(key.isEmpty());
    }

    @Test
    void invalidJEd25519KeyTest() {
        JEd25519Key key = new JEd25519Key(new byte[JEd25519Key.ED25519_BYTE_LENGTH - 1]);
        assertFalse(key.isValid());
    }

    @Test
    void validJEd25519KeyTest() {
        JEd25519Key key = new JEd25519Key(new byte[JEd25519Key.ED25519_BYTE_LENGTH]);
        assertTrue(key.isValid());
    }

    @Test
    void equalsWorks() {
        JEd25519Key key1 = new JEd25519Key("firstKey".getBytes());
        JEd25519Key key2 = new JEd25519Key("secondKey".getBytes());
        JEd25519Key key3 = new JEd25519Key("firstKey".getBytes());

        assertNotEquals(key1, key2);
        assertNotEquals(key1.hashCode(), key2.hashCode());
        assertEquals(key1, key3);
        assertEquals(key1.hashCode(), key3.hashCode());
        assertEquals(key1, key1);
        boolean forceEquals = key2.equals("sampleText");
        assertFalse(forceEquals);
        forceEquals = key1.equals(null);
        assertFalse(forceEquals);
    }

    @Test
    void canGetPrimitiveKeyForEd25519OrSecp256k1() {
        final var mockEd25519 = new JEd25519Key("01234578901234578901234578901".getBytes());
        final var mockSecp256k1 = new JECDSASecp256k1Key("012345789012345789012345789012".getBytes());

        assertEquals(0, mockEd25519.getECDSASecp256k1Key().length);
        assertEquals(0, mockSecp256k1.getEd25519().length);
    }
}
