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
import java.util.List;

public class HederaTokenFactory {

    private static final String INNER_CLASS_NAME = "HederaToken";
    private static final String HELPER_CLASS_NAME = "Expiry";
    private static Class classType;
    private static Constructor constructor;

    public HederaTokenFactory(final Class thatClassType) {
        classType = thatClassType;
    }

    public <T, U, V> T getInstance(
            final String name,
            final String symbol,
            final String treasury,
            final String memo,
            final Boolean tokenSupplyType,
            final BigInteger maxSupply,
            final Boolean freezeDefault,
            final List<U> tokenKeys,
            V expiry) {
        try {
            Class<?> innerClassExpiry = null;
            for (Class<?> declaredClass : classType.getDeclaredClasses()) {
                if (declaredClass.getSimpleName().equals(HELPER_CLASS_NAME)) {
                    innerClassExpiry = declaredClass;
                    break;
                }
            }

            Class<?> innerClassTokenKey;
            for (Class<?> declaredClass : classType.getDeclaredClasses()) {
                if (declaredClass.getSimpleName().equals(INNER_CLASS_NAME)) {
                    innerClassTokenKey = declaredClass;
                    constructor = innerClassTokenKey.getConstructor(
                            String.class,
                            String.class,
                            String.class,
                            String.class,
                            Boolean.class,
                            BigInteger.class,
                            Boolean.class,
                            List.class,
                            innerClassExpiry);
                    break;
                }
            }

            return (T) constructor.newInstance(
                    name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault, tokenKeys, expiry);
        } catch (Exception e) {
            throw new RuntimeException("Unable to instantiate Expiry class", e);
        }
    }
}
