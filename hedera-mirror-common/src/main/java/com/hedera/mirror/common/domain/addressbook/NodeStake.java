package com.hedera.mirror.common.domain.addressbook;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.IdClass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(NodeStake.Id.class)
@NoArgsConstructor
public class NodeStake implements Persistable<NodeStake.Id> {

    @javax.persistence.Id
    private long consensusTimestamp;

    /**
     * The epoch day of the ending staking period
     */
    private long epochDay;

    /**
     * The maximum stake (rewarded or not rewarded) this node can have as consensus weight. If its stake to reward is
     * above this maximum at the start of a period, then accounts staking to the node in that period will be rewarded at
     * a lower rate scaled by (maxStake / stakeRewardStart).
     */
    private long maxStake;

    /**
     * The minimum stake (rewarded or not rewarded) this node must reach before having non-zero consensus weight. If its
     * total stake is below this minimum at the start of a period, then accounts staking to the node in that period will
     * receive no rewards.
     */
    private long minStake;

    @javax.persistence.Id
    private long nodeId;

    /**
     * The node's reward rate at the end of the staking period on epochDay
     */
    private long rewardRate;

    /**
     * The node consensus weight at the end of the staking period on epochDay
     */
    private long stake;

    /**
     * The sum of (balance + stakedToMe) for all accounts staked to this node with declineReward=true, at the end of the
     * staking period on epochDay
     */
    private long stakeNotRewarded;

    /**
     * The sum of (balance + stakedToMe) for all accounts staked to this node with declineReward=false, at the end of
     * the staking period on epochDay
     */
    private long stakeRewarded;

    /**
     * The timestamp of the end of the staking period on epochDay
     */
    private long stakingPeriod;

    @JsonIgnore
    @Override
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setNodeId(nodeId);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -2513526593205520365L;
        private long consensusTimestamp;
        private long nodeId;
    }
}
