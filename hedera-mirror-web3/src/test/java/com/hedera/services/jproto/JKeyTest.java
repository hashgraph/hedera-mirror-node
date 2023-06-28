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

import static com.hedera.services.utils.TxnUtils.nestJKeys;
import static com.hedera.services.utils.TxnUtils.nestKeys;
import static com.hedera.services.utils.TxnUtils.randomUtf8ByteString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Arrays;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;

class JKeyTest {

    @Test
    void positiveConvertKeyTest() {
        // given:
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final var key = Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
        // expect:
        assertDoesNotThrow(() -> JKey.convertKey(key, 1));
    }

    @Test
    void negativeConvertKeyTest() {
        // given:
        var keyTooDeep = nestKeys(Key.newBuilder(), JKey.MAX_KEY_DEPTH).build();

        // expect:
        assertThrows(
                DecoderException.class,
                () -> JKey.convertKey(keyTooDeep, 1),
                "Exceeding max expansion depth of " + JKey.MAX_KEY_DEPTH);
    }

    @Test
    void negativeConvertJKeyTest() {
        // given:
        var jKeyTooDeep = nestJKeys(JKey.MAX_KEY_DEPTH);

        // expect:
        assertThrows(
                DecoderException.class,
                () -> JKey.convertJKey(jKeyTooDeep, 1),
                "Exceeding max expansion depth of " + JKey.MAX_KEY_DEPTH);
    }

    @Test
    void convertsECDSAsecp256k1Key() {
        ByteString edcsaSecp256K1Bytes = ByteString.copyFrom(new byte[] {0x02})
                .concat(randomUtf8ByteString(JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH - 1));
        final Key aKey = Key.newBuilder().setECDSASecp256K1(edcsaSecp256K1Bytes).build();

        var validEDCSAsecp256K1Key = assertDoesNotThrow(() -> JKey.convertKey(aKey, 1));
        assertTrue(validEDCSAsecp256K1Key instanceof JECDSASecp256k1Key);
        assertEquals(33, validEDCSAsecp256K1Key.getECDSASecp256k1Key().length);
        assertTrue(validEDCSAsecp256K1Key.isValid());
        assertTrue(Arrays.equals(edcsaSecp256K1Bytes.toByteArray(), validEDCSAsecp256K1Key.getECDSASecp256k1Key()));
    }
}
