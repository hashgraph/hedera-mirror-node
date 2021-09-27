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

import com.hedera.mirror.importer.domain.Schedule_;

@Named
@Value
public class ScheduleUpsertQueryGenerator extends AbstractUpsertQueryGenerator<Schedule_> {
    private final String finalTableName = "schedule";
    private final String temporaryTableName = getFinalTableName() + "_temp";
    // scheduleId is used for completeness
    private final List<String> v1ConflictIdColumns = List.of(Schedule_.SCHEDULE_ID);
    private final Set<String> nullableColumns = Set.of(Schedule_.EXECUTED_TIMESTAMP);
    private final Set<String> nonUpdatableColumns = Set.of(Schedule_.CONSENSUS_TIMESTAMP,
            Schedule_.CREATOR_ACCOUNT_ID, Schedule_.PAYER_ACCOUNT_ID, Schedule_.SCHEDULE_ID,
            Schedule_.TRANSACTION_BODY);

    @Override
    public String getInsertWhereClause() {
        return String.format(" where %s is not null",
                getFullTempTableColumnName(Schedule_.CONSENSUS_TIMESTAMP));
    }

    @Override
    public String getUpdateWhereClause() {
        return String.format(" where %s = %s and %s is not null",
                getFullFinalTableColumnName(Schedule_.SCHEDULE_ID),
                getFullTempTableColumnName(Schedule_.SCHEDULE_ID),
                getFullTempTableColumnName(Schedule_.EXECUTED_TIMESTAMP));
    }
}
