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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("v1")
class ScheduleUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {
    @Resource
    private ScheduleUpsertQueryGenerator scheduleRepositoryCustom;

    @Override
    public UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return scheduleRepositoryCustom;
    }

    @Override
    public String getInsertQuery() {
        return "insert into schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, " +
                "schedule_id, transaction_body) select schedule_temp.consensus_timestamp, schedule_temp" +
                ".creator_account_id, schedule_temp.executed_timestamp, schedule_temp.payer_account_id, schedule_temp" +
                ".schedule_id, schedule_temp.transaction_body from schedule_temp where schedule_temp" +
                ".consensus_timestamp is not null on conflict (schedule_id) do nothing";
    }

    @Override
    public String getUpdateQuery() {
        return "update schedule set executed_timestamp = coalesce(schedule_temp.executed_timestamp, schedule" +
                ".executed_timestamp) from schedule_temp where schedule.schedule_id = schedule_temp.schedule_id and " +
                "schedule_temp.executed_timestamp is not null";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(upsertQuery).isEqualTo("schedule");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("schedule_temp");
    }
}
