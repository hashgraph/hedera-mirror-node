/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JKeyListTest {
    @Test
    void requiresNonNullKeys() {
        // expect:
        Assertions.assertThrows(IllegalArgumentException.class, () -> new JKeyList(null));
    }

    @Test
    void defaultConstructor() {
        final var cut = new JKeyList();

        assertEquals(0, cut.getKeysList().size());
    }

    @Test
    void isEmptySubkeys() {
        final var cut = new JKeyList(List.of(new JEd25519Key(new byte[0])));

        assertTrue(cut.isEmpty());
    }

    @Test
    void isNotEmpty() {
        final var cut = new JKeyList(List.of(new JECDSA_384Key(new byte[1])));

        assertFalse(cut.isEmpty());
    }

    @Test
    void requiresAnExplicitScheduledChild() {
        // setup:
        var ed25519Key = new JEd25519Key("ed25519".getBytes());
        var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        var contractKey = new JContractIDKey(0, 0, 75231);
        var ecdsasecp256k1Key = new JECDSASecp256k1Key("ecdsasecp256k1".getBytes());
        // and:
        List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey, ecdsasecp256k1Key);

        // given:
        var subject = new JKeyList(keys);
        // and:
        assertFalse(subject.isForScheduledTxn());

        // expect:
        for (JKey key : keys) {
            key.setForScheduledTxn(true);
            assertTrue(subject.isForScheduledTxn());
            key.setForScheduledTxn(false);
        }
    }

    @Test
    void propagatesScheduleScope() {
        // setup:
        var ed25519Key = new JEd25519Key("ed25519".getBytes());
        var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        var contractKey = new JContractIDKey(0, 0, 75231);
        // and:
        List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey);

        // given:
        var subject = new JKeyList(keys);

        // when:
        subject.setForScheduledTxn(true);
        // then:
        for (JKey key : keys) {
            assertTrue(key.isForScheduledTxn());
        }
        // and when:
        subject.setForScheduledTxn(false);
        // then:
        assertFalse(subject.isForScheduledTxn());
    }

    static byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }

    static ByteString randomUtf8ByteString(int n) {
        return ByteString.copyFrom(randomUtf8Bytes(n));
    }
}
