/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class StakingRewardTransferRepositoryTest extends ImporterIntegrationTest {

    private final StakingRewardTransferRepository stakingRewardTransferRepository;

    @Test
    void prune() {
        domainBuilder.stakingRewardTransfer().persist();
        var stakingRewardTransfer2 = domainBuilder.stakingRewardTransfer().persist();
        var stakingRewardTransfer3 = domainBuilder.stakingRewardTransfer().persist();

        stakingRewardTransferRepository.prune(stakingRewardTransfer2.getConsensusTimestamp());

        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(stakingRewardTransfer3);
    }

    @Test
    void save() {
        var stakingRewardTransfer = domainBuilder.stakingRewardTransfer().get();
        stakingRewardTransferRepository.save(stakingRewardTransfer);
        assertThat(stakingRewardTransferRepository.findById(stakingRewardTransfer.getId()))
                .get()
                .isEqualTo(stakingRewardTransfer);
    }
}
