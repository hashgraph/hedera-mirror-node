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

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.NftId_;
import com.hedera.mirror.importer.domain.Nft_;
import com.hedera.mirror.importer.domain.TokenId_;

@Named
@Value
public class NftUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Nft_> {
    private final String finalTableName = "nft";
    private final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> v1ConflictIdColumns = List.of(NftId_.TOKEN_ID, NftId_.SERIAL_NUMBER);
    // createdTimestamp is needed for v2 schema compliance as it's used in index
    private final List<String> v2ConflictIdColumns = List.of(NftId_.TOKEN_ID, NftId_.SERIAL_NUMBER,
            Nft_.CREATED_TIMESTAMP);
    private final Set<String> nullableColumns = Set.of(Nft_.ACCOUNT_ID);
    private final Set<String> nonUpdatableColumns = Set.of(Nft_.CREATED_TIMESTAMP, Nft_.ID, Nft_.METADATA,
            NftId_.SERIAL_NUMBER, NftId_.TOKEN_ID);
    private static final String RESERVED_ENTITY_ID = EntityId.EMPTY.getId().toString();

    @Getter(lazy = true)
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(Nft_.accountId, Nft_.createdTimestamp, Nft_.deleted,
            Nft_.modifiedTimestamp, Nft_.metadata, NftId_.serialNumber, NftId_.tokenId);

    @Override
    public String getInsertWhereClause() {
        StringBuilder insertWhereQueryBuilder = new StringBuilder();

        // ignore entries where token not present
        insertWhereQueryBuilder
                .append(String.format(" join %s on %s = %s where %s is not null",
                        TokenUpsertQueryGenerator.TABLE,
                        getFullTempTableColumnName(NftId_.TOKEN_ID),
                        getFullTableColumnName(TokenUpsertQueryGenerator.TABLE, TokenId_.TOKEN_ID),
                        getFullTempTableColumnName(Nft_.CREATED_TIMESTAMP)));

        return insertWhereQueryBuilder.toString();
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s is null and %s = %s and %s = %s",
                getFullTempTableColumnName(Nft_.CREATED_TIMESTAMP),
                getFullFinalTableColumnName(NftId_.TOKEN_ID),
                getFullTempTableColumnName(NftId_.TOKEN_ID),
                getFullFinalTableColumnName(NftId_.SERIAL_NUMBER),
                getFullTempTableColumnName(NftId_.SERIAL_NUMBER));
    }

    @Override
    public String getAttributeUpdateQuery(String attributeName) {
        if (attributeName.equalsIgnoreCase(Nft_.ACCOUNT_ID)) {
            return String.format(
                    "%s = case when %s = true then null else coalesce(%s, %s) end",
                    getFormattedColumnName(Nft_.ACCOUNT_ID),
                    getFullTempTableColumnName(Nft_.DELETED),
                    getFullTempTableColumnName(Nft_.ACCOUNT_ID),
                    getFullFinalTableColumnName(Nft_.ACCOUNT_ID));
        } else if (attributeName.equalsIgnoreCase(Nft_.DELETED)) {
            return String.format(
                    "%s = coalesce(%s, %s)",
                    getFormattedColumnName(Nft_.DELETED),
                    getFullTempTableColumnName(Nft_.DELETED),
                    getFullFinalTableColumnName(Nft_.DELETED));
        }

        return null;
    }
}
