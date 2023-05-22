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

package com.hedera.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
public class NetworkStake implements Persistable<Long> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private long epochDay;
    private long maxStakingRewardRatePerHbar;
    private long nodeRewardFeeDenominator;
    private long nodeRewardFeeNumerator;
    private long stakeTotal;
    private long stakingPeriod;
    private long stakingPeriodDuration;
    private long stakingPeriodsStored;
    private long stakingRewardFeeDenominator;
    private long stakingRewardFeeNumerator;
    private long stakingRewardRate;
    private long stakingStartThreshold;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}
