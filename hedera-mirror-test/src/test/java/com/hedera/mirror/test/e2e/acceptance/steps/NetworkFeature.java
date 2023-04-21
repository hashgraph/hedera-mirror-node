/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorNetworkStake;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class NetworkFeature {

    private final MirrorNodeClient mirrorClient;

    private MirrorNetworkStake networkStake;

    @When("I query the network stake")
    public void verifyNetworkStake() {
        networkStake = mirrorClient.getNetworkStake();
        assertNotNull(networkStake);
    }

    @Then("the mirror node REST API returns the network stake")
    public void verifyNetworkStakeResponse() {
        assertThat(networkStake.getMaxStakingRewardRatePerHbar()).isPositive();
        assertThat(networkStake.getNodeRewardFeeFraction()).isBetween(0.0F, 1.0F);
        assertThat(networkStake.getStakeTotal()).isNotNegative();
        assertNotNull(networkStake.getStakingPeriod());
        assertThat(networkStake.getStakingPeriodDuration()).isPositive();
        assertThat(networkStake.getStakingPeriodsStored()).isPositive();
        assertThat(networkStake.getStakingRewardFeeFraction()).isBetween(0.0F, 1.0F);
        assertThat(networkStake.getStakingRewardRate()).isPositive();
        assertThat(networkStake.getStakingStartThreshold()).isPositive();
    }
}
