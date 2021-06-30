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

import com.hedera.mirror.importer.domain.Entity_;
import com.hedera.mirror.importer.domain.NftId_;
import com.hedera.mirror.importer.domain.Nft_;
import com.hedera.mirror.importer.domain.TokenId_;
import com.hedera.mirror.importer.domain.Token_;

@Named
@Value
public class NftUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Nft_> {
    private static final String JOIN_TABLE = "entity";
    private final String finalTableName = "nft";
    private final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> v1ConflictIdColumns = List.of(NftId_.TOKEN_ID, NftId_.SERIAL_NUMBER);
    // createdTimestamp is needed for v2 schema compliance as it's used in index
    private final List<String> v2ConflictIdColumns = List.of(NftId_.TOKEN_ID, NftId_.SERIAL_NUMBER,
            Nft_.CREATED_TIMESTAMP);
    private final Set<String> nullableColumns = Set.of(Nft_.DELETED);
    private final Set<String> nonUpdatableColumns = Set.of(Nft_.CREATED_TIMESTAMP, Nft_.METADATA,
            NftId_.SERIAL_NUMBER, NftId_.TOKEN_ID);

    @Getter(lazy = true)
    // JPAMetaModelEntityProcessor does not expand embeddedId fields, as such they need to be explicitly referenced
    private final Set<SingularAttribute> selectableColumns = Set.of(Nft_.accountId, Nft_.createdTimestamp, Nft_.deleted,
            Nft_.modifiedTimestamp, Nft_.metadata, NftId_.serialNumber, NftId_.tokenId);

    @Override
    public String getInsertWhereClause() {
        StringBuilder insertWhereQueryBuilder = new StringBuilder();

        // optionally join with entity if present to allow for deleted reference if necessary
        insertWhereQueryBuilder
                .append(String.format(" right outer join %s on %s = %s", EntityUpsertQueryGenerator.TABLE,
                        getFullTempTableColumnName(NftId_.TOKEN_ID),
                        getFullTableColumnName(EntityUpsertQueryGenerator.TABLE, Entity_.ID)));

        // optionally join with token if present to allow for treasuryAccount reference
        insertWhereQueryBuilder
                .append(String.format(" right outer join %s on %s = %s", TokenUpsertQueryGenerator.TABLE,
                        getFullTempTableColumnName(NftId_.TOKEN_ID),
                        getFullTableColumnName(TokenUpsertQueryGenerator.TABLE, TokenId_.TOKEN_ID)));

        // ignore entries where nft created timestamp is noted
        insertWhereQueryBuilder.append(String.format(" where %s is not null",
                getFullTempTableColumnName(Nft_.CREATED_TIMESTAMP)));

        return insertWhereQueryBuilder.toString();
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s = %s and %s is not null",
                getFullFinalTableColumnName(NftId_.TOKEN_ID),
                getFullTempTableColumnName(NftId_.TOKEN_ID),
                getFullFinalTableColumnName(NftId_.SERIAL_NUMBER),
                getFullTempTableColumnName(NftId_.SERIAL_NUMBER),
                getFullFinalTableColumnName(Nft_.CREATED_TIMESTAMP));
    }

    @Override
    public String getAttributeSelectQuery(String attributeName) {
        if (attributeName.equalsIgnoreCase(Nft_.ACCOUNT_ID)) {
            return getSelectCoalesceQuery(
                    Nft_.ACCOUNT_ID,
                    getFullTableColumnName(TokenUpsertQueryGenerator.TABLE, Token_.TREASURY_ACCOUNT_ID));
//            String treasuryAccountInsert = "coalesce() case when %s is not null then %s when" +
//                    " %s is null then null else %s end %s";
//            String treasuryAccountInsert = "case when %s is not null then %s when" +
//                    " %s is null then null else %s end %s";
//            return String.format(treasuryAccountInsert,
//                    getFullTempTableColumnName(Nft_.DELETED),
//                    getFullTempTableColumnName(Nft_.DELETED),
//                    getFullTableColumnName(JOIN_TABLE, Entity_.DELETED),
//                    getFullTableColumnName(JOIN_TABLE, Entity_.DELETED),
//                    getFormattedColumnName(Nft_.DELETED));
        } else if (attributeName.equalsIgnoreCase(Nft_.DELETED)) {
            return getSelectCoalesceQuery(
                    Nft_.DELETED,
                    getFullTableColumnName(EntityUpsertQueryGenerator.TABLE, Entity_.DELETED));
        }

        return null;
    }
}
