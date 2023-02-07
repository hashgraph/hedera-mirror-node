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

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.addressbook.NodeStake;

public interface NodeStakeRepository extends CrudRepository<NodeStake, NodeStake.Id>, RetentionRepository {

    @Query(value = "select * from node_stake where consensus_timestamp = (select max(consensus_timestamp) from " +
            "node_stake)", nativeQuery = true)
    List<NodeStake> findLatest();

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from node_stake where consensus_timestamp <= ?1 " +
            "and epoch_day < (select max(epoch_day) from node_stake) - 366")
    int prune(long consensusTimestamp);
}
