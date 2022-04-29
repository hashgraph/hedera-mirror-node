package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.schedule.Schedule;

class ScheduleUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {

    @Resource
    private UpsertQueryGeneratorFactory factory;

    @Override
    protected UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return factory.get(Schedule.class);
    }

    @Override
    protected String getInsertQuery() {
        return "with existing as (" +
                "  select e.*" +
                "  from schedule_temp t" +
                "  join schedule e on e.schedule_id = t.schedule_id" +
                ")" +
                "insert into" +
                "  schedule (" +
                "    consensus_timestamp," +
                "    creator_account_id," +
                "    executed_timestamp," +
                "    expiration_time," +
                "    payer_account_id," +
                "    schedule_id," +
                "    transaction_body," +
                "    wait_for_expiry" +
                "  ) " +
                "select" +
                "  coalesce(t.consensus_timestamp, e.consensus_timestamp, null)," +
                "  coalesce(t.creator_account_id, e.creator_account_id, null)," +
                "  coalesce(t.executed_timestamp, e.executed_timestamp, null)," +
                "  coalesce(t.expiration_time, e.expiration_time, null)," +
                "  coalesce(t.payer_account_id, e.payer_account_id, null)," +
                "  coalesce(t.schedule_id, e.schedule_id, null)," +
                "  coalesce(t.transaction_body, e.transaction_body, null)," +
                "  coalesce(t.wait_for_expiry, e.wait_for_expiry, false) " +
                "from schedule_temp t " +
                "left join existing e on e.schedule_id = t.schedule_id " +
                "where coalesce(t.consensus_timestamp, e.consensus_timestamp) is not null " +
                "on conflict (schedule_id) do update" +
                "  set executed_timestamp = excluded.executed_timestamp";
    }

    @Override
    protected String getUpdateQuery() {
        return "";
    }

    @Test
    void tableName() {
        String tableName = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(tableName).isEqualTo("schedule");
    }

    @Test
    void tempTableName() {
        String tempTableName = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(tempTableName).isEqualTo("schedule_temp");
    }
}
