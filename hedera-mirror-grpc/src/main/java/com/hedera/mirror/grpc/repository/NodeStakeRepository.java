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

package com.hedera.mirror.grpc.repository;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.grpc.config.CacheConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NodeStakeRepository extends CrudRepository<NodeStake, NodeStake.Id> {
    @Query(value = "select max(consensus_timestamp) from node_stake", nativeQuery = true)
    Optional<Long> findLatestTimestamp();

    List<NodeStake> findAllByConsensusTimestamp(long consensusTimestamp);

    // An empty map may be cached, indicating the node_stake table is empty
    @Cacheable(cacheManager = CacheConfiguration.NODE_STAKE_CACHE, cacheNames = "node_stake")
    default Map<Long, Long> findAllStakeByConsensusTimestamp(long consensusTimestamp) {
        return findAllByConsensusTimestamp(consensusTimestamp).stream()
                .collect(Collectors.toUnmodifiableMap(NodeStake::getNodeId, NodeStake::getStake));
    }
}
