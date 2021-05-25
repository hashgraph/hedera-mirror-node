package com.hedera.mirror.importer.domain;

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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.converter.ScheduleIdConverter;

@Data
@Entity
@NoArgsConstructor
public class Schedule {
    public static final String TEMP_TABLE = "schedule_temp";
    public static final String TEMP_TO_MAIN_INSERT_SQL = "insert into schedule select consensus_timestamp, " +
            "creator_account_id, , executed_timestamp, payer_account_id, schedule_id, transaction_body " +
            "from " + TEMP_TABLE + "where consensus_timestamp is not null on conflict(schedule_id) do nothing";
    public static final String TEMP_TO_MAIN_UPDATE_SQL = "update schedule set " +
            "executed_timestamp = " + TEMP_TABLE + ".executed_timestamp from " + TEMP_TABLE +
            " where schedule.schedule_id = " + TEMP_TABLE + ".schedule_id and " + TEMP_TABLE + ".consensus_timestamp " +
            "is null";
    public static final String TEMP_TO_MAIN_UPSERT_SQL = "insert into schedule select coalesce(consensus_timestamp, " +
            "1), coalesce(creator_account_id, 0), executed_timestamp, coalesce(payer_account_id, 0), schedule_id, " +
            "coalesce(transaction_body, E'\'\''::bytea) from " + TEMP_TABLE +
            " on conflict (schedule_id) do update set " +
            "executed_timestamp = coalesce(excluded.executed_timestamp, schedule.executed_timestamp)";

    @Id
    private Long consensusTimestamp;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId creatorAccountId;

    private Long executedTimestamp;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId payerAccountId;

    @Convert(converter = ScheduleIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId scheduleId;

    @ToString.Exclude
    private byte[] transactionBody;
}
