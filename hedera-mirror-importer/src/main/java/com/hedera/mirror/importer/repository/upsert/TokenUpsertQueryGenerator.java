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
import lombok.Value;

import com.hedera.mirror.importer.domain.TokenId_;
import com.hedera.mirror.importer.domain.Token_;

@Named
@RequiredArgsConstructor
@Value
public class TokenUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Token_> {
    public static final String TABLE = "token";
    public final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> conflictIdColumns = List.of(TokenId_.TOKEN_ID);
    private final Set<String> nullableColumns = Set.of(Token_.FREEZE_KEY, Token_.FREEZE_KEY_ED25519_HEX,
            Token_.KYC_KEY, Token_.KYC_KEY_ED25519_HEX, Token_.SUPPLY_KEY, Token_.SUPPLY_KEY_ED25519_HEX,
            Token_.WIPE_KEY, Token_.WIPE_KEY_ED25519_HEX);
    private final Set<String> nonUpdatableColumns = Set.of(Token_.CREATED_TIMESTAMP, Token_.DECIMALS,
            Token_.FREEZE_DEFAULT, Token_.INITIAL_SUPPLY, Token_.TOKEN_ID);

    @Getter(lazy = true)
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(Token_.createdTimestamp,
            Token_.decimals, Token_.freezeDefault, Token_.freezeKey, Token_.freezeKeyEd25519Hex, Token_.initialSupply,
            Token_.kycKey, Token_.kycKeyEd25519Hex, Token_.modifiedTimestamp, Token_.name,
            Token_.supplyKey, Token_.supplyKeyEd25519Hex, Token_.symbol, Token_.tokenId,
            Token_.totalSupply, Token_.treasuryAccountId, Token_.wipeKey, Token_.wipeKeyEd25519Hex);

    @Override
    public String getFinalTableName() {
        return TABLE;
    }

    @Override
    public String getInsertWhereClause() {
        return String.format(" where %s is not null ",
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
