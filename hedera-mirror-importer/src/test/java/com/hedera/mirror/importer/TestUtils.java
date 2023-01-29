package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static java.lang.invoke.MethodType.methodType;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.File;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.apache.commons.beanutils.BeanUtilsBean;

import com.hedera.mirror.importer.util.Utility;

@UtilityClass
public class TestUtils {

    // Customize BeanUtilsBean to not copy properties for null since non-nulls represent partial updates in our system.
    private static final BeanUtilsBean BEAN_UTILS = new BeanUtilsBean() {
        @Override
        public void copyProperty(Object dest, String name, Object value)
                throws IllegalAccessException, InvocationTargetException {
            if (value != null) {
                super.copyProperty(dest, name, value);
            }
        }
    };

    public static <T> T clone(T object) {
        try {
            return (T) BEAN_UTILS.cloneBean(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Instant asStartOfEpochDay(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /**
     * Dynamically lookup method references for every getter in object with the given return type
     */
    public static <O, R> Collection<Supplier<R>> gettersByType(O object, Class<R> returnType) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> objectClass = object.getClass();
        Collection<Supplier<R>> getters = new ArrayList<>();

        for (var m : objectClass.getDeclaredMethods()) {
            try {
                if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                MethodType type = MethodType.methodType(returnType, objectClass);
                MethodHandle handle = lookup.unreflect(m);
                if (!handle.type().equals(type)) {
                    continue;
                }

                MethodType functionType = handle.type();
                var function = (Function<O, R>) LambdaMetafactory.metafactory(lookup, "apply",
                                methodType(Function.class), functionType.erase(), handle, functionType)
                        .getTarget()
                        .invokeExact();
                getters.add(() -> function.apply(object));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return getters;
    }

    public static File getResource(String path) {
        ClassLoader[] classLoaders = {Thread
                .currentThread().getContextClassLoader(), Utility.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()};
        URL url = null;

        for (ClassLoader classLoader : classLoaders) {
            if (classLoader != null) {
                url = classLoader.getResource(path);
                if (url != null) {
                    break;
                }
            }
        }

        if (url == null) {
            throw new RuntimeException("Cannot find resource: " + path);
        }

        try {
            return new File(url.toURI().getSchemeSpecificPart());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T merge(T previous, T current) {
        try {
            T merged = clone(previous);
            BEAN_UTILS.copyProperties(merged, current);
            return merged;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public AccountID toAccountId(String accountId) {
        var parts = accountId.split("\\.");
        return AccountID.newBuilder().setShardNum(Long.parseLong(parts[0])).setRealmNum(Long.parseLong(parts[1]))
                .setAccountNum(Long.parseLong(parts[2])).build();
    }

    public TransactionID toTransactionId(String transactionId) {
        var parts = transactionId.split("-");
        return TransactionID.newBuilder().setAccountID(toAccountId(parts[0]))
                .setTransactionValidStart(toTimestamp(Long.valueOf(parts[1]))).build();
    }

    public Timestamp toTimestamp(Long nanosecondsSinceEpoch) {
        if (nanosecondsSinceEpoch == null) {
            return null;
        }
        return Utility.instantToTimestamp(Instant.ofEpochSecond(0, nanosecondsSinceEpoch));
    }

    public Timestamp toTimestamp(long seconds, long nanoseconds) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos((int) nanoseconds).build();
    }

    public byte[] toByteArray(Key key) {
        return (null == key) ? null : key.toByteArray();
    }

    public byte[] generateRandomByteArray(int size) {
        byte[] hashBytes = new byte[size];
        new SecureRandom().nextBytes(hashBytes);
        return hashBytes;
    }
}
