/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
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
        var nodeStakeZeroZero = nodeStake(consensusTimestamp, 0L, 0L);
        var nodeStakeZeroOne = nodeStake(consensusTimestamp, 1L, 1L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 0 stakes")
                .containsExactly(nodeStakeZeroZero, nodeStakeZeroOne);

        // Load the next day's node stake info. This repository method is not cached.
        consensusTimestamp++;
        var nodeStakeOneZero = nodeStake(consensusTimestamp, 0L, 10L);
        var nodeStakeOneOne = nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 1 stakes")
                .containsExactly(nodeStakeOneZero, nodeStakeOneOne);
    }

    @Test
    void findAllStakeByConsensusTimestamp() {
        long consensusTimestamp = 0L;
        var nodeStakeZeroZero = nodeStake(consensusTimestamp, 0L, 0L);
        var nodeStakeZeroOne = nodeStake(consensusTimestamp, 1L, 1L);

        assertThat(nodeStakeRepository.findAllStakeByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 0 stakes")
                .containsAllEntriesOf(Map.of(
                        nodeStakeZeroZero.getNodeId(),
                        nodeStakeZeroZero.getStake(),
                        nodeStakeZeroOne.getNodeId(),
                        nodeStakeZeroOne.getStake()));

        // Clear cache and load the next day's node stake info
        reset();

        consensusTimestamp++;
        var nodeStakeOneZero = nodeStake(consensusTimestamp, 0L, 10L);
        var nodeStakeOneOne = nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findAllStakeByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 1 stakes")
                .containsAllEntriesOf(Map.of(
                        nodeStakeOneZero.getNodeId(),
                        nodeStakeOneZero.getStake(),
                        nodeStakeOneOne.getNodeId(),
                        nodeStakeOneOne.getStake()));
    }

    private NodeStake nodeStake(long consensusTimestamp, long nodeId, long stake) {
        return domainBuilder
                .nodeStake()
                .customize(e ->
                        e.consensusTimestamp(consensusTimestamp).nodeId(nodeId).stake(stake))
                .persist();
    }
}
