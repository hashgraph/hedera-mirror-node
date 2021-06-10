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

import com.google.common.collect.Lists;
import java.util.List;
import javax.inject.Named;
import javax.persistence.metamodel.SingularAttribute;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.Entity_;

@Named
@RequiredArgsConstructor
public class EntityUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Entity_> {
    public static final String TABLE = "entity";
    public static final String TEMP_TABLE = TABLE + "_temp";
    private static final List<String> conflictTargetColumns = List.of(Entity_.ID);
    private static final List<String> nullableColumns = List.of(Entity_.AUTO_RENEW_ACCOUNT_ID,
            Entity_.AUTO_RENEW_PERIOD, Entity_.CREATED_TIMESTAMP, Entity_.DELETED, Entity_.EXPIRATION_TIMESTAMP,
            Entity_.KEY, Entity_.MODIFIED_TIMESTAMP, Entity_.PUBLIC_KEY, Entity_.PROXY_ACCOUNT_ID, Entity_.SUBMIT_KEY);
    private static final List<SingularAttribute> updatableColumns = Lists.newArrayList(Entity_.autoRenewAccountId,
            Entity_.autoRenewPeriod, Entity_.deleted, Entity_.expirationTimestamp, Entity_.key, Entity_.memo,
            Entity_.proxyAccountId, Entity_.publicKey, Entity_.submitKey);

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

    @Override
    public String getInsertWhereClause() {
        return "";
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s is null",
                getTableColumnName(getTableName(), Entity_.ID),
                getTableColumnName(getTemporaryTableName(), Entity_.ID),
                getTableColumnName(getTemporaryTableName(), Entity_.CREATED_TIMESTAMP));
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
