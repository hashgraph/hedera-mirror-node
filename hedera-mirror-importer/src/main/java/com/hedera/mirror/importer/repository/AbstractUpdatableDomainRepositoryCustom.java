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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.metamodel.SingularAttribute;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.utils.CollectionUtils;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Entity_;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Schedule_;
import com.hedera.mirror.importer.domain.TokenAccountId_;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;

public abstract class AbstractUpdatableDomainRepositoryCustom<T> implements UpdatableDomainRepositoryCustom {
    private static final String EXCLUDED_TABLE = "excluded";
    private static final String EMPTY_STRING = "\'\'";
    private static final String NULL_STRING = "null";
    private static final String RESERVED_SPACE_CHAR = "\' \'";
    private static final Comparator<Field> FIELD_COMPARATOR = Comparator
            .comparing(Field::getName);
    private static final Comparator<SingularAttribute> ATTRIBUTE_COMPARATOR = Comparator
            .comparing(SingularAttribute::getName);
    private String upsertQuery = null;
    private final Class<T> metaModelClass = (Class<T>) new TypeToken<T>(getClass()) {
    }.getRawType();
    protected final Logger log = LogManager.getLogger(getClass());
//    private final Class<T> domainClass = (Class<T>) metaModelClass.getAnnotation(StaticMetamodel.class).value();

    @Override
    public String getCreateTempTableQuery() {
        return String.format("create temporary table %s on commit drop as table %s limit 0",
                getTemporaryTableName(), getTableName());
    }

    @Override
    public String getUpsertQuery() {
        if (StringUtils.isNotEmpty(upsertQuery)) {
            return upsertQuery;
        }

        StringBuilder upsertQueryBuilder = new StringBuilder("insert into " + getTableName());

        // when a subset of columns are provided scope the columns to ensure data types line up with incoming select
        upsertQueryBuilder.append(" (");
        if (!CollectionUtils.isNullOrEmpty(getSelectableColumns())) {
            upsertQueryBuilder.append(getListFromSelectableColumns());
        } else {
            upsertQueryBuilder.append(getListFromAllColumns());
        }
        upsertQueryBuilder.append(") ");

        upsertQueryBuilder.append("select ");

        upsertQueryBuilder.append(getSelectableFieldsClause());

        upsertQueryBuilder.append(" from " + getTemporaryTableName());

        upsertQueryBuilder
                .append(shouldUpdateOnConflict() ? getDoUpdateConflictClause() : getDoNothingConflictClause());

        upsertQuery = upsertQueryBuilder.toString();
        return upsertQuery;
    }

    private static String getTableColumnName(String camelCaseName) {
        return CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                camelCaseName);
    }

    private String getSelectCoalesceQuery(String column, String defaultValue) {
        // e.g. "coalesce(delete, 'false')"
        String formattedColumnName = getTableColumnName(column);
        return String.format("coalesce(%s, %s)", formattedColumnName, defaultValue);
    }

    private String getSelectCoalesceQueryWithDefaultAlias(String column, String defaultValue) {
        // e.g. "coalesce(delete, 'false') as deleted"
        String formattedColumnName = getTableColumnName(column);
        return String.format("coalesce(%s, %s) as %s", formattedColumnName, defaultValue, formattedColumnName);
    }

    private static String getSelectCaseQuery(String column, String expectedValue, String value, String defaultValue) {
        // e.g. "coalesce(delete, 'false') as deleted"
        String formattedColumnName = getTableColumnName(column);
        return String.format("case when %s = %s then %s else %s end",
                formattedColumnName,
                expectedValue,
                value,
                defaultValue);
    }

    private static String getNullableStringCoalesceQuery(String column) {
        // e.g. "coalesce(memo, '') as memo"
        String formattedColumnName = getTableColumnName(column);
        return String.format("coalesce(%s, '') as %s", formattedColumnName, formattedColumnName);
    }

    private static String getUpsertConflictCoalesceAssign(String tableName, String column) {
        // e.g. "memo = coalesce(excluded.memo, entity.memo)"
        String formattedColumnName = getTableColumnName(column);
        return String.format("%s = coalesce(%s.%s, %s.%s)", formattedColumnName, EXCLUDED_TABLE, formattedColumnName,
                tableName, formattedColumnName);
    }

    private static String getUpsertNullableStringConflictCaseCoalesceAssign(String tableName, String column) {
        // e.g. "case when excluded.memo = ' ' then '' else coalesce(excluded.memo, entity.memo) end "
        String formattedColumnName = getTableColumnName(column);
        return String.format(
                "%s = case when %s.%s = ' ' then '' else coalesce(%s.%s, %s.%s) end",
                formattedColumnName,
                EXCLUDED_TABLE,
                formattedColumnName,
                EXCLUDED_TABLE,
                formattedColumnName,
                tableName,
                formattedColumnName);
    }

    private String getDoNothingConflictClause() {
        return getConflictClause("nothing");
    }

    private String getSelectableFieldsClause() {
        List<SingularAttribute> selectableAttributes = getSelectableColumns();
        if (CollectionUtils.isNullOrEmpty(selectableAttributes)) {
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
            Type[] parameterizedTypes = ((ParameterizedTypeImpl) field
                    .getGenericType())
                    .getActualTypeArguments();
            Type domainType = parameterizedTypes[0];
            Type attributeClass = parameterizedTypes[1];
            selectQuery = getColumnSelectQuery(
//                    domainType,
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
        } else if (attributeType == Boolean.class) {
            return getBooleanColumnTypeSelect(attributeName);
        } else if (attributeType == byte[].class) {
            return getByteArrayColumnTypeSelect(attributeName);
        } else if (attributeType == EntityId.class) {
            return getEntityIdColumnTypeSelect(attributeName);
        } else if (attributeType == Long.class) {
            return getLongColumnTypeSelect(attributeName);
        } else if (attributeType == TokenFreezeStatusEnum.class) {
            return getTokenFreezeEnumColumnTypeSelect(attributeName);
        } else if (attributeType == TokenKycStatusEnum.class) {
            return getTokenKycEnumColumnTypeSelect(attributeName);
        } else {
            return getTableColumnName(attributeName);
        }
    }

    private String getTokenFreezeEnumColumnTypeSelect(String attributeName) {
        // String columns need extra logic since their default value and empty value are both serialized as null
        // Domain serializer adds a special scenario to set ' ' as the placeholder for an empty. Null is null
        if (isNullableColumn(attributeName)) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            String freezeValue = String.format(
                    "getNewAccountFreezeStatus(%s.%s)",
                    getTemporaryTableName(),
                    getTableColumnName(TokenAccountId_.TOKEN_ID));
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, freezeValue);
        }
    }

    private String getTokenKycEnumColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            String kycValue = String.format(
                    "getNewAccountKycStatus(%s.%s)",
                    getTemporaryTableName(),
                    getTableColumnName(TokenAccountId_.TOKEN_ID));
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, kycValue);
        }
    }

    private String getStringColumnTypeSelect(String attributeName) {
        // String columns need extra logic since their default value and empty value are both serialized as null
        // Domain serializer adds a special scenario to set ' ' as the placeholder for an empty. Null is null
        if (isNullableColumn(attributeName)) {
            return getSelectCaseQuery(
                    attributeName,
                    RESERVED_SPACE_CHAR,
                    EMPTY_STRING,
                    getSelectCoalesceQuery(attributeName, NULL_STRING));
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            return getSelectCaseQuery(
                    attributeName,
                    RESERVED_SPACE_CHAR,
                    EMPTY_STRING,
                    getSelectCoalesceQuery(attributeName, EMPTY_STRING));
        }
    }

    private String getBooleanColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) { // && domainType == domainClass) {
            return getSelectCoalesceQuery(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            return getSelectCoalesceQuery(attributeName, "FALSE");
        }
    }

    private String getByteArrayColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) { // && domainType == domainClass) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, "E'\\'\\''::bytea");
        }
    }

    private String getEntityIdColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, "1");
        }
    }

    private String getLongColumnTypeSelect(String attributeName) {
        if (isNullableColumn(attributeName)) {
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, NULL_STRING);
        } else {
            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            return getSelectCoalesceQueryWithDefaultAlias(attributeName, "0");
        }
    }

    private String getInsertColumnSelect(SingularAttribute attribute) {
        // nullable Strings
        if (attribute.getType().getJavaType() == String.class) {
            if (attribute.getDeclaringType().getJavaType() == Entity.class && attribute.getName() == Entity_.MEMO) {
                return getSelectCoalesceQueryWithDefaultAlias(attribute.getName(), EMPTY_STRING);
            }

            return getTableColumnName(attribute.getName());
//            return "'' as " + getTableColumnName(attribute.getName());
        }
        // nullable boolean
        else if (attribute.getType().getJavaType() == Boolean.class) {
            return getSelectCoalesceQueryWithDefaultAlias(attribute.getName(), NULL_STRING);
//            return "null as " + getTableColumnName(attribute.getName());
        } else if (attribute.getType().getJavaType() == Long.class) {
            if (attribute.getDeclaringType().getJavaType() == Schedule.class &&
                    attribute.getName() == Schedule_.EXECUTED_TIMESTAMP) {
                return getSelectCoalesceQueryWithDefaultAlias(attribute.getName(), NULL_STRING);
            }

            // non-applicable for insert but needed to prevent "violates not-null constraint" error on updates
            return getSelectCoalesceQueryWithDefaultAlias(attribute.getName(), "0");
        } else {
            return getTableColumnName(attribute.getName());
        }

//        new SingularAttributeImpl<Entity_, String>()
//        Attribute JavaType, Name, DeclaringType i.e <<Class, String>, Object>
    }

    private String getListFromSelectableColumns() {
        return getSelectableColumns().stream()
                .sorted(ATTRIBUTE_COMPARATOR)
                .map(x -> getTableColumnName(x.getName()))
                .collect(Collectors.joining(", "));
    }

    private String getListFromAllColumns() {
        return Arrays.stream(metaModelClass.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isVolatile(f.getModifiers()))
                .collect(Collectors.toList())
                .stream()
                .sorted(FIELD_COMPARATOR)
                .map(x -> getTableColumnName(x.getName())).collect(Collectors.joining(", "));
    }

    private String getDoUpdateConflictClause() {
        StringBuilder updateQueryBuilder = new StringBuilder(getConflictClause("update set"));

        List<String> updateQueries = new ArrayList<>();
        List<SingularAttribute> updatableAttributes = getUpdatableColumns();
        Collections.sort(updatableAttributes, ATTRIBUTE_COMPARATOR); // sort fields alphabetically
        updatableAttributes.forEach(attribute -> {
            String updateQuery = "";
            if (attribute.getType().getJavaType() == String.class) {
                updateQuery = getUpsertNullableStringConflictCaseCoalesceAssign(getTableName(), attribute.getName());
            } else {
                updateQuery = getUpsertConflictCoalesceAssign(getTableName(), attribute.getName());
            }

            updateQueries.add(updateQuery);
        });

        // sort and add update statements to string builder
        updateQueryBuilder.append(StringUtils.join(updateQueries, ", "));
        return updateQueryBuilder.toString();
    }

    private String getConflictClause(String action) {
        return String.format(
                " on conflict (%s) do %s ",
                getConflictIdColumns().stream().map(x -> getTableColumnName(x)).collect(Collectors.joining(", ")),
                action);
    }
}
