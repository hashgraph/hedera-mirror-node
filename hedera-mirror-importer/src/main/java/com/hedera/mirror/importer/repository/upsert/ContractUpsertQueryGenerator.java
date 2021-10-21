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

import com.hedera.mirror.importer.domain.AbstractEntity_;
import com.hedera.mirror.importer.domain.Contract_;

@Named
@Value
public class ContractUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Contract_> {

    private final String finalTableName = "contract";
    private final String temporaryTableName = getFinalTableName() + "_temp";
    private final List<String> v1ConflictIdColumns = List.of(AbstractEntity_.ID);
    private final List<String> v2ConflictIdColumns = List.of(AbstractEntity_.ID);
    private final Set<String> nullableColumns = Set.of(AbstractEntity_.AUTO_RENEW_PERIOD,
            AbstractEntity_.CREATED_TIMESTAMP, AbstractEntity_.DELETED, AbstractEntity_.EXPIRATION_TIMESTAMP,
            Contract_.FILE_ID, AbstractEntity_.KEY, Contract_.OBTAINER_ID, Contract_.PARENT_ID,
            AbstractEntity_.PUBLIC_KEY, AbstractEntity_.PROXY_ACCOUNT_ID, AbstractEntity_.TIMESTAMP_RANGE);
    private final Set<String> nonUpdatableColumns = Set.of(AbstractEntity_.CREATED_TIMESTAMP, AbstractEntity_.ID,
            AbstractEntity_.NUM,
            AbstractEntity_.REALM, AbstractEntity_.SHARD, AbstractEntity_.TYPE);

    @Override
    public String getInsertWhereClause() {
        return "";
    }

    /**
     * Only EntityId entries will have a timestamp range as [0,) so those are only relevant for insert flow and can be
     * discarded.
     */
    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s is null and lower(%s) > 0",
                getFullFinalTableColumnName(AbstractEntity_.ID),
                getFullTempTableColumnName(AbstractEntity_.ID),
                getFullTempTableColumnName(AbstractEntity_.CREATED_TIMESTAMP),
                getFullTempTableColumnName(AbstractEntity_.TIMESTAMP_RANGE));
    }
}
