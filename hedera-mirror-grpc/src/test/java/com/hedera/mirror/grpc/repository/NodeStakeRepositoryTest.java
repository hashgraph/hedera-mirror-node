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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NodeStakeRepositoryTest extends GrpcIntegrationTest {

    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void findLatestTimeStampEmptyTable() {
        Optional<Long> latestTimestampOpt = nodeStakeRepository.findLatestTimeStamp();
        assertThat(latestTimestampOpt).isEmpty();
    }

    @Test
    void findLatestTimeStamp() {
        nodeStake(1L, 0L, 0L);
        assertThat(nodeStakeRepository.findLatestTimeStamp()).contains(1L);

        nodeStake(2L, 0L, 0L);
        assertThat(nodeStakeRepository.findLatestTimeStamp()).contains(2L);
    }

    @Test
    void findStakeByConsensusTimestampAndNodeId() {
        long consensusTimestamp = 0L;
        nodeStake(consensusTimestamp, 0L, 0L);
        nodeStake(consensusTimestamp, 1L, 1L);

        consensusTimestamp++;
        nodeStake(consensusTimestamp, 0L, 10L);
        nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findStakeByConsensusTimestampAndNodeId(consensusTimestamp, 0L))
                .as("Latest node 0 stake")
                .contains(10L);

        assertThat(nodeStakeRepository.findStakeByConsensusTimestampAndNodeId(consensusTimestamp, 1L))
                .as("Latest node 1 stake")
                .contains(11L);
    }

    private NodeStake nodeStake(long consensusTimestamp, long nodeId, long stake) {
        return domainBuilder
                .nodeStake()
                .customize(e ->
                        e.consensusTimestamp(consensusTimestamp).nodeId(nodeId).stake(stake))
                .persist();
    }
}
