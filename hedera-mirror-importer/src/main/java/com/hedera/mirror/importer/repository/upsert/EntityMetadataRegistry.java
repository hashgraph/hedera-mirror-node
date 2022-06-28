package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.util.Utility.toSnakeCase;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EmbeddableType;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.metamodel.model.domain.spi.SingularPersistentAttribute;
import org.springframework.core.annotation.AnnotationUtils;

import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;

@Log4j2
@Named
@RequiredArgsConstructor
public class EntityMetadataRegistry {

    private final EntityManager entityManager;
    private final Map<Class<?>, EntityMetadata> entityMetadata = new ConcurrentHashMap<>();

    public EntityMetadata lookup(Class<?> domainClass) {
        return entityMetadata.computeIfAbsent(domainClass, this::create);
    }

    private EntityMetadata create(Class<?> domainClass) {
        Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);

        if (upsertable == null) {
            throw new UnsupportedOperationException("Class is not annotated with @Upsertable: " + domainClass);
        }

        EntityType<?> entityType = entityManager.getMetamodel().entity(domainClass);
        Table table = AnnotationUtils.findAnnotation(domainClass, Table.class);
        String tableName = table != null ? table.name() : toSnakeCase(entityType.getName());
        Set<String> idAttributes = getIdAttributes(entityType);
        Set<ColumnMetadata> columnMetadata = new TreeSet<>();

        Map<String, InformationSchemaColumns> schema = getColumnSchema(tableName);

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            boolean id = idAttributes.contains(attribute.getName());

            if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
                var persistentAttribute = (SingularPersistentAttribute) attribute;
                var embeddableType = (EmbeddableType<?>) persistentAttribute.getType();
                embeddableType.getDeclaredSingularAttributes()
                        .forEach(a -> columnMetadata.add(columnMetadata(schema, a, id)));
            } else {
                columnMetadata.add(columnMetadata(schema, attribute, id));
            }
        }

        var entityMetadata = new EntityMetadata(tableName, upsertable, columnMetadata);
        log.debug("Creating {}", entityMetadata);
        return entityMetadata;
    }

    private ColumnMetadata columnMetadata(Map<String, InformationSchemaColumns> schema,
                                          Attribute<?, ?> attribute, boolean id) {
        String name = attribute.getName();
        Field field = (Field) attribute.getJavaMember();
        Column column = field.getAnnotation(Column.class);
        UpsertColumn upsertColumn = field.getAnnotation(UpsertColumn.class);
        String columnName = column != null && StringUtils.isNotBlank(column.name()) ?
                toSnakeCase(column.name()) :
                toSnakeCase(name);

        InformationSchemaColumns columnSchema = schema.get(columnName);

        if (columnSchema == null) {
            throw new IllegalStateException("Missing information schema for " + columnName);
        }

        var getter = getter(field);
        var setter = setter(field);
        boolean updatable = !id && (column == null || column.updatable());
        return new ColumnMetadata(columnSchema.getColumnDefault(), getter, id, columnName,
                columnSchema.isNullable(), setter, attribute.getJavaType(), updatable, upsertColumn);
    }

    /*
     * Looks up column defaults in the information_schema.columns table.
     */
    private Map<String, InformationSchemaColumns> getColumnSchema(String tableName) {
        String sql = "select column_name, regexp_replace(column_default, '::.*', '') as column_default, " +
                "is_nullable = 'YES' as nullable from information_schema.columns where table_name = ?";

        Query query = entityManager.createNativeQuery(sql, InformationSchemaColumns.class);
        query.setParameter(1, tableName);
        var schema = (Map<String, InformationSchemaColumns>) query.getResultList()
                .stream()
                .collect(Collectors.toMap(InformationSchemaColumns::getColumnName, Function.identity()));

        if (schema == null || schema.isEmpty()) {
            throw new IllegalStateException("Missing information schema for " + tableName);
        }

        return schema;
    }

    private Set<String> getIdAttributes(EntityType<?> entityType) {
        try {
            return entityType.getIdClassAttributes()
                    .stream()
                    .map(SingularAttribute::getName)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            SingularAttribute<?, ?> idAttribute = entityType.getId(Object.class);

            var attributeType = idAttribute.getPersistentAttributeType();
            if (attributeType != Attribute.PersistentAttributeType.BASIC &&
                    attributeType != Attribute.PersistentAttributeType.EMBEDDED) {
                throw new UnsupportedOperationException("Unsupported ID attribute " + entityType.getName());
            }

            return Set.of(idAttribute.getName());
        }
    }

    private Function<Object, Object> getter(Field field) {
        try {
            String prefix = field.getType().equals(boolean.class) ? "is" : "get";
            String methodName = prefix + StringUtils.capitalize(field.getName());
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(field.getType());
            MethodHandle handle = lookup.findVirtual(field.getDeclaringClass(), methodName, type);
            MethodType functionType = handle.type();
            return (Function<Object, Object>) LambdaMetafactory.metafactory(lookup, "apply",
                    methodType(Function.class), functionType.erase(), handle, functionType).getTarget().invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private BiConsumer<Object, Object> setter(Field field) {
        try {
            String methodName = "set" + StringUtils.capitalize(field.getName());
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(void.class, field.getType());
            MethodHandle handle = lookup.findVirtual(field.getDeclaringClass(), methodName, type);
            MethodType functionType = handle.type();
            return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(lookup, "accept",
                    methodType(BiConsumer.class), functionType.erase(), handle, functionType).getTarget().invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Data
    @Entity(name = "columns")
    static class InformationSchemaColumns {
        @Id
        private String columnName;
        private String columnDefault;
        private boolean nullable;
    }
}
