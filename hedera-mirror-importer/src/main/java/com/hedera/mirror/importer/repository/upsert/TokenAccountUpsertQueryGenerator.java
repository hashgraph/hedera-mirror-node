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

import java.lang.reflect.Type;
import java.util.Set;
import javax.inject.Named;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import lombok.Value;

import com.hedera.mirror.importer.domain.TokenAccountId_;
import com.hedera.mirror.importer.domain.TokenAccount_;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.Token_;

@Named
@Value
public class TokenAccountUpsertQueryGenerator extends AbstractUpsertQueryGenerator<TokenAccount_> {
    private static final String CTE_NAME = "last";
    private static final String JOIN_TABLE = "token";
    // id columns (account_id, token_id, and modified_timestamp) are set directly. freeze_status / kyc_status require
    // special care. Other columns are set by coalescing the temp table column and the last association column
    private static final Set<String> COALESCE_COLUMNS = Set.of(TokenAccount_.ASSOCIATED,
            TokenAccount_.AUTOMATIC_ASSOCIATION, TokenAccount_.CREATED_TIMESTAMP);
    private final String finalTableName = "token_account";
    private final String temporaryTableName = getFinalTableName() + "_temp";

    @Getter(lazy = true)
    @SuppressWarnings("java:S3740")
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(TokenAccountId_.accountId, TokenAccount_.associated,
            TokenAccount_.automaticAssociation, TokenAccount_.createdTimestamp, TokenAccount_.freezeStatus,
            TokenAccount_.kycStatus, TokenAccountId_.modifiedTimestamp, TokenAccountId_.tokenId);

    @Override
    protected boolean isInsertOnly() {
        return true;
    }

    @Override
    protected String getCteForInsert() {
        String finalTableAccountIdColumn = getFullFinalTableColumnName(TokenAccountId_.ACCOUNT_ID);
        String finalTableTokenIdColumn = getFullFinalTableColumnName(TokenAccountId_.TOKEN_ID);
        String finalTableModifiedTimestampColumn = getFullFinalTableColumnName(TokenAccountId_.MODIFIED_TIMESTAMP);
        return String.format("with %s as (" +
                        "  select distinct on (%s, %s) %s.*" +
                        "  from %s" +
                        "  join %s on %s = %s and %s = %s" +
                        "  order by %s, %s, %s desc)",
                CTE_NAME,
                finalTableAccountIdColumn, finalTableTokenIdColumn, finalTableName,
                finalTableName,
                temporaryTableName,
                getFullTempTableColumnName(TokenAccountId_.ACCOUNT_ID), finalTableAccountIdColumn,
                getFullTempTableColumnName(TokenAccountId_.TOKEN_ID), finalTableTokenIdColumn,
                finalTableAccountIdColumn, finalTableTokenIdColumn, finalTableModifiedTimestampColumn
        );
    }

    @Override
    protected String getInsertWhereClause() {
        return String.format(" join %s on %s = %s" +
                        " left join %s on %s = %s and %s = %s and %s is true" +
                        " where %s is not null or %s is not null" +
                        " order by %s",
                TokenUpsertQueryGenerator.TABLE,
                getFullTempTableColumnName(TokenAccountId_.TOKEN_ID),
                getFullTableColumnName(TokenUpsertQueryGenerator.TABLE, Token_.TOKEN_ID),
                CTE_NAME,
                getFullTableColumnName(CTE_NAME, TokenAccountId_.ACCOUNT_ID),
                getFullTempTableColumnName(TokenAccountId_.ACCOUNT_ID),
                getFullTableColumnName(CTE_NAME, TokenAccountId_.TOKEN_ID),
                getFullTempTableColumnName(TokenAccountId_.TOKEN_ID),
                getFullTableColumnName(CTE_NAME, TokenAccount_.ASSOCIATED),
                getFullTempTableColumnName(TokenAccount_.CREATED_TIMESTAMP),
                getFullTableColumnName(CTE_NAME, TokenAccount_.CREATED_TIMESTAMP),
                getFullTempTableColumnName(TokenAccountId_.MODIFIED_TIMESTAMP));
    }

    @Override
    protected String getAttributeSelectQuery(Type attributeType, String attributeName) {
        if (attributeName.equalsIgnoreCase(TokenAccount_.FREEZE_STATUS)) {
            String freezeStatusInsert = "case when %s is not null then %s" +
                    "  when %s is not null then" +
                    "    case" +
                    "      when %s is null then %s" +
                    "      when %s is true then %s" +
                    "      else %s" +
                    "    end" +
                    "  else %s " +
                    "end %s";
            return String.format(freezeStatusInsert,
                    getFullTempTableColumnName(TokenAccount_.FREEZE_STATUS),
                    getFullTempTableColumnName(TokenAccount_.FREEZE_STATUS),
                    getFullTempTableColumnName(TokenAccount_.CREATED_TIMESTAMP),
                    getFullTableColumnName(JOIN_TABLE, Token_.FREEZE_KEY),
                    TokenFreezeStatusEnum.NOT_APPLICABLE.getId(),
                    getFullTableColumnName(JOIN_TABLE, Token_.FREEZE_DEFAULT),
                    TokenFreezeStatusEnum.FROZEN.getId(),
                    TokenFreezeStatusEnum.UNFROZEN.getId(),
                    getFullTableColumnName(CTE_NAME, TokenAccount_.FREEZE_STATUS),
                    getFormattedColumnName(TokenAccount_.FREEZE_STATUS));
        } else if (attributeName.equalsIgnoreCase(TokenAccount_.KYC_STATUS)) {
            String kycInsert = "case when %s is not null then %s" +
                    "  when %s is not null then" +
                    "    case" +
                    "      when %s is null then %s" +
                    "      else %s" +
                    "    end" +
                    "  else %s " +
                    "end %s";
            return String.format(kycInsert,
                    getFullTempTableColumnName(TokenAccount_.KYC_STATUS),
                    getFullTempTableColumnName(TokenAccount_.KYC_STATUS),
                    getFullTempTableColumnName(TokenAccount_.CREATED_TIMESTAMP),
                    getFullTableColumnName(JOIN_TABLE, Token_.KYC_KEY),
                    TokenKycStatusEnum.NOT_APPLICABLE.getId(),
                    TokenKycStatusEnum.REVOKED.getId(),
                    getFullTableColumnName(CTE_NAME, TokenAccount_.KYC_STATUS),
                    getFormattedColumnName(TokenAccount_.KYC_STATUS));
        } else if (COALESCE_COLUMNS.contains(attributeName)) {
            return String.format("coalesce(%s, %s)", getFullTempTableColumnName(attributeName),
                    getFullTableColumnName(CTE_NAME, attributeName));
        }

        return super.getAttributeSelectQuery(attributeType, attributeName);
    }

    @Override
    protected boolean needsOnConflictAction() {
        return false;
    }
}
