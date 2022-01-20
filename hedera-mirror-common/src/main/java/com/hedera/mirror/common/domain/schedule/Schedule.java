package com.hedera.mirror.common.domain.schedule;

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

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@NoArgsConstructor
@Upsertable
public class Schedule {
    private Long consensusTimestamp;

    @Convert(converter = AccountIdConverter.class)
    private EntityId creatorAccountId;

    private Long executedTimestamp;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @Id
    private Long scheduleId;

    @ToString.Exclude
    private byte[] transactionBody;

    public void setScheduleId(EntityId scheduleId) {
        this.scheduleId = scheduleId != null ? scheduleId.getId() : null;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }
}
