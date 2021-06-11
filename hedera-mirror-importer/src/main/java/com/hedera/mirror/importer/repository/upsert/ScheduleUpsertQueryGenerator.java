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
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.Schedule_;

@Named
@RequiredArgsConstructor
public class ScheduleUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Schedule_> {
    public static final String TABLE = "schedule";
    public static final String TEMP_TABLE = TABLE + "_temp";
    private static final List<String> conflictTargetColumns = List.of(Schedule_.SCHEDULE_ID);
    private static final Set<String> nullableColumns = Set.of(Schedule_.EXECUTED_TIMESTAMP);
    private static final Set<SingularAttribute> updatableColumns = Set.of(Schedule_.executedTimestamp);

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
        return String.format(" where %s is not null ",
                getTableColumnName(getTemporaryTableName(), Schedule_.CONSENSUS_TIMESTAMP));
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s is not null",
                getTableColumnName(getTableName(), Schedule_.SCHEDULE_ID),
                getTableColumnName(getTemporaryTableName(), Schedule_.SCHEDULE_ID),
                getTableColumnName(getTemporaryTableName(), Schedule_.EXECUTED_TIMESTAMP));
    }

    @Override
    public Set<SingularAttribute> getUpdatableColumns() {
        return updatableColumns;
    }

    @Override
    public boolean isNullableColumn(String columnName) {
        return nullableColumns.contains(columnName);
    }
}
