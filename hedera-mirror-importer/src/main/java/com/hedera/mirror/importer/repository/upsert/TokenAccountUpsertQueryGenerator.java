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
import lombok.Value;

import com.hedera.mirror.importer.db.FlywayProperties;
import com.hedera.mirror.importer.domain.TokenAccountId_;
import com.hedera.mirror.importer.domain.TokenAccount_;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.Token_;

@Named
@Value
public class TokenAccountUpsertQueryGenerator extends AbstractUpsertQueryGenerator<TokenAccount_> {
    private static final String JOIN_TABLE = "token";
    private final String finalTableName = "token_account";
    private final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> v1ConflictIdColumns = List.of(TokenAccountId_.TOKEN_ID, TokenAccountId_.ACCOUNT_ID);
    // createdTimestamp is needed for v2 schema compliance as it's used in index
    private final List<String> v2ConflictIdColumns = List.of(TokenAccountId_.TOKEN_ID,
            TokenAccountId_.ACCOUNT_ID, TokenAccount_.CREATED_TIMESTAMP);
    private final Set<String> nonUpdatableColumns = Set.of(TokenAccountId_.ACCOUNT_ID,
            TokenAccount_.CREATED_TIMESTAMP, TokenAccount_.ID, TokenAccountId_.TOKEN_ID);

    @Getter(lazy = true)
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(TokenAccountId_.accountId,
            TokenAccount_.associated, TokenAccount_.createdTimestamp, TokenAccount_.freezeStatus,
            TokenAccount_.kycStatus, TokenAccount_.modifiedTimestamp, TokenAccountId_.tokenId);

    public TokenAccountUpsertQueryGenerator(FlywayProperties flywayProperties) {
        super(flywayProperties);
    }

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
    public String getAttributeSelectQuery(String attributeName) {
        if (attributeName.equalsIgnoreCase(TokenAccount_.FREEZE_STATUS)) {
            String freezeStatusInsert = "case when %s is not null then %s when" +
                    " %s is null then %s when %s = true then %s else %s end %s";
            return String.format(freezeStatusInsert,
                    getFullTempTableColumnName(TokenAccount_.FREEZE_STATUS),
                    getFullTempTableColumnName(TokenAccount_.FREEZE_STATUS),
                    getFullTableColumnName(JOIN_TABLE, Token_.FREEZE_KEY),
                    TokenFreezeStatusEnum.NOT_APPLICABLE.getId(),
                    getFullTableColumnName(JOIN_TABLE, Token_.FREEZE_DEFAULT),
                    TokenFreezeStatusEnum.FROZEN.getId(),
                    TokenFreezeStatusEnum.UNFROZEN.getId(),
                    getFormattedColumnName(TokenAccount_.FREEZE_STATUS));
        } else if (attributeName.equalsIgnoreCase(TokenAccount_.KYC_STATUS)) {
            String kycInsert = "case when %s is not null then %s when %s is null then %s else %s end %s";
            return String.format(kycInsert,
                    getFullTempTableColumnName(TokenAccount_.KYC_STATUS),
                    getFullTempTableColumnName(TokenAccount_.KYC_STATUS),
                    getFullTableColumnName(JOIN_TABLE, Token_.KYC_KEY),
                    TokenKycStatusEnum.NOT_APPLICABLE.getId(),
                    TokenKycStatusEnum.REVOKED.getId(),
                    getFormattedColumnName(TokenAccount_.KYC_STATUS));
        }

        return null;
    }
}
