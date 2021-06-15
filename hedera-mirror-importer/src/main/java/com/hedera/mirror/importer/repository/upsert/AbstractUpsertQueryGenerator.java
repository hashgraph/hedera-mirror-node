package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import com.google.common.reflect.TypeToken;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.metamodel.SingularAttribute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.converter.NullableStringSerializer;
import com.hedera.mirror.importer.db.FlywayProperties;

@RequiredArgsConstructor
public abstract class AbstractUpsertQueryGenerator<T> implements UpsertQueryGenerator {
    private static final String EMPTY_STRING = "\'\'";
    private static final String NULL_STRING = "null";
    private static final String RESERVED_CHAR = "\'" + NullableStringSerializer.NULLABLE_STRING_REPLACEMENT + "\'";
    private final Comparator<DomainField> DOMAIN_FIELD_COMPARATOR = Comparator
            .comparing(DomainField::getName);
    private Set<Field> attributes = null;

    private final Class<T> metaModelClass = (Class<T>) new TypeToken<T>(getClass()) {
    }.getRawType();

    @Getter(lazy = true)
    private final String insertQuery = generateInsertQuery();

    @Getter(lazy = true)
    private final String updateQuery = generateUpdateQuery();

    private final FlywayProperties flywayProperties;

    protected final Logger log = LogManager.getLogger(getClass());

    protected abstract String getInsertWhereClause();

    protected abstract Set<String> getNonUpdatableColumns();

    protected abstract String getUpdateWhereClause();

    @Override
    public String getCreateTempTableQuery() {
        return String.format("create temporary table %s on commit drop as table %s limit 0",
                getTemporaryTableName(), getFinalTableName());
    }

    protected String getAttributeSelectQuery(String attributeName) {
        return null;
    }

    protected List<String> getV1ConflictIdColumns() {
        return Collections.emptyList();
    }

    protected List<String> getV2ConflictIdColumns() {
        return Collections.emptyList();
    }

    protected Set<String> getNullableColumns() {
        return null;
    }

    // Note JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    protected Set<SingularAttribute> getSelectableColumns() {
        return Collections.emptySet();
    }

    private String generateInsertQuery() {
        StringBuilder insertQueryBuilder = new StringBuilder("insert into " + getFinalTableName());

        // build target column list
        insertQueryBuilder.append(String.format(" (%s) select ", getColumnListFromSelectableColumns()));

        insertQueryBuilder.append(getSelectableFieldsClause());

        insertQueryBuilder.append(" from " + getTemporaryTableName());

        // insertable query
        insertQueryBuilder.append(getInsertWhereClause());

        insertQueryBuilder.append(getDoNothingConflictClause());

        return insertQueryBuilder.toString();
    }

    private String generateUpdateQuery() {
        StringBuilder updateQueryBuilder = new StringBuilder("update " + getFinalTableName() + " set ");

        updateQueryBuilder.append(getUpdateClause());

        updateQueryBuilder.append(" from " + getTemporaryTableName());

        // insertable query
        updateQueryBuilder.append(getUpdateWhereClause());

        return updateQueryBuilder.toString();
    }

    private boolean isNullableColumn(String columnName) {
        return getNullableColumns() == null ? false : getNullableColumns().contains(columnName);
    }

    private List<String> getConflictIdColumns() {
        if (flywayProperties.getLocations().contains(FlywayProperties.V1)) {
            return getV1ConflictIdColumns();
        } else if (flywayProperties.getLocations().contains(FlywayProperties.V2)) {
            return getV2ConflictIdColumns();
        }

        return Collections.emptyList();
    }

    private String getColumnListFromSelectableColumns() {
        List<DomainField> domainFields = getSelectableDomainFields();

        // loop over fields to create select clause
        return domainFields
                .stream()
                .map(d -> getFormattedColumnName(d.getName()))
                .collect(Collectors.joining(", "));
    }

    protected String getFullFinalTableColumnName(String camelCaseColumnName) {
        return getFullTableColumnName(getFinalTableName(), camelCaseColumnName);
    }

    protected String getFullTempTableColumnName(String camelCaseColumnName) {
        return getFullTableColumnName(getTemporaryTableName(), camelCaseColumnName);
    }

    protected String getFullTableColumnName(String tableName, String camelCaseColumnName) {
        return String.format("%s.%s",
                tableName,
                CaseFormat.UPPER_CAMEL.to(
                        CaseFormat.LOWER_UNDERSCORE,
                        camelCaseColumnName));
    }

    protected String getFormattedColumnName(String camelCaseName) {
        return CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                camelCaseName);
    }

    private String getSelectCoalesceQuery(String column, String defaultValue) {
        // e.g. "coalesce(delete, 'false')"
        String formattedColumnName = getFullTempTableColumnName(column);
        return String.format("coalesce(%s, %s)", formattedColumnName, defaultValue);
    }

    private String getSelectCaseQuery(String column, String expectedValue, String value, String defaultValue) {
        // e.g. "case when entity_temp.memo = ' ' then '' else <value> end"
        String formattedColumnName = getFullTempTableColumnName(column);
        return String.format("case when %s = %s then %s else %s end",
                formattedColumnName,
                expectedValue,
                value,
                defaultValue);
    }

    private String getUpdateCoalesceAssign(String column) {
        // e.g. "memo = coalesce(entity_temp.memo, entity.memo)"
        String formattedColumnName = getFullFinalTableColumnName(column);
        return String.format("%s = coalesce(%s, %s)",
                getFormattedColumnName(column),
                getFullTempTableColumnName(column),
                formattedColumnName);
    }

    private String getUpdateNullableStringCaseCoalesceAssign(String column) {
        // e.g. "case when entity_temp.memo = ' ' then '' else coalesce(entity_temp.memo, entity.memo) end"
        String finalFormattedColumnName = getFullFinalTableColumnName(column);
        String tempFormattedColumnName = getFullTempTableColumnName(column);
        return String.format(
                "%s = case when %s = %s then '' else coalesce(%s, %s) end",
                getFormattedColumnName(column),
                tempFormattedColumnName,
                RESERVED_CHAR,
                tempFormattedColumnName,
                finalFormattedColumnName);
    }

    private String getDoNothingConflictClause() {
        return getConflictClause("nothing");
    }

    private Set<Field> getAttributes() {
        if (attributes == null) {
            attributes = Arrays.stream(metaModelClass.getDeclaredFields())
                    // get SingularAttributes which are both static and volatile
                    .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isVolatile(f.getModifiers()))
                    .collect(Collectors.toSet());
        }

        return attributes;
    }

    private List<DomainField> getSelectableDomainFields() {
        List<DomainField> domainFields;
        if (CollectionUtils.isEmpty(getSelectableColumns())) {
            // extract fields using reflection
            domainFields = getAttributes().stream()
                    .map(f -> new DomainField(extractJavaType(f), f.getName()))
                    .collect(Collectors.toList());
        } else {
            // use provided fields
            domainFields = getSelectableColumns().stream()
                    .map(a -> new DomainField(a.getType().getJavaType(), a.getName()))
                    .collect(Collectors.toList());
        }

        // ensure columns are in order to avoid inconsistencies with db but also improve readability
        Collections.sort(domainFields, DOMAIN_FIELD_COMPARATOR);
        return domainFields;
    }

    private String getSelectableFieldsClause() {
        List<DomainField> domainFields = getSelectableDomainFields();

        // loop over fields to create select clause
        List<String> selectableFields = new ArrayList<>();
        domainFields.forEach(d -> selectableFields
                .add(getDefaultColumnSelectQuery(d.getType(), d.getName())));

        return StringUtils.join(selectableFields, ", ");
    }

    private Type extractJavaType(Field field) {
        Type[] parameterizedTypes = ((ParameterizedType) field
                .getGenericType())
                .getActualTypeArguments(); // [<domainType>, <attributeClass>]
        return parameterizedTypes[1];
    }

    private String getDefaultColumnSelectQuery(Type attributeType, String attributeName) {
        // get column custom select implementations
        String columnSelectQuery = getAttributeSelectQuery(attributeName);
        if (!StringUtils.isEmpty(columnSelectQuery)) {
            return columnSelectQuery;
        }

        // default implementations per type
        if (attributeType == String.class) {
            return getStringColumnTypeSelect(attributeName);
        } else {
            return getFullTempTableColumnName(attributeName);
        }
    }

    private String getStringColumnTypeSelect(String attributeName) {
        // String columns need extra logic since their default value and empty value are both serialized as null
        // Domain serializer adds a special scenario to set a placeholder for an empty. Null is null
        if (isNullableColumn(attributeName)) {
            return getSelectCaseQuery(
                    attributeName,
                    RESERVED_CHAR,
                    EMPTY_STRING,
                    getSelectCoalesceQuery(attributeName, NULL_STRING));
        } else {
            return getSelectCaseQuery(
                    attributeName,
                    RESERVED_CHAR,
                    EMPTY_STRING,
                    getSelectCoalesceQuery(attributeName, EMPTY_STRING));
        }
    }

    private String getUpdateClause() {
        StringBuilder updateQueryBuilder = new StringBuilder();

        List<String> updateQueries = new ArrayList<>();
        List<DomainField> updatableAttributes = getAttributes().stream()
                .filter(x -> !getNonUpdatableColumns().contains(x.getName())) // filter out non-updatable fields
                .map(a -> new DomainField(extractJavaType(a), a.getName()))
                .collect(Collectors.toList());
        Collections.sort(updatableAttributes, DOMAIN_FIELD_COMPARATOR); // sort fields alphabetically
        updatableAttributes.forEach(d -> {
            String attributeUpdateQuery = "";
            if (d.getType() == String.class) {
                attributeUpdateQuery = getUpdateNullableStringCaseCoalesceAssign(d.getName());
            } else {
                attributeUpdateQuery = getUpdateCoalesceAssign(d.getName());
            }

            updateQueries.add(attributeUpdateQuery);
        });

        // sort and add update statements to string builder
        updateQueryBuilder.append(StringUtils.join(updateQueries, ", "));
        return updateQueryBuilder.toString();
    }

    private String getConflictClause(String action) {
        return String.format(
                " on conflict (%s) do %s",
                getConflictIdColumns()
                        .stream()
                        .map(this::getFormattedColumnName)
                        .collect(Collectors.joining(", ")),
                action);
    }

    @Data
    @AllArgsConstructor
    private class DomainField {
        private Type type;
        private String name;
    }
}
