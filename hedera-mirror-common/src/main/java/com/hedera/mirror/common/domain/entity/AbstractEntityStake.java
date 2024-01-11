/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.entity;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.Upsertable;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractEntityStake implements History {

    // The *Start properties are the entity's staking state at the beginning of the endStakePeriod + 1 staking period
    private boolean declineRewardStart;

    // The epoch day of the period for which the pending reward has included so far
    private long endStakePeriod;

    @Id
    private Long id;

    private long pendingReward;

    private long stakedNodeIdStart;

    private long stakedToMe;

    private long stakeTotalStart;

    private Range<Long> timestampRange;
}
