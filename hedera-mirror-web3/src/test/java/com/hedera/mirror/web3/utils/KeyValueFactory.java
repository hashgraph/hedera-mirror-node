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

package com.hedera.mirror.web3.utils;

import java.lang.reflect.Constructor;

public class KeyValueFactory {

    private static final String INNER_CLASS_NAME = "KeyValue";
    private static Class classType;
    private static Constructor constructor;

    public KeyValueFactory(final Class thatClassType) {
        classType = thatClassType;
    }

    public <T> T getInstance(
            final Boolean inheritAccountKey,
            final String contractId,
            final byte[] ed25519,
            final byte[] ECDSA_secp256k1,
            final String delegatableContractId) {
        try {
            Class<?> innerClass;
            for (Class<?> declaredClass : classType.getDeclaredClasses()) {
                if (declaredClass.getSimpleName().equals(INNER_CLASS_NAME)) {
                    innerClass = declaredClass;
                    constructor = innerClass.getConstructor(
                            Boolean.class, String.class, byte[].class, byte[].class, String.class);
                    break;
                }
            }

            return (T) constructor.newInstance(
                    inheritAccountKey, contractId, ed25519, ECDSA_secp256k1, delegatableContractId);
        } catch (Exception e) {
            throw new RuntimeException("Unable to instantiate Expiry class", e);
        }
    }
}
