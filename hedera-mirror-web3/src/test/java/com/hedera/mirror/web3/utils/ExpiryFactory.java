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

public class ExpiryFactory {

    private static final String INNER_CLASS_NAME = "Expiry";
    private static Class classType;
    private static Constructor constructor;

    public ExpiryFactory(final Class thatClassType) {
        classType = thatClassType;
    }

    public <T> T getInstance(final BigInteger second, final String autoRenewAccount, final BigInteger autoRenewPeriod) {
        try {
            Class<?> innerClass;
            for (Class<?> declaredClass : classType.getDeclaredClasses()) {
                if (declaredClass.getSimpleName().equals(INNER_CLASS_NAME)) {
                    innerClass = declaredClass;
                    constructor = innerClass.getConstructor(BigInteger.class, String.class, BigInteger.class);
                    break;
                }
            }

            // Use reflection to create a new instance of the Expiry class
            return (T) constructor.newInstance(second, autoRenewAccount, autoRenewPeriod);
        } catch (Exception e) {
            throw new RuntimeException("Unable to instantiate Expiry class", e);
        }
    }
}
