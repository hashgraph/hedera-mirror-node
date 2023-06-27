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

package com.hedera.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NodeStakeRepositoryTest extends GrpcIntegrationTest {

    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void emptyTableTest() {
        assertThat(nodeStakeRepository.findLatestTimestamp()).isEmpty();
        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(0L)).isEmpty();
        assertThat(nodeStakeRepository.findAllStakeByConsensusTimestamp(0L)).isEmpty();
    }

    @Test
    void findLatestTimeStamp() {
        nodeStake(1L, 0L, 0L);
        assertThat(nodeStakeRepository.findLatestTimestamp()).contains(1L);

        nodeStake(2L, 0L, 0L);
        assertThat(nodeStakeRepository.findLatestTimestamp()).contains(2L);
    }

    @Test
    void findAllByConsensusTimestamp() {
        long consensusTimestamp = 0L;
        var nodeStake0_0 = nodeStake(consensusTimestamp, 0L, 0L);
        var nodeStake0_1 = nodeStake(consensusTimestamp, 1L, 1L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 0 stakes")
                .containsExactly(nodeStake0_0, nodeStake0_1);

        // Load the next day's node stake info. This repository method is not cached.
        consensusTimestamp++;
        var nodeStake1_0 = nodeStake(consensusTimestamp, 0L, 10L);
        var nodeStake1_1 = nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 1 stakes")
                .containsExactly(nodeStake1_0, nodeStake1_1);
    }

    @Test
    void findAllStakeByConsensusTimestamp() {
        long consensusTimestamp = 0L;
        var nodeStake0_0 = nodeStake(consensusTimestamp, 0L, 0L);
        var nodeStake0_1 = nodeStake(consensusTimestamp, 1L, 1L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 0 stakes")
                .containsExactly(nodeStake0_0, nodeStake0_1);

        // Clear cache and load the next day's node stake info
        reset();

        consensusTimestamp++;
        var nodeStake1_0 = nodeStake(consensusTimestamp, 0L, 10L);
        var nodeStake1_1 = nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 1 stakes")
                .containsExactly(nodeStake1_0, nodeStake1_1);
    }

    private NodeStake nodeStake(long consensusTimestamp, long nodeId, long stake) {
        return domainBuilder
                .nodeStake()
                .customize(e ->
                        e.consensusTimestamp(consensusTimestamp).nodeId(nodeId).stake(stake))
                .persist();
    }
}
