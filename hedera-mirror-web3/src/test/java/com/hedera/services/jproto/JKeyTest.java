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

import static com.hedera.services.utils.EntityIdUtils.asContract;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;

import com.hedera.services.jproto.utils.TxnHandlingScenario;

class JKeyTest {

    @Test
    void positiveConvertKeyTest() {
        // given:
        final Key aKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();

        // expect:
        assertDoesNotThrow(() -> JKey.convertKey(aKey, 1));
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
    void byDefaultHasNoPrimitiveKey() {
        final var subject = mock(JKey.class);

        doCallRealMethod().when(subject).hasDelegatableContractId();
        doCallRealMethod().when(subject).getDelegatableContractIdKey();

        assertFalse(subject.hasDelegatableContractId());
        assertNull(subject.getDelegatableContractIdKey());
    }

    @Test
    void canGetPrimitiveKeyForEd25519OrSecp256k1() {
        final var mockEd25519 = new JEd25519Key("01234578901234578901234578901".getBytes());
        final var mockSecp256k1 = new JECDSASecp256k1Key("012345789012345789012345789012".getBytes());

        assertEquals(0, mockEd25519.getECDSASecp256k1Key().length);
        assertEquals(0, mockSecp256k1.getEd25519().length);
        assertEquals(0, mockSecp256k1.getECDSA384().length);
        assertEquals(0, mockSecp256k1.getRSA3072().length);
    }

    @Test
    void canMapDelegateToGrpc() throws DecoderException {
        final var id = asContract("1.2.3");
        final var expected = Key.newBuilder().setDelegatableContractId(id).build();

        final var subject = new JDelegatableContractIDKey(id);
        final var result = JKey.mapJKey(subject);

        assertEquals(expected, result);
    }

    @Test
    void canMapDelegateFromGrpc() throws DecoderException {
        final var id = asContract("1.2.3");
        final var input = Key.newBuilder().setDelegatableContractId(id).build();

        final var subject = JKey.mapKey(input);

        assertTrue(subject.hasDelegatableContractId());
        assertEquals(id, subject.getDelegatableContractIdKey().getContractID());
    }

    @Test
    void rejectsEmptyKey() {
        // expect:
        assertThrows(
                DecoderException.class,
                () -> JKey.convertJKeyBasic(new JKey() {
                    @Override
                    public boolean isEmpty() {
                        return false;
                    }

                    @Override
                    public boolean isValid() {
                        return false;
                    }

                    @Override
                    public void setForScheduledTxn(boolean flag) {
                    }

                    @Override
                    public boolean isForScheduledTxn() {
                        return false;
                    }
                }));
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

    @Test
    void convertsECDSA384BasicKey() {
        ByteString ecdsa384Bytes = ByteString.copyFromUtf8("test");
        JKey jkey = new JECDSA_384Key(ecdsa384Bytes.toByteArray());
        var key = assertDoesNotThrow(() -> JKey.convertJKeyBasic(jkey));
        assertFalse(key.getECDSA384().isEmpty());
    }

    @Test
    void convertsRSA3072Key() {
        ByteString rsa3072Bytes = ByteString.copyFromUtf8("test");
        JKey jkey = new JRSA_3072Key(rsa3072Bytes.toByteArray());
        var key = assertDoesNotThrow(() -> JKey.convertJKeyBasic(jkey));
        assertFalse(key.getRSA3072().isEmpty());
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

    Key.Builder nestKeys(Key.Builder builder, int additionalKeysToNest) {
        if (additionalKeysToNest == 0) {
            builder.setEd25519(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey().getEd25519());
            return builder;
        } else {
            var nestedBuilder = Key.newBuilder();
            nestKeys(nestedBuilder, additionalKeysToNest - 1);
            builder.setKeyList(KeyList.newBuilder().addKeys(nestedBuilder));
            return builder;
        }
    }

    JKey nestJKeys(int additionalKeysToNest) {
        if (additionalKeysToNest == 0) {
            return TxnHandlingScenario.SIMPLE_NEW_ADMIN_KT.asJKeyUnchecked();
        } else {
            final var descendantKeys = nestJKeys(additionalKeysToNest - 1);
            return new JKeyList(List.of(descendantKeys));
        }
    }
}
