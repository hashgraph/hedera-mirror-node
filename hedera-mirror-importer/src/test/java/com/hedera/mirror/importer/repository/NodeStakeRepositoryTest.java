package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NodeStakeRepositoryTest extends AbstractRepositoryTest {

    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void prune() {
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 367)).persist();
        var nodeStake2 = domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 1)).persist();
        var nodeStake3 = domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay)).persist();

        nodeStakeRepository.prune(nodeStake2.getConsensusTimestamp());

        assertThat(nodeStakeRepository.findAll()).containsExactlyInAnyOrder(nodeStake2, nodeStake3);
    }

    @Test
    void save() {
        var nodeStake = domainBuilder.nodeStake().get();
        nodeStakeRepository.save(nodeStake);
        assertThat(nodeStakeRepository.findById(nodeStake.getId())).get().isEqualTo(nodeStake);
    }

    @Test
    void findLatest() {
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 2)).persist();
        domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 1)).persist();
        var latestNodeStake = domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay)).persist();

        assertThat(nodeStakeRepository.findLatest()).containsOnly(latestNodeStake);
    }
}
