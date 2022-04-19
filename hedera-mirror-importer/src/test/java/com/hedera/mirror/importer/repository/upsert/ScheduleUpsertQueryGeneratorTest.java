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
        return "insert into schedule (consensus_timestamp, creator_account_id, executed_timestamp, " +
                "expiration_time, payer_account_id, schedule_id, transaction_body, wait_for_expiry) " +
                "select" +
                "  t.consensus_timestamp, t.creator_account_id," +
                "  t.executed_timestamp, t.expiration_time, t.payer_account_id," +
                "  t.schedule_id, t.transaction_body, t.wait_for_expiry from schedule_temp t " +
                "where t.consensus_timestamp is not null on conflict (schedule_id) do nothing";
    }

    @Override
    protected String getUpdateQuery() {
        return "update schedule e " +
                "set executed_timestamp = coalesce(t.executed_timestamp, e.executed_timestamp) " +
                "from schedule_temp t " +
                "where t.consensus_timestamp is null and e.schedule_id = t.schedule_id";
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
