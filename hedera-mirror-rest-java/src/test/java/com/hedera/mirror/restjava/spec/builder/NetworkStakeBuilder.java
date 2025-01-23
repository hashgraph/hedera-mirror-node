/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class NetworkStakeBuilder extends AbstractEntityBuilder<NetworkStake, NetworkStake.NetworkStakeBuilder> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::networkStakes;
    }

    @Override
    protected NetworkStake.NetworkStakeBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return NetworkStake.builder()
                .consensusTimestamp(0L)
                .epochDay(0L)
                .maxStakeRewarded(10L)
                .maxStakingRewardRatePerHbar(17808L)
                .maxTotalReward(20L)
                .nodeRewardFeeDenominator(0L)
                .nodeRewardFeeNumerator(100L)
                .reservedStakingRewards(30L)
                .rewardBalanceThreshold(40L)
                .stakeTotal(10_000_000L)
                .stakingPeriod(86_400_000_000_000L - 1L)
                .stakingPeriodDuration(1440L)
                .stakingPeriodsStored(365L)
                .stakingRewardFeeDenominator(100L)
                .stakingRewardFeeNumerator(100L)
                .stakingRewardRate(100_000_000_000L)
                .stakingStartThreshold(25_000_000_000_000_000L)
                .unreservedStakingRewardBalance(50L);
    }

    @Override
    protected NetworkStake getFinalEntity(NetworkStake.NetworkStakeBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
