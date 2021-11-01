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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

@RequiredArgsConstructor
public abstract class AbstractUpsertQueryGenerator<T> implements UpsertQueryGenerator {
    protected static final String EMPTY_STRING = "''";
    private static final String EMPTY_CLAUSE = "";
    private static final String V1_DIRECTORY = "/v1";
    private static final String V2_DIRECTORY = "/v2";
    private static final Comparator<DomainField> DOMAIN_FIELD_COMPARATOR = Comparator.comparing(DomainField::getName);
    protected final Logger log = LogManager.getLogger(getClass());
    private final Class<T> metaModelClass = (Class<T>) new TypeToken<T>(getClass()) {
    }.getRawType();
    @Getter(lazy = true)
    private final String insertQuery = generateInsertQuery();
    private volatile Set<Field> attributes = null;
    @Getter(lazy = true)
    private final String updateQuery = generateUpdateQuery();
    @Value("${spring.flyway.locations:v1}")
    private String version;

    protected boolean isInsertOnly() {
        return false;
    }

    protected String getCteForInsert() {
        return EMPTY_CLAUSE;
    }

    protected abstract String getInsertWhereClause();

    protected Set<String> getNonUpdatableColumns() {
        return Collections.emptySet();
    }

    protected String getUpdateWhereClause() {
        return EMPTY_CLAUSE;
    }

    protected boolean needsOnConflictAction() {
        return true;
    }

    @Override
    public String getCreateTempTableQuery() {
        return String.format("create temporary table if not exists %s on commit drop as table %s limit 0",
                getTemporaryTableName(), getFinalTableName());
    }

    protected String getAttributeSelectQuery(Type attributeType, String attributeName) {
        // default implementations per type
        if (attributeType == String.class && !isNullableColumn(attributeName)) {
            return getSelectCoalesceQuery(attributeName, EMPTY_STRING);
        } else {
            return getFullTempTableColumnName(attributeName);
        }
    }

    protected String getAttributeUpdateQuery(String attributeName) {
        return null;
    }

    protected List<String> getV1ConflictIdColumns() {
        return Collections.emptyList();
    }

    protected List<String> getV2ConflictIdColumns() {
        return Collections.emptyList();
    }

    protected Set<String> getNullableColumns() {
        return Collections.emptySet();
    }

    // Note JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    protected Set<SingularAttribute> getSelectableColumns() {
        return Collections.emptySet();
    }

    private String generateInsertQuery() {
        StringBuilder insertQueryBuilder = new StringBuilder(StringUtils.joinWith(
                " ",
                getCteForInsert(),
                "insert into",
                getFinalTableName()
        ));

        // build target column list
        insertQueryBuilder.append(String.format(" (%s) select ", getColumnListFromSelectableColumns()));

        insertQueryBuilder.append(getSelectableFieldsClause());

        insertQueryBuilder.append(" from " + getTemporaryTableName());

        // insertable query
        insertQueryBuilder.append(getInsertWhereClause());

        if (needsOnConflictAction()) {
            insertQueryBuilder.append(getDoNothingConflictClause());
        }

        return insertQueryBuilder.toString();
    }

    private String generateUpdateQuery() {
        if (isInsertOnly()) {
            return EMPTY_CLAUSE;
        }

        return StringUtils.joinWith(
                " ",
                "update", getFinalTableName(), "set",
                getUpdateClause(),
                "from", getTemporaryTableName(),
                getUpdateWhereClause()
        );
    }

    protected final boolean isNullableColumn(String columnName) {
        return getNullableColumns() != null && getNullableColumns().contains(columnName);
    }

    private List<String> getConflictIdColumns() {
        if (version.contains(V1_DIRECTORY)) {
            return getV1ConflictIdColumns();
        } else if (version.contains(V2_DIRECTORY)) {
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

    protected String getSelectCoalesceQuery(String column, String defaultValue) {
        // e.g. "coalesce(delete, 'false')"
        String formattedColumnName = getFullTempTableColumnName(column);
        return String.format("coalesce(%s, %s)", formattedColumnName, defaultValue);
    }

    private String getUpdateCoalesceAssign(String column) {
        // e.g. "memo = coalesce(entity_temp.memo, entity.memo)"
        String formattedColumnName = getFullFinalTableColumnName(column);
        return String.format("%s = coalesce(%s, %s)",
                getFormattedColumnName(column),
                getFullTempTableColumnName(column),
                formattedColumnName);
    }

    private String getDoNothingConflictClause() {
        return getConflictClause("nothing");
    }

    private Set<Field> getAttributes() {
        if (attributes == null) {
            Set<Field> fields = new HashSet<>();
            ReflectionUtils.doWithFields(metaModelClass, field -> {
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isVolatile(field.getModifiers())) {
                    fields.add(field);
                }
            });
            attributes = fields;
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
        domainFields.forEach(d -> selectableFields.add(getAttributeSelectQuery(d.getType(), d.getName())));

        return StringUtils.join(selectableFields, ", ");
    }

    private Type extractJavaType(Field field) {
        Type[] parameterizedTypes = ((ParameterizedType) field
                .getGenericType())
                .getActualTypeArguments(); // [<domainType>, <attributeClass>]
        return parameterizedTypes[1];
    }

    private String getUpdateClause() {
        StringBuilder updateQueryBuilder = new StringBuilder();

        List<String> updateQueries = new ArrayList<>();
        List<DomainField> updatableAttributes = getAttributes().stream()
                .filter(x -> !getNonUpdatableColumns().contains(x.getName())) // filter out non-updatable fields
                .map(a -> new DomainField(extractJavaType(a), a.getName()))
                .sorted(DOMAIN_FIELD_COMPARATOR) // sort fields alphabetically
                .collect(Collectors.toList());
        updatableAttributes.forEach(d -> {
            String attributeUpdateQuery;
            // get column custom update implementations
            String columnSelectQuery = getAttributeUpdateQuery(d.getName());
            if (!StringUtils.isEmpty(columnSelectQuery)) {
                attributeUpdateQuery = columnSelectQuery;
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
    private static class DomainField {
        private Type type;
        private String name;
    }
}
