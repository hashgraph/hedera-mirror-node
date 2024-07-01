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
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.CustomLog;
import org.springframework.transaction.support.TransactionOperations;

@CustomLog
abstract class AbstractEntityBuilder {

    /*
     * Map builder parameter value provided in spec JSON to the type expected by the builder method.
     */
    private static final Map<Class<?>, Function<Object, Object>> DEFAULT_PARAMETER_CONVERTERS = Map.of(
            java.lang.Boolean.class, source -> Boolean.parseBoolean(String.valueOf(source)),
            java.lang.Integer.class, source -> Integer.parseInt(String.valueOf(source)),
            java.lang.Long.class, source -> Long.parseLong(String.valueOf(source)),
            byte[].class, source -> source
    );

    private static final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();

    protected final Map<Class<?>, Function<Object, Object>> parameterConverters;
    protected final EntityManager entityManager;
    protected final TransactionOperations transactionOperations;

    protected AbstractEntityBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        this.entityManager = entityManager;
        this.transactionOperations = transactionOperations;
        this.parameterConverters = DEFAULT_PARAMETER_CONVERTERS;
    }

    protected AbstractEntityBuilder(EntityManager entityManager, TransactionOperations transactionOperations,
            Map<Class<?>, Function<Object, Object>> parameterConverters) {
        this.entityManager = entityManager;
        this.transactionOperations = transactionOperations;
        this.parameterConverters =
                Stream.concat(DEFAULT_PARAMETER_CONVERTERS.entrySet().stream(), parameterConverters.entrySet().stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2));
    }

    abstract void customizeAndPersistEntity(Map<String, Object> entityAttributes);

    protected void customizeWithSpec(DomainWrapper<?, ?> wrapper, Map<String, Object> customizations) {
        wrapper.customize(builder -> {
            var builderClass = builder.getClass();
            var builderMethods = methodCache.computeIfAbsent(builderClass, clazz -> Arrays.stream(
                    clazz.getMethods()).collect(Collectors.toMap(Method::getName, Function.identity(), (m1, m2) -> m2)));

            for (var customization : customizations.entrySet()) {
                var methodName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, customization.getKey());
                var method = builderMethods.get(methodName);
                if (method != null) {
                    try {
                        var expectedParameterType = method.getParameterTypes()[0];
                        var mappedBuilderParameter = mapBuilderParameter(expectedParameterType, customization.getValue());
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

    protected Object mapBuilderParameter(Class<?> expectedType, Object specParameterValue) {
        var typeMapper = DEFAULT_PARAMETER_CONVERTERS.getOrDefault(expectedType, Function.identity());
        return  typeMapper.apply(specParameterValue);
    }
}
