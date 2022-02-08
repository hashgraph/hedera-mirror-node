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

import java.util.List;
import java.util.Set;
import javax.inject.Named;
import javax.persistence.metamodel.SingularAttribute;
import lombok.Getter;
import lombok.Value;

import com.hedera.mirror.common.domain.token.TokenId_;
import com.hedera.mirror.common.domain.token.Token_;

@Named
@Value
public class TokenUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Token_> {
    public static final String TABLE = "token";
    public final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> conflictIdColumns = List.of(TokenId_.TOKEN_ID);
    private final Set<String> nullableColumns = Set.of(Token_.FEE_SCHEDULE_KEY, Token_.FREEZE_KEY, Token_.KYC_KEY,
            Token_.PAUSE_KEY, Token_.PAUSE_STATUS, Token_.SUPPLY_KEY, Token_.WIPE_KEY);
    private final Set<String> nonUpdatableColumns = Set.of(Token_.CREATED_TIMESTAMP, Token_.DECIMALS,
            Token_.FREEZE_DEFAULT, Token_.INITIAL_SUPPLY, Token_.MAX_SUPPLY, Token_.SUPPLY_TYPE, Token_.TOKEN_ID,
            Token_.TYPE);

    @Getter(lazy = true)
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(Token_.createdTimestamp, Token_.decimals,
            Token_.feeScheduleKey, Token_.freezeDefault, Token_.freezeKey,
            Token_.initialSupply, Token_.kycKey, Token_.maxSupply,
            Token_.modifiedTimestamp, Token_.name, Token_.pauseKey, Token_.pauseStatus,
            Token_.supplyKey, Token_.supplyType, Token_.symbol, Token_.tokenId,
            Token_.totalSupply, Token_.treasuryAccountId, Token_.type, Token_.wipeKey);

    @Override
    protected String getAttributeUpdateQuery(String attributeName) {
        if (attributeName.equalsIgnoreCase(Token_.TOTAL_SUPPLY)) {
            return String.format("%s = case when %s >= 0 then %s else %s + coalesce(%s, 0) end",
                    getFormattedColumnName(Token_.TOTAL_SUPPLY),
                    getFullTempTableColumnName(Token_.TOTAL_SUPPLY),
                    getFullTempTableColumnName(Token_.TOTAL_SUPPLY),
                    getFullFinalTableColumnName(Token_.TOTAL_SUPPLY),
                    getFullTempTableColumnName(Token_.TOTAL_SUPPLY)
            );
        }

        return super.getAttributeUpdateQuery(attributeName);
    }

    @Override
    public String getFinalTableName() {
        return TABLE;
    }

    @Override
    public String getInsertWhereClause() {
        return String.format(" where %s is not null",
                getFullTempTableColumnName(Token_.CREATED_TIMESTAMP));
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s is null",
                getFullFinalTableColumnName(Token_.TOKEN_ID),
                getFullTempTableColumnName(Token_.TOKEN_ID),
                getFullTempTableColumnName(Token_.CREATED_TIMESTAMP));
    }
}
