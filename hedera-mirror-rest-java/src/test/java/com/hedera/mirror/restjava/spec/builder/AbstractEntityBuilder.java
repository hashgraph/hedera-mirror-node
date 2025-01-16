/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.CollectionUtils;

@CustomLog
abstract class AbstractEntityBuilder<T, B> implements SpecDomainBuilder {
    private static final Base32 BASE32 = new Base32();

    /*
     * Common handy spec attribute value converter functions to be used by subclasses.
     */
    protected static final Function<Object, Object> BASE32_CONVERTER =
            value -> value == null ? null : BASE32.decode(value.toString());
    private static final Pattern HEX_STRING_PATTERN = Pattern.compile("^(0x)?[0-9A-Fa-f]+$");
    protected static final Function<Object, Object> HEX_OR_BASE64_CONVERTER = value -> {
        if (value instanceof String valueStr) {
            if (HEX_STRING_PATTERN.matcher(valueStr).matches()) {
                var cleanValueStr = valueStr.replace("0x", "");

                if (cleanValueStr.length() % 2 != 0) {
                    return HexFormat.of().parseHex(cleanValueStr.substring(0, cleanValueStr.length() - 1));
                }

                return HexFormat.of().parseHex(cleanValueStr);
            }
            return Base64.getDecoder().decode(valueStr);
        }

        if (value instanceof Collection<?> valueCollection) {
            return ArrayUtils.toPrimitive(valueCollection.stream()
                    .map(item -> ((Integer) item).byteValue())
                    .toArray(Byte[]::new));
        }

        return value;
    };
    private static final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();
    // Map a synthetic spec attribute name to another attribute name convertable to a builder method name
    protected final Map<String, String> attributeNameMap;
    // Map a builder method by name to a specific attribute value converter function
    protected final Map<String, Function<Object, Object>> methodParameterConverters;

    @Resource
    protected ConversionService conversionService;

    @Resource
    private EntityManager entityManager;

    @Resource
    private TransactionOperations transactionOperations;

    protected AbstractEntityBuilder() {
        this(Map.of());
    }

    protected AbstractEntityBuilder(Map<String, Function<Object, Object>> methodParameterConverters) {
        this(methodParameterConverters, Map.of());
    }

    protected AbstractEntityBuilder(
            Map<String, Function<Object, Object>> methodParameterConverters, Map<String, String> attributeNameMap) {
        this.methodParameterConverters = methodParameterConverters;
        this.attributeNameMap = attributeNameMap;
    }

    /**
     * Return the required entity builder instance configured with all initial default values which may be
     * overridden based on further customization using the spec JSON setup.
     *
     * @param builderContext carries state information about the entity being built
     * @return entity builder
     */
    protected abstract B getEntityBuilder(SpecBuilderContext builderContext);

    /**
     * Perform any post customization processing required and produce a final DB entity to be persisted.
     *
     * @param builder entity builder
     * @param entityAttributes spec setup attributes
     * @return entity to be persisted
     */
    protected abstract T getFinalEntity(B builder, Map<String, Object> entityAttributes);

    /**
     * Return the supplier function used to return the relevant attributes from the spec JSON setup object.
     *
     * @param specSetup spec setup attributes
     * @return
     */
    protected abstract Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup);

    protected boolean isHistory(Map<String, Object> entityAttributes) {
        return Optional.ofNullable(entityAttributes.get("timestamp_range"))
                .map(range -> !range.toString().endsWith(",)"))
                .orElse(false);
    }

    @Override
    public void customizeAndPersistEntities(SpecSetup specSetup) {
        var specEntities = getSpecEntitiesSupplier(specSetup).get();
        if (!CollectionUtils.isEmpty(specEntities)) {
            specEntities.forEach(specEntity -> transactionOperations.executeWithoutResult(t -> {
                var entityBuilder = getEntityBuilder(new SpecBuilderContext(isHistory(specEntity)));
                customizeWithSpec(entityBuilder, specEntity);
                var entity = getFinalEntity(entityBuilder, specEntity);
                entityManager.persist(entity);
            }));
        }
    }

    private void customizeWithSpec(B builder, Map<String, Object> customizations) {
        var builderClass = builder.getClass();
        var builderMethods = methodCache.computeIfAbsent(builderClass, clazz -> Arrays.stream(clazz.getMethods())
                .collect(Collectors.toMap(Method::getName, Function.identity(), (v1, v2) -> v2)));

        for (var customization : customizations.entrySet()) {
            var methodName = methodName(customization.getKey());
            var method = builderMethods.get(methodName);
            if (method != null) {
                try {
                    var expectedParameterType = method.getParameterTypes()[0];
                    var mappedBuilderParameter =
                            mapBuilderParameter(methodName, expectedParameterType, customization.getValue());
                    method.invoke(builder, mappedBuilderParameter);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    log.warn(
                            "Failed to invoke method '{}' for attribute override '{}' for {}",
                            methodName,
                            customization.getKey(),
                            builderClass.getName(),
                            e);
                }
            } else {
                log.warn("Unknown attribute override '{}' for {}", customization.getKey(), builderClass.getName());
            }
        }
    }

    private Object mapBuilderParameter(String methodName, Class<?> expectedType, Object specParameterValue) {
        var typeMapper = methodParameterConverters.get(methodName);
        if (typeMapper != null) {
            return typeMapper.apply(specParameterValue);
        }
        return conversionService.convert(specParameterValue, expectedType);
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
