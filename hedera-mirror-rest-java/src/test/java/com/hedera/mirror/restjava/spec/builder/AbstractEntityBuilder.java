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
import com.hedera.mirror.common.domain.entity.EntityId;
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

@CustomLog
abstract class AbstractEntityBuilder<B> {

    /*
     * Map builder parameter value provided in spec JSON to the type expected by the builder method.
     */
    private static final Map<Class<?>, Function<Object, Object>> DEFAULT_PARAMETER_CONVERTERS = Map.of(
            java.lang.Boolean.class, source -> Boolean.parseBoolean(String.valueOf(source)),
            java.lang.Integer.class, source -> Integer.parseInt(String.valueOf(source)),
            java.lang.Long.class, source -> Long.parseLong(String.valueOf(source))
    );

    private static final Base32 BASE32 = new Base32();
    private static final Pattern HEX_STRING_PATTERN = Pattern.compile("^(0x)?[0-9A-Fa-f]+$");
    private static final Object IGNORE_ATTRIBUTE_SIGNAL = new Object();
    private static final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();

    /*
     * Common handy spec attribute value converter functions to be used by subclasses.
     */
    protected static final Function<Object, Object> IGNORE_CONVERTER = value -> IGNORE_ATTRIBUTE_SIGNAL;

    protected static final Function<Object, Object> BASE32_CONVERTER = value -> value == null ? null : BASE32.decode(value.toString());

    protected static final Function<Object, Object> ENTITY_ID_CONVERTER = value -> value == null ? null
            : value instanceof String valueStr ? EntityId.of(valueStr) : EntityId.of((Long)value);

    protected static final Function<Object, Object> ENTITY_ID_TO_LONG_CONVERTER = value -> value == null ? 0L
            : value instanceof String valueStr ? EntityId.of(valueStr).getId() : (long)value;

    protected static final Function<Object, Object> HEX_OR_BASE64_CONVERTER = value -> {
        if (value instanceof String valueStr) {
            return HEX_STRING_PATTERN.matcher(valueStr).matches()
                    ? Bytes.fromHexString(valueStr.startsWith("0x") ? valueStr : "0x" + valueStr).toArray()
                    : Base64.getDecoder().decode(valueStr);
        }
        return value;
    };

    // Map a synthetic spec attribute name to another attribute name convertable to a builder method name
    protected final Map<String, String> attributeNameMap;
    // Map a builder method by name to a specific attribute value converter function
    protected final Map<String, Function<Object, Object>> methodParameterConverters;

    protected AbstractEntityBuilder(
            Map<String, Function<Object, Object>> methodParameterConverters) {
        this(methodParameterConverters, Map.of());
    }

    protected AbstractEntityBuilder(
            Map<String, Function<Object, Object>> methodParameterConverters,
            Map<String, String> attributeNameMap) {
        this.methodParameterConverters = methodParameterConverters;
        this.attributeNameMap = attributeNameMap;
    }

    protected abstract void customizeAndPersistEntity(Map<String, Object> entityAttributes);

    protected void customizeWithSpec(B builder, Map<String, Object> customizations) {
        var builderClass = builder.getClass();
        var builderMethods = methodCache.computeIfAbsent(builderClass, clazz -> Arrays.stream(
                clazz.getMethods()).collect(Collectors.toMap(Method::getName, Function.identity(), (v1, v2) -> v2)));

        for (var customization : customizations.entrySet()) {
            var methodName = methodName(customization.getKey());
            var method = builderMethods.get(methodName);
            if (method != null) {
                try {
                    var expectedParameterType = method.getParameterTypes()[0];
                    var mappedBuilderParameter = mapBuilderParameter(methodName, expectedParameterType, customization.getValue());
                    if (mappedBuilderParameter != IGNORE_ATTRIBUTE_SIGNAL) {
                        method.invoke(builder, mappedBuilderParameter);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    log.warn("Failed to invoke method '{}' for attribute override '{}' for {}",
                            methodName, customization.getKey(), builderClass.getName(), e);
                }
            }
            else {
                log.warn("Unknown attribute override '{}' for {}", customization.getKey(), builderClass.getName());
            }
        }
    }

    private Object mapBuilderParameter(String methodName, Class<?> expectedType, Object specParameterValue) {
        var typeMapper = methodParameterConverters.get(methodName);
        if (typeMapper == null) {
            typeMapper = DEFAULT_PARAMETER_CONVERTERS.getOrDefault(expectedType, Function.identity());
        }
        return typeMapper.apply(specParameterValue);
    }

    /*
     * The setup entity attribute names defined in the spec JSON files are named using either snake case
     * ("entity_id", "charged_tx_fee") or in lower camel case ("num", "nodeAccountId", "treasuryAccountId"). In
     * the latter case, Guava's CaseFormat will convert lower camel case to lower camel case into all lowercase.
     * Thus, it returns "nodeaccountid" and "treasuryaccountid", which do not match builder method names.
     *
     * Only invoke the guava converter if at least one underscore is present, else assume the name is already in
     * lower camel case.
     */
    private String methodName(String attributeName) {
        var mappedAttributeName = attributeNameMap.getOrDefault(attributeName, attributeName);
        return mappedAttributeName.indexOf('_') >= 0
                ? CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, mappedAttributeName)
                : mappedAttributeName;
    }
}
