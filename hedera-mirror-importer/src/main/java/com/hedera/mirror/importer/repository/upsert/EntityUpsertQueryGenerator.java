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
import lombok.Value;

import com.hedera.mirror.importer.domain.Entity_;

@Named
@Value
public class EntityUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Entity_> {
    private final String finalTableName = "entity";
    private final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> v1ConflictIdColumns = List.of(Entity_.ID);
    private final Set<String> nullableColumns = Set.of(Entity_.AUTO_RENEW_ACCOUNT_ID,
            Entity_.AUTO_RENEW_PERIOD, Entity_.CREATED_TIMESTAMP, Entity_.DELETED, Entity_.EXPIRATION_TIMESTAMP,
            Entity_.KEY, Entity_.MAX_AUTOMATIC_TOKEN_ASSOCIATIONS, Entity_.MODIFIED_TIMESTAMP, Entity_.PUBLIC_KEY,
            Entity_.PROXY_ACCOUNT_ID, Entity_.SUBMIT_KEY, Entity_.RECEIVER_SIG_REQUIRED);
    private final Set<String> nonUpdatableColumns = Set.of(Entity_.CREATED_TIMESTAMP, Entity_.ID,
            Entity_.NUM, Entity_.REALM, Entity_.SHARD, Entity_.TYPE);

    @Override
    public String getInsertWhereClause() {
        return "";
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s is null",
                getFullFinalTableColumnName(Entity_.ID),
                getFullTempTableColumnName(Entity_.ID),
                getFullTempTableColumnName(Entity_.CREATED_TIMESTAMP));
    }
}
