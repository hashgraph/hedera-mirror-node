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

import com.hedera.mirror.importer.EnabledIfV2;

@EnabledIfV2
@SuppressWarnings("java:S2187")
class ScheduleUpsertQueryGeneratorV2Test extends ScheduleUpsertQueryGeneratorTest {
    @Override
    protected String getInsertQuery() {
        return "insert into schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, " +
                "schedule_id, transaction_body) select schedule_temp.consensus_timestamp, schedule_temp" +
                ".creator_account_id, schedule_temp.executed_timestamp, schedule_temp.payer_account_id, schedule_temp" +
                ".schedule_id, schedule_temp.transaction_body from schedule_temp where schedule_temp" +
                ".consensus_timestamp is not null on conflict (schedule_id) do nothing";
    }
}
