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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.DecoderException;

/**
 * Maps to proto Key.
 */
public abstract class JKey {

    private static final byte[] MISSING_ED25519_KEY = new byte[0];
    private static final byte[] MISSING_ECDSA_SECP256K1_KEY = new byte[0];
    static final int MAX_KEY_DEPTH = 15;

    /**
     * Maps a proto Key to Jkey.
     *
     * @param key the proto Key to be converted
     * @return the generated JKey instance
     * @throws DecoderException on an inconvertible given key
     */
    public static JKey mapKey(Key key) throws DecoderException {
        return convertKey(key, 1);
    }

    /**
     * Maps a JKey instance to a proto Key instance.
     *
     * @param jkey the JKey to be converted
     * @return the converted proto Key instance
     * @throws DecoderException on an inconvertible given key
     */
    public static Key mapJKey(JKey jkey) throws DecoderException {
        return convertJKey(jkey, 1);
    }

    /**
     * Converts a key up to a given level of depth. Both the signature and the key may be complex with multiple levels.
     *
     * @param key   the current proto Key to be converted
     * @param depth current level that is to be verified. The first level has a value of 1.
     * @return the converted JKey instance
     * @throws org.apache.commons.codec.DecoderException on an inconvertible given key
     */
    public static JKey convertKey(Key key, int depth) throws DecoderException {
        if (depth > MAX_KEY_DEPTH) {
            throw new DecoderException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
        }

        if (!(key.hasThresholdKey() || key.hasKeyList())) {
            return convertBasic(key);
        } else {
            List<Key> tKeys = key.getKeyList().getKeysList();
            List<JKey> jkeys = new ArrayList<>();
            for (Key aKey : tKeys) {
                JKey res = convertKey(aKey, depth + 1);
                jkeys.add(res);
            }
            return new JKeyList(jkeys);
        }
    }

    /**
     * Converts a JKey to proto Key for up to a given level of depth.
     *
     * @param jkey  the current JKey to be converted
     * @param depth current level that is to be verified. The first level has a value of 1.
     * @return the converted proto Key instance
     * @throws DecoderException on an inconvertible given key
     */
    public static Key convertJKey(JKey jkey, int depth) throws DecoderException {
        if (depth > MAX_KEY_DEPTH) {
            throw new DecoderException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
        }

        if (!(jkey.hasThresholdKey() || jkey.hasKeyList())) {
            return convertJKeyBasic(jkey);
        } else {
            List<JKey> jKeys = jkey.getKeyList().getKeysList();
            List<Key> tkeys = new ArrayList<>();
            for (JKey aKey : jKeys) {
                Key res = convertJKey(aKey, depth + 1);
                tkeys.add(res);
            }
            KeyList keys = KeyList.newBuilder().addAllKeys(tkeys).build();
            Key result = Key.newBuilder().setKeyList(keys).build();
            return (result);
        }
    }

    /**
     * Converts a basic JKey to proto Key.
     *
     * @param jkey JKey object to be converted
     * @return the converted proto Key instance
     * @throws DecoderException on an inconvertible given key
     */
    static Key convertJKeyBasic(JKey jkey) throws DecoderException {
        Key rv;
        if (jkey.hasEd25519Key()) {
            rv = Key.newBuilder()
                    .setEd25519(ByteString.copyFrom(jkey.getEd25519()))
                    .build();
        } else if (jkey.hasECDSAsecp256k1Key()) {
            rv = Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFrom(jkey.getECDSASecp256k1Key()))
                    .build();
        } else if (jkey.hasContractID()) {
            rv = Key.newBuilder()
                    .setContractID(jkey.getContractIDKey().getContractID())
                    .build();
        } else if (jkey.hasDelegatableContractId()) {
            rv = Key.newBuilder()
                    .setDelegatableContractId(jkey.getDelegatableContractIdKey().getContractID())
                    .build();
        } else {
            throw new DecoderException("Key type not implemented: key=" + jkey);
        }

        return rv;
    }

    /**
     * Converts a basic key.
     *
     * @param key proto Key to be converted
     * @return the converted JKey instance
     * @throws org.apache.commons.codec.DecoderException on an inconvertible given key
     */
    private static JKey convertBasic(Key key) throws org.apache.commons.codec.DecoderException {
        JKey rv;
        if (!key.getEd25519().isEmpty()) {
            byte[] pubKeyBytes = key.getEd25519().toByteArray();
            rv = new JEd25519Key(pubKeyBytes);
        } else if (!key.getECDSASecp256K1().isEmpty()) {
            byte[] pubKeyBytes = key.getECDSASecp256K1().toByteArray();
            rv = new JECDSASecp256k1Key(pubKeyBytes);
        } else if (key.getContractID().getContractNum() != 0) {
            rv = new JContractIDKey(key.getContractID());
        } else if (key.getDelegatableContractId().getContractNum() != 0) {
            rv = new JDelegatableContractIDKey(key.getDelegatableContractId());
        } else {
            throw new org.apache.commons.codec.DecoderException("Key type not implemented: key=" + key);
        }

        return rv;
    }

    public byte[] primitiveKeyIfPresent() {
        if (hasEd25519Key()) {
            return getEd25519();
        } else if (hasECDSAsecp256k1Key()) {
            return getECDSASecp256k1Key();
        } else {
            return MISSING_ECDSA_SECP256K1_KEY;
        }
    }

    public abstract boolean isEmpty();

    /**
     * Expected to return {@code false} if the key is empty
     *
     * @return whether the key is valid
     */
    public abstract boolean isValid();

    public boolean hasEd25519Key() {
        return false;
    }

    public boolean hasECDSAsecp256k1Key() {
        return false;
    }

    public boolean hasContractID() {
        return false;
    }

    public boolean hasKeyList() {
        return false;
    }

    public boolean hasThresholdKey() {
        return false;
    }

    public boolean hasDelegatableContractId() {
        return false;
    }

    public JKeyList getKeyList() {
        return null;
    }

    public byte[] getEd25519() {
        return MISSING_ED25519_KEY;
    }

    public byte[] getECDSASecp256k1Key() {
        return MISSING_ECDSA_SECP256K1_KEY;
    }

    public JContractIDKey getContractIDKey() {
        return null;
    }

    public JDelegatableContractIDKey getDelegatableContractIdKey() {
        return null;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        try {
            return Objects.equals(mapJKey(this), mapJKey((JKey) other));
        } catch (DecoderException ignore) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        try {
            return Objects.hashCode(mapJKey(this));
        } catch (DecoderException ignore) {
            return Integer.MIN_VALUE;
        }
    }
}
