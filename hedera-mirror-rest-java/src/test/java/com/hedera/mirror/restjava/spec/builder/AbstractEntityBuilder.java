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

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.base.CaseFormat;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.apache.commons.codec.binary.Base32;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.transaction.support.TransactionOperations;

@CustomLog
abstract class AbstractEntityBuilder {

    /*
     * Map builder parameter value provided in spec JSON to the type expected by the builder method.
     */
    private static final Map<Class<?>, Function<Object, Object>> DEFAULT_PARAMETER_CONVERTERS = Map.of(
            java.lang.Boolean.class, source -> Boolean.parseBoolean(String.valueOf(source)),
            java.lang.Integer.class, source -> Integer.parseInt(String.valueOf(source)),
            java.lang.Long.class, source -> Long.parseLong(String.valueOf(source))
    );

    private static final Base32 BASE32 = new Base32();

    private static final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();

    private static final Pattern HEX_STRING_PATTERN = Pattern.compile("/^(0x)?[0-9A-Fa-f]+$/");

    /*
     * Common handy converter functions to be used by subclasses.
     */
    protected static final Function<Object, Object> BASE32_CONVERTER = source -> BASE32.decode(source.toString());

    protected static final Function<Object, Object> ENTITY_ID_CONVERTER = source -> source instanceof String sourceStr
            ? EntityId.of(sourceStr).getId() : EntityId.of((Long)source);

    protected static final Function<Object, Object> EVM_ADDRESS_CONVERTER = source -> {
        if (source instanceof String sourceStr) {
            return HEX_STRING_PATTERN.matcher(sourceStr).matches()
                    ? Bytes.fromHexString(((String)source).startsWith("0x") ? (String)source : "0x" + source).toArray()
                    : Base64.getDecoder().decode(sourceStr);
        }
        return source;
    };


    protected final EntityManager entityManager;
    protected final TransactionOperations transactionOperations;
    protected final Map<String, Function<Object, Object>> methodParameterConverters;

    protected AbstractEntityBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        this.entityManager = entityManager;
        this.transactionOperations = transactionOperations;
        this.methodParameterConverters = Map.of();
    }

    protected AbstractEntityBuilder(EntityManager entityManager, TransactionOperations transactionOperations,
            Map<String, Function<Object, Object>> methodParameterConverters) {
        this.entityManager = entityManager;
        this.transactionOperations = transactionOperations;
        this.methodParameterConverters = methodParameterConverters;
    }

    abstract void customizeAndPersistEntity(Map<String, Object> entityAttributes);

    protected void customizeWithSpec(DomainWrapper<?, ?> wrapper, Map<String, Object> customizations) {
        wrapper.customize(builder -> {
            var builderClass = builder.getClass();
            var builderMethods = methodCache.computeIfAbsent(builderClass, clazz -> Arrays.stream(
                    clazz.getMethods()).collect(Collectors.toMap(Method::getName, Function.identity(), (v1, v2) -> v2)));

            for (var customization : customizations.entrySet()) {
                var methodName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, customization.getKey());
                var method = builderMethods.get(methodName);
                if (method != null) {
                    try {
                        var expectedParameterType = method.getParameterTypes()[0];
                        var mappedBuilderParameter = mapBuilderParameter(methodName, expectedParameterType, customization.getValue());
                        method.invoke(builder, mappedBuilderParameter);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        log.warn("Failed to invoke method '{}' for attribute override '{}' for {}",
                                methodName, customization.getKey(), builderClass.getName(), e);
                    }
                }
                else {
                    log.warn("Unknown attribute override '{}' for {}", customization.getKey(), builderClass.getName());
                }
            }
        });
    }

    protected Object mapBuilderParameter(String methodName, Class<?> expectedType, Object specParameterValue) {
        var typeMapper = methodParameterConverters.get(methodName);
        if (typeMapper == null) {
            typeMapper = DEFAULT_PARAMETER_CONVERTERS.getOrDefault(expectedType, Function.identity());
        }
        return typeMapper.apply(specParameterValue);
    }
}
