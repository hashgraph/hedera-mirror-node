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
import com.hedera.mirror.importer.util.Utility;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NetworkStakeRepositoryTest extends ImporterIntegrationTest {

    private final NetworkStakeRepository networkStakeRepository;

    @Test
    void prune() {
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        domainBuilder.networkStake().customize(n -> n.epochDay(epochDay - 367)).persist();
        var networkStake2 = domainBuilder
                .networkStake()
                .customize(n -> n.epochDay(epochDay - 1))
                .persist();
        var networkStake3 = domainBuilder
                .networkStake()
                .customize(n -> n.epochDay(epochDay))
                .persist();

        networkStakeRepository.prune(networkStake2.getConsensusTimestamp());

        assertThat(networkStakeRepository.findAll()).containsExactlyInAnyOrder(networkStake2, networkStake3);
    }

    @Test
    void save() {
        var networkStake = domainBuilder.networkStake().get();
        networkStakeRepository.save(networkStake);
        assertThat(networkStakeRepository.findById(networkStake.getId())).get().isEqualTo(networkStake);
    }
}
