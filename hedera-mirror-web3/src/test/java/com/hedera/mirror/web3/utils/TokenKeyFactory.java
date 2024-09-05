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
import java.math.BigInteger;

public class TokenKeyFactory {

    private static final String INNER_CLASS_NAME = "TokenKey";
    private static final String HELPER_CLASS_NAME = "KeyValue";
    private static Class classType;
    private static Constructor constructor;

    public TokenKeyFactory(final Class thatClassType) {
        classType = thatClassType;
    }

    public <T, U> T getInstance(final BigInteger keyType, final U keyValue) {
        try {
            Class<?> innerClassKeyValue = null;
            for (Class<?> declaredClass : classType.getDeclaredClasses()) {
                if (declaredClass.getSimpleName().equals(HELPER_CLASS_NAME)) {
                    innerClassKeyValue = declaredClass;
                    break;
                }
            }

            Class<?> innerClassTokenKey;
            for (Class<?> declaredClass : classType.getDeclaredClasses()) {
                if (declaredClass.getSimpleName().equals(INNER_CLASS_NAME)) {
                    innerClassTokenKey = declaredClass;
                    constructor = innerClassTokenKey.getConstructor(BigInteger.class, innerClassKeyValue);
                    break;
                }
            }

            return (T) constructor.newInstance(keyType, keyValue);
        } catch (Exception e) {
            throw new RuntimeException("Unable to instantiate Expiry class", e);
        }
    }
}
