/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.convertRange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.Order;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@CustomLog
@Data
@RequiredArgsConstructor
public class NetworkFeature {

    private final MirrorNodeClient mirrorClient;

    @When("I verify the network stake")
    public void verifyNetworkStake() {
        if (!shouldHaveStake()) {
            log.warn("Skipping network stake verification since there's not yet any staking information");
            return;
        }

        var networkStake = mirrorClient.getNetworkStake();
        assertThat(networkStake).isNotNull();
        assertThat(networkStake.getMaxStakeRewarded()).isNotNegative();
        assertThat(networkStake.getMaxStakingRewardRatePerHbar()).isNotNegative();
        assertThat(networkStake.getMaxTotalReward()).isNotNegative();
        assertThat(networkStake.getNodeRewardFeeFraction()).isBetween(0.0F, 1.0F);
        assertThat(networkStake.getReservedStakingRewards()).isNotNegative();
        assertThat(networkStake.getRewardBalanceThreshold()).isNotNegative();
        assertThat(networkStake.getStakeTotal()).isNotNegative();
        assertNotNull(networkStake.getStakingPeriod());
        assertThat(networkStake.getStakingPeriodDuration()).isPositive();
        assertThat(networkStake.getStakingPeriodsStored()).isPositive();
        assertThat(networkStake.getStakingRewardFeeFraction()).isBetween(0.0F, 1.0F);
        assertThat(networkStake.getStakingRewardRate()).isNotNegative();
        assertThat(networkStake.getStakingStartThreshold()).isPositive();
        assertThat(networkStake.getUnreservedStakingRewardBalance()).isNotNegative();
    }

    private boolean shouldHaveStake() {
        var blocks = mirrorClient.getBlocks(Order.ASC, 1);
        if (blocks.getBlocks().isEmpty()) {
            return false;
        }

        var block = blocks.getBlocks().getFirst();
        var timestamp = convertRange(block.getTimestamp());
        if (timestamp == null || !timestamp.hasLowerBound()) {
            return false;
        }

        var midnight =
                LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return timestamp.lowerEndpoint().isBefore(midnight);
    }
}
