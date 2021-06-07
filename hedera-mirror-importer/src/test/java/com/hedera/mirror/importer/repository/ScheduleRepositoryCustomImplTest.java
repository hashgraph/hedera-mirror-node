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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

class ScheduleRepositoryCustomImplTest extends AbstractRepositoryCustomImplTest {
    @Resource
    private ScheduleRepositoryCustomImpl scheduleRepositoryCustom;

    @Override
    public UpdatableDomainRepositoryCustom getUpdatableDomainRepositoryCustom() {
        return scheduleRepositoryCustom;
    }

    @Override
    public String getInsertQuery() {
        return "insert into schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, " +
                "schedule_id, transaction_body) select coalesce(schedule_temp.consensus_timestamp, 0) as " +
                "consensus_timestamp, coalesce(schedule_temp.creator_account_id, 1) as creator_account_id, coalesce" +
                "(schedule_temp.executed_timestamp, null) as executed_timestamp, coalesce(schedule_temp" +
                ".payer_account_id, 1) as payer_account_id, coalesce(schedule_temp.schedule_id, 1) as schedule_id, " +
                "coalesce(schedule_temp.transaction_body, E'\'\''::bytea) as transaction_body from schedule_temp " +
                "where schedule_temp.consensus_timestamp is not null  on conflict (schedule_id) do nothing";
    }

    @Override
    public String getUpdateQuery() {
        return "update schedule set executed_timestamp = coalesce(schedule_temp.executed_timestamp, schedule" +
                ".executed_timestamp) from schedule_temp where schedule.schedule_id = schedule_temp.schedule_id and " +
                "schedule_temp.executed_timestamp is not null";
    }

    @Override
    public String getUpsertQuery() {
        return "insert into schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, " +
                "schedule_id, transaction_body) select coalesce(schedule_temp.consensus_timestamp, 0) as " +
                "consensus_timestamp, coalesce(schedule_temp.creator_account_id, 1) as creator_account_id, coalesce" +
                "(schedule_temp.executed_timestamp, null) as executed_timestamp, coalesce(schedule_temp" +
                ".payer_account_id, 1) as payer_account_id, coalesce(schedule_temp.schedule_id, 1) as schedule_id, " +
                "coalesce(schedule_temp.transaction_body, E''''::bytea) as transaction_body from schedule_temp on " +
                "conflict (schedule_id) do update set executed_timestamp = coalesce(excluded.executed_timestamp, " +
                "schedule.executed_timestamp)";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTableName();
        assertThat(upsertQuery).isEqualTo("schedule");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("schedule_temp");
    }
}
