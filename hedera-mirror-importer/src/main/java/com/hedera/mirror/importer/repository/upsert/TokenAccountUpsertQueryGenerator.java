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

import java.util.List;
import java.util.Set;
import javax.inject.Named;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.TokenAccountId_;
import com.hedera.mirror.importer.domain.TokenAccount_;
import com.hedera.mirror.importer.domain.Token_;

@Named
@RequiredArgsConstructor
public class TokenAccountUpsertQueryGenerator extends AbstractUpsertQueryGenerator<TokenAccount_> {
    public static final String TABLE = "token_account";
    public static final String TEMP_TABLE = TABLE + "_temp";
    private static final List<String> conflictTargetColumns = List.of(TokenAccountId_.TOKEN_ID,
            TokenAccountId_.ACCOUNT_ID);
    private static final Set<String> nullableColumns = Set.of();
    private static final Set<SingularAttribute> updatableColumns = Set.of(TokenAccount_.associated,
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
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(TokenAccountId_.accountId,
            TokenAccount_.associated, TokenAccount_.createdTimestamp, TokenAccount_.freezeStatus,
            TokenAccount_.kycStatus, TokenAccount_.modifiedTimestamp, TokenAccountId_.tokenId);

    @Override
    public String getInsertWhereClause() {
        StringBuilder insertWhereQueryBuilder = new StringBuilder();

        // ignore entries where token not in db
        insertWhereQueryBuilder.append(String.format(" join %s on %s = %s", TokenUpsertQueryGenerator.TABLE,
                getFullTempTableColumnName(TokenAccountId_.TOKEN_ID),
                getFullTableColumnName(TokenUpsertQueryGenerator.TABLE, Token_.TOKEN_ID)));

        // ignore entries where token created timestamp is noted
        insertWhereQueryBuilder.append(String.format(" where %s is not null ",
                getFullTempTableColumnName(TokenAccount_.CREATED_TIMESTAMP)));

        return insertWhereQueryBuilder.toString();
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s = %s and %s is null",
                getFullFinalTableColumnName(TokenAccountId_.TOKEN_ID),
                getFullTempTableColumnName(TokenAccountId_.TOKEN_ID),
                getFullFinalTableColumnName(TokenAccountId_.ACCOUNT_ID),
                getFullTempTableColumnName(TokenAccountId_.ACCOUNT_ID),
                getFullTempTableColumnName(TokenAccount_.CREATED_TIMESTAMP));
    }

    @Override
    public Set<SingularAttribute> getUpdatableColumns() {
        return updatableColumns;
    }

    @Override
    public boolean isNullableColumn(String columnName) {
        return nullableColumns.contains(columnName);
    }
}
