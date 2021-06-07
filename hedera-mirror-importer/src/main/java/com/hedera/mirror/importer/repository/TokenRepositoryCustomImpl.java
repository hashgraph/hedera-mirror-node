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

import com.hedera.mirror.importer.domain.TokenId_;
import com.hedera.mirror.importer.domain.Token_;

@Component
@Named
@RequiredArgsConstructor
public class TokenRepositoryCustomImpl extends AbstractUpdatableDomainRepositoryCustom<Token_> {
    public static final String TABLE = "token";
    public static final String TEMP_TABLE = "token_temp";
    private static final List<String> conflictTargetColumns = List.of(TokenId_.TOKEN_ID);
    private static final List<String> nullableColumns = List.of(Token_.FREEZE_KEY, Token_.FREEZE_KEY_ED25519_HEX,
            Token_.KYC_KEY, Token_.KYC_KEY_ED25519_HEX, Token_.SUPPLY_KEY, Token_.SUPPLY_KEY_ED25519_HEX,
            Token_.WIPE_KEY, Token_.WIPE_KEY_ED25519_HEX);
    private static final List<SingularAttribute> updatableColumns = Lists.newArrayList(Token_.freezeKey,
            Token_.freezeKeyEd25519Hex, Token_.kycKey, Token_.kycKeyEd25519Hex, Token_.modifiedTimestamp, Token_.name,
            Token_.supplyKey, Token_.supplyKeyEd25519Hex, Token_.symbol, Token_.totalSupply, Token_.treasuryAccountId,
            Token_.wipeKey, Token_.wipeKeyEd25519Hex);

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
    private final List<SingularAttribute> selectableColumns = Lists.newArrayList(Token_.createdTimestamp,
            Token_.decimals, Token_.freezeDefault, Token_.freezeKey, Token_.freezeKeyEd25519Hex, Token_.initialSupply,
            Token_.kycKey, Token_.kycKeyEd25519Hex, Token_.modifiedTimestamp, Token_.name, Token_.supplyKey,
            Token_.supplyKeyEd25519Hex, Token_.symbol, Token_.tokenId, Token_.totalSupply, Token_.treasuryAccountId,
            Token_.wipeKey, Token_.wipeKeyEd25519Hex);

    @Override
    public String getInsertWhereClause() {
        return String.format(" where %s is not null ",
                getTableColumnName(getTemporaryTableName(), Token_.CREATED_TIMESTAMP));
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s  and %s is null",
                getTableColumnName(getTableName(), Token_.TOKEN_ID),
                getTableColumnName(getTemporaryTableName(), Token_.TOKEN_ID),
                getTableColumnName(getTemporaryTableName(), Token_.CREATED_TIMESTAMP));
    }

    @Override
    public List<SingularAttribute> getUpdatableColumns() {
        return updatableColumns;
    }

    @Override
    public boolean isNullableColumn(String columnName) {
        return nullableColumns.contains(columnName);
    }

    @Override
    public boolean shouldUpdateOnConflict() {
        return true;
    }
}
