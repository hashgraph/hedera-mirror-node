/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.common.domain.schedule;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@NoArgsConstructor
@Upsertable
public class Schedule {

    @Column(updatable = false)
    private Long consensusTimestamp;

    @Column(updatable = false)
    @Convert(converter = AccountIdConverter.class)
    private EntityId creatorAccountId;

    private Long executedTimestamp;

    @Column(updatable = false)
    private Long expirationTime;

    @Column(updatable = false)
    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @Id
    private Long scheduleId;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] transactionBody;

    @Column(updatable = false)
    private boolean waitForExpiry;

    public void setScheduleId(EntityId scheduleId) {
        this.scheduleId = scheduleId != null ? scheduleId.getId() : null;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }
}
