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

import com.google.common.collect.Lists;
import java.util.List;
import javax.inject.Named;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.hedera.mirror.importer.domain.TokenAccountId_;
import com.hedera.mirror.importer.domain.TokenAccount_;
import com.hedera.mirror.importer.domain.Token_;

@Component
@Named
@RequiredArgsConstructor
public class TokenAccountRepositoryCustomImpl extends AbstractUpdatableDomainRepositoryCustom<TokenAccount_> {
    public static final String TABLE = "token_account";
    public static final String TEMP_TABLE = "token_account_temp";
    private static final List<String> conflictTargetColumns = List.of(TokenAccountId_.TOKEN_ID,
            TokenAccountId_.ACCOUNT_ID);
    private static final List<String> nullableColumns = List.of();
    private static final List<SingularAttribute> updatableColumns = Lists.newArrayList(TokenAccount_.associated,
            TokenAccount_.modifiedTimestamp, TokenAccount_.freezeStatus, TokenAccount_.kycStatus);

    @Override
    public String getTableName() {
        return TABLE;
    }

    @Override
    public String getTemporaryTableName() {
        return TEMP_TABLE;
    }

    @Override
    public List<String> getConflictIdColumns() {
        return conflictTargetColumns;
    }

    @Getter(lazy = true)
    // using Lombok getter to implement getSelectableColumns, null or empty list implies select all fields
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final List<SingularAttribute> selectableColumns = Lists.newArrayList(TokenAccountId_.accountId,
            TokenAccount_.associated, TokenAccount_.createdTimestamp, TokenAccount_.freezeStatus,
            TokenAccount_.kycStatus, TokenAccount_.modifiedTimestamp, TokenAccountId_.tokenId);

    @Override
    public String getInsertWhereClause() {
        StringBuilder insertWhereQueryBuilder = new StringBuilder();

        // ignore entries where token not in db
        insertWhereQueryBuilder.append(String.format(" join %s on %s = %s", TokenRepositoryCustomImpl.TABLE,
                getTableColumnName(getTemporaryTableName(), Token_.TOKEN_ID),
                getTableColumnName(TokenRepositoryCustomImpl.TABLE, TokenAccountId_.TOKEN_ID)));

        // ignore entries where token not in db
        insertWhereQueryBuilder.append(String.format(" where %s is not null ",
                getTableColumnName(getTemporaryTableName(), TokenAccount_.CREATED_TIMESTAMP)));

        return insertWhereQueryBuilder.toString();
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s = %s and %s is null",
                getTableColumnName(getTableName(), TokenAccountId_.TOKEN_ID),
                getTableColumnName(getTemporaryTableName(), TokenAccountId_.TOKEN_ID),
                getTableColumnName(getTableName(), TokenAccountId_.ACCOUNT_ID),
                getTableColumnName(getTemporaryTableName(), TokenAccountId_.ACCOUNT_ID),
                getTableColumnName(getTemporaryTableName(), TokenAccount_.CREATED_TIMESTAMP));
    }

    @Override
    public List<SingularAttribute> getUpdatableColumns() {
        return updatableColumns;
    }

    @Override
    public boolean isNullableColumn(String columnName) {
        return nullableColumns.contains(columnName);
    }
}
