package com.hedera.mirror.importer.repository;

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
import java.util.stream.Collectors;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.converter.NullableStringSerializer;
import com.hedera.mirror.importer.domain.TokenAccountId_;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;

public abstract class AbstractUpdatableDomainRepositoryCustom<T> implements UpdatableDomainRepositoryCustom {
    private static final String EMPTY_STRING = "\'\'";
    private static final String NULL_STRING = "null";
    protected static final String RESERVED_CHAR = "\'" + NullableStringSerializer.NULLABLE_STRING_REPLACEMENT + "\'";

    private static final Comparator<Field> FIELD_COMPARATOR = Comparator
            .comparing(Field::getName);
    private static final Comparator<SingularAttribute> ATTRIBUTE_COMPARATOR = Comparator
            .comparing(SingularAttribute::getName);

    private final Class<T> metaModelClass = (Class<T>) new TypeToken<T>(getClass()) {
    }.getRawType();

    @Getter(lazy = true)
    private final String insertQuery = generateInsertQuery();

    // using Lombok getter to implement getSelectableColumns, null or empty list implies select all fields
    @Getter(lazy = true)
    // Note JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final List<SingularAttribute> selectableColumns = Collections.emptyList();

    @Getter(lazy = true)
    private final String updateQuery = generateUpdateQuery();

    protected final Logger log = LogManager.getLogger(getClass());

    @Override
    public String getCreateTempTableQuery() {
        return String.format("create temporary table %s on commit drop as table %s limit 0",
                getTemporaryTableName(), getTableName());
    }

    private String generateInsertQuery() {
        StringBuilder insertQueryBuilder = new StringBuilder("insert into " + getTableName());

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
        if (CollectionUtils.isEmpty(getUpdatableColumns())) {
            return "";
        }

        StringBuilder updateQueryBuilder = new StringBuilder("update " + getTableName() + " set ");

        updateQueryBuilder.append(getUpdateClause(getTemporaryTableName()));

        updateQueryBuilder.append(" from " + getTemporaryTableName());

        // insertable query
        updateQueryBuilder.append(getUpdateWhereClause());

        return updateQueryBuilder.toString();
    }

    private String getColumnListFromSelectableColumns() {
        if (CollectionUtils.isEmpty(getSelectableColumns())) {
            return getColumnListFromAllColumns();
        }

        return getSelectableColumns().stream()
                .sorted(ATTRIBUTE_COMPARATOR)
                .map(x -> getFormattedColumnName(x.getName()))
                .collect(Collectors.joining(", "));
    }

    private String getColumnListFromAllColumns() {
        return Arrays.stream(metaModelClass.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isVolatile(f.getModifiers()))
                .collect(Collectors.toList())
                .stream()
                .sorted(FIELD_COMPARATOR)
                .map(x -> getFormattedColumnName(x.getName())).collect(Collectors.joining(", "));
    }

    protected String getTableColumnName(String tableName, String camelCaseName) {
        return String.format("%s.%s",
                tableName,
                CaseFormat.UPPER_CAMEL.to(
                        CaseFormat.LOWER_UNDERSCORE,
                        camelCaseName));
    }

    protected String getFormattedColumnName(String camelCaseName) {
        return CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                camelCaseName);
    }

    private String getSelectCoalesceQuery(String column, String defaultValue) {
        // e.g. "coalesce(delete, 'false')"
        String formattedColumnName = getTableColumnName(getTemporaryTableName(), column);
        return String.format("coalesce(%s, %s)", formattedColumnName, defaultValue);
    }

    private String getSelectCoalesceQueryWithDefaultAlias(String column, String defaultValue) {
        // e.g. "coalesce(delete, 'false') as deleted"
        String formattedColumnName = getTableColumnName(getTemporaryTableName(), column);
        return String.format("coalesce(%s, %s) as %s",
                formattedColumnName,
                defaultValue,
                getFormattedColumnName(column));
    }

    private String getSelectCaseQuery(String column, String expectedValue, String value, String defaultValue) {
        // e.g. "coalesce(delete, 'false') as deleted"
        String formattedColumnName = getTableColumnName(getTemporaryTableName(), column);
        return String.format("case when %s = %s then %s else %s end",
                formattedColumnName,
                expectedValue,
                value,
                defaultValue);
    }

    private String getUpsertConflictCoalesceAssign(String column, String tempTableName) {
        // e.g. "memo = coalesce(excluded.memo, entity.memo)"
        String formattedColumnName = getTableColumnName(getTableName(), column);
        return String.format("%s = coalesce(%s, %s)",
                getFormattedColumnName(column),
                getTableColumnName(tempTableName, column),
                formattedColumnName);
    }

    private String getUpsertNullableStringConflictCaseCoalesceAssign(String column,
                                                                     String tempTableName) {
        // e.g. "case when excluded.memo = ' ' then '' else coalesce(excluded.memo, entity.memo) end "
        String finalFormattedColumnName = getTableColumnName(getTableName(), column);
        String tempFormattedColumnName = getTableColumnName(getTemporaryTableName(), column);
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

    private String getSelectableFieldsClause() {
        List<SingularAttribute> selectableAttributes = getSelectableColumns();
        if (CollectionUtils.isEmpty(selectableAttributes)) {
            return getSelectableFieldsThroughReflection();
        } else {
            Collections.sort(selectableAttributes, ATTRIBUTE_COMPARATOR);
            return getSelectableFieldsFromDomainModel(selectableAttributes);
        }
    }

    private String getSelectableFieldsFromDomainModel(List<SingularAttribute> selectableAttributes) {
        List<String> selectableColumns = new ArrayList<>();
        selectableAttributes.forEach(attribute -> {
            selectableColumns.add(getColumnSelectQuery(attribute.getType().getJavaType(), attribute.getName()));
        });

        // sort and add update statements to string builder
        return StringUtils.join(selectableColumns, ", ");
    }

    private String getSelectableFieldsThroughReflection() {
        List<Field> entityFields = Arrays.stream(metaModelClass.getDeclaredFields())
                // get SingularAttributes which are both static and volatile
                .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isVolatile(f.getModifiers()))
                .collect(Collectors.toList());

        Collections.sort(entityFields, FIELD_COMPARATOR); // sort fields alphabetically

        // add selection for columns
        List<String> selectQueries = new ArrayList<>();
        entityFields.forEach(field -> {
            String selectQuery = "";
            Type[] parameterizedTypes = ((ParameterizedType) field
                    .getGenericType())
                    .getActualTypeArguments(); // [<domainType>, <attributeClass>]
            Type attributeClass = parameterizedTypes[1];
            selectQuery = getColumnSelectQuery(
                    attributeClass,
                    field.getName());

            selectQueries.add(selectQuery);
        });

        // add select statements to string builder
        return StringUtils.join(selectQueries, ", ");
    }

    private String getColumnSelectQuery(Type attributeType, String attributeName) {
        if (attributeType == String.class) {
            return getStringColumnTypeSelect(attributeName);
        } else if (attributeType == TokenFreezeStatusEnum.class) {
            return getTokenFreezeEnumColumnTypeSelect(attributeName);
        } else if (attributeType == TokenKycStatusEnum.class) {
            return getTokenKycEnumColumnTypeSelect(attributeName);
        } else {
            return getTableColumnName(getTemporaryTableName(), attributeName);
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

    private String getTokenFreezeEnumColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            String freezeValue = String.format(
                    "getNewAccountFreezeStatus(%s)",
                    getTableColumnName(getTemporaryTableName(), TokenAccountId_.TOKEN_ID));
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, freezeValue);
        }
    }

    private String getTokenKycEnumColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            String kycValue = String.format(
                    "getNewAccountKycStatus(%s)",
                    getTableColumnName(getTemporaryTableName(), TokenAccountId_.TOKEN_ID));
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, kycValue);
        }
    }

    private String getUpdateClause(String tempTableName) {
        StringBuilder updateQueryBuilder = new StringBuilder();

        List<String> updateQueries = new ArrayList<>();
        List<SingularAttribute> updatableAttributes = getUpdatableColumns();
        Collections.sort(updatableAttributes, ATTRIBUTE_COMPARATOR); // sort fields alphabetically
        updatableAttributes.forEach(attribute -> {
            String updateQuery = "";
            if (attribute.getType().getJavaType() == String.class) {
                updateQuery = getUpsertNullableStringConflictCaseCoalesceAssign(
                        attribute.getName(),
                        tempTableName);
            } else {
                updateQuery = getUpsertConflictCoalesceAssign(attribute.getName(), tempTableName);
            }

            updateQueries.add(updateQuery);
        });

        // sort and add update statements to string builder
        updateQueryBuilder.append(StringUtils.join(updateQueries, ", "));
        return updateQueryBuilder.toString();
    }

    private String getConflictClause(String action) {
        return String.format(
                " on conflict (%s) do %s",
                getConflictIdColumns().stream().map(x -> getFormattedColumnName(x)).collect(Collectors.joining(", ")),
                action);
    }
}
