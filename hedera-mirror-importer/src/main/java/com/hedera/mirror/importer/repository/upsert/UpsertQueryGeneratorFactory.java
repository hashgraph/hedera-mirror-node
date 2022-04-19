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

import com.google.common.base.CaseFormat;
import com.google.common.collect.Range;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;

import com.hedera.mirror.common.domain.Upsertable;

@Log4j2
@Named
@RequiredArgsConstructor
public class UpsertQueryGeneratorFactory {

    private final EntityManager entityManager;
    private final Collection<UpsertQueryGenerator> existingGenerators;
    private final Map<Class<?>, UpsertQueryGenerator> upsertQueryGenerators = new ConcurrentHashMap<>();

    public UpsertQueryGenerator get(Class<?> domainClass) {
        return upsertQueryGenerators.computeIfAbsent(domainClass, this::findOrCreate);
    }

    /**
     * This method relies on the convention that the domain class and its associated UpsertQueryGenerator have the same
     * prefix. Otherwise, it falls back to creating a generic upsert query generator.
     */
    private UpsertQueryGenerator findOrCreate(Class<?> domainClass) {
        String className = domainClass.getSimpleName() + UpsertQueryGenerator.class.getSimpleName();
        return existingGenerators.stream()
                .filter(u -> u.getClass().getSimpleName().equals(className))
                .findFirst()
                .orElseGet(() -> create(domainClass));
    }

    private UpsertQueryGenerator create(Class<?> domainClass) {
        UpsertEntity upsertEntity = createEntity(domainClass);
        log.debug("Creating {}", upsertEntity);
        return new GenericUpsertQueryGenerator(upsertEntity);
    }

    UpsertEntity createEntity(Class<?> domainClass) {
        Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);

        if (upsertable == null) {
            throw new UnsupportedOperationException("Class is not annotated with @Upsertable: " + domainClass);
        }

        EntityType<?> entityType = entityManager.getMetamodel().entity(domainClass);
        Table table = AnnotationUtils.findAnnotation(domainClass, Table.class);
        String tableName = table != null ? table.name() : toSnakeCase(entityType.getName());
        Set<String> idAttributes = getIdAttributes(entityType);
        Set<UpsertColumn> upsertColumns = new TreeSet<>();

        Map<String, InformationSchemaColumns> schema = getColumnSchema(tableName);

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            boolean id = idAttributes.contains(attribute.getName());
            upsertColumns.add(createUpsertColumn(schema, attribute, upsertable.history(), id));
        }

        return new UpsertEntity(tableName, upsertable, upsertColumns);
    }

    private UpsertColumn createUpsertColumn(Map<String, InformationSchemaColumns> schema,
                                            Attribute<?, ?> attribute, boolean historyTable, boolean id) {
        String name = attribute.getName();
        Field field = (Field) attribute.getJavaMember();
        Column column = field.getAnnotation(Column.class);
        String columnName = column != null && StringUtils.isNotBlank(column.name()) ?
                toSnakeCase(column.name()) :
                toSnakeCase(name);

        boolean history = Range.class == attribute.getJavaType();
        boolean nullable = !id && (column == null || column.nullable());
        boolean updatable = false;
        if (!id) {
            // for non-id columns, if the table has history, by default it's updatable or set by the Column annotation;
            // if the table doesn't have history,  it's updatable if set so by the Column annotation.
            updatable = historyTable ? (column == null || column.updatable()) : (column != null && column.updatable());
        }
        InformationSchemaColumns columnSchema = schema.get(columnName);

        if (columnSchema == null) {
            throw new IllegalStateException("Missing information schema for " + columnName);
        }

        return new UpsertColumn(columnSchema.getColumnDefault(), history, id, columnName, nullable, updatable);
    }

    /*
     * Looks up column defaults in the information_schema.columns table.
     */
    private Map<String, InformationSchemaColumns> getColumnSchema(String tableName) {
        String sql = "select column_name, regexp_replace(column_default, '::.*', '') as column_default" +
                " from information_schema.columns where table_name = ?";

        Query query = entityManager.createNativeQuery(sql, InformationSchemaColumns.class);
        query.setParameter(1, tableName);
        Map<String, InformationSchemaColumns> schema = (Map<String, InformationSchemaColumns>) query.getResultList()
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

            if (idAttribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
                throw new UnsupportedOperationException();
            }

            return Set.of(idAttribute.getName());
        }
    }

    private String toSnakeCase(String text) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, text);
    }

    @Data
    @Entity(name = "columns")
    static class InformationSchemaColumns {
        @Id
        private String columnName;
        private String columnDefault;
    }
}
