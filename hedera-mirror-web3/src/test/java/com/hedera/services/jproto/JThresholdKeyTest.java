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

import java.util.List;
import org.junit.jupiter.api.Test;

class JThresholdKeyTest {
    @Test
    void isEmpty() {
        final var cut = new JThresholdKey(new JKeyList(), 0);

        assertTrue(cut.isEmpty());
    }

    @Test
    void isEmptySubkeys() {
        final var cut = new JThresholdKey(new JKeyList(List.of(new JEd25519Key(new byte[0]))), 1);

        assertTrue(cut.isEmpty());

        final var cut1 = new JThresholdKey(new JKeyList(List.of(new JECDSASecp256k1Key(new byte[0]))), 1);

        assertTrue(cut1.isEmpty());
    }

    @Test
    void isNotEmpty() {
        final var cut = new JThresholdKey(new JKeyList(List.of(new JEd25519Key(new byte[1]))), 1);

        assertFalse(cut.isEmpty());
    }

    @Test
    void degenerateKeyNotForScheduledTxn() {
        // given:
        final var subject = new JThresholdKey(null, 0);

        // expect:
        assertFalse(subject.isForScheduledTxn());
    }

    @Test
    void delegatesScheduledScope() {
        // setup:
        final var ed25519Key = new JEd25519Key("ed25519".getBytes());
        final var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        final var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        final var ecdsasecp256k1Key = new JECDSASecp256k1Key("ecdsasecp256k1".getBytes());
        final var contractKey = new JContractIDKey(0, 0, 75231);
        // and:
        final List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey, ecdsasecp256k1Key);
        final var delegate = new JKeyList(keys);

        // given:
        final var subject = new JThresholdKey(delegate, 1);
        // and:
        assertFalse(subject.isForScheduledTxn());

        // expect:
        for (final JKey key : keys) {
            key.setForScheduledTxn(true);
            assertTrue(subject.isForScheduledTxn());
            key.setForScheduledTxn(false);
        }
    }

    @Test
    void propagatesSettingScheduledScope() {
        // setup:
        final var ed25519Key = new JEd25519Key("ed25519".getBytes());
        final var ecdsa384Key = new JECDSA_384Key("ecdsa384".getBytes());
        final var rsa3072Key = new JRSA_3072Key("rsa3072".getBytes());
        final var contractKey = new JContractIDKey(0, 0, 75231);
        // and:
        final List<JKey> keys = List.of(ed25519Key, ecdsa384Key, rsa3072Key, contractKey);

        // given:
        final var subject = new JThresholdKey(new JKeyList(keys), 1);

        // when:
        subject.setForScheduledTxn(true);
        // then:
        for (final JKey key : keys) {
            assertTrue(key.isForScheduledTxn());
        }
        // and when:
        subject.setForScheduledTxn(false);
        // then:
        assertFalse(subject.isForScheduledTxn());
    }
}
