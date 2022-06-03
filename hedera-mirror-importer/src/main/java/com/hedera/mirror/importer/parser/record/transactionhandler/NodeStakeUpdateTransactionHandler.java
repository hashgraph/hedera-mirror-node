package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;

import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.NodeStakeRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class NodeStakeUpdateTransactionHandler implements TransactionHandler {

    private final EntityListener entityListener;
    private final NodeStakeRepository nodeStakeRepository;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODESTAKEUPDATE;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        // We subtract one since we get stake update in current day, but it applies to previous day
        long epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1L;
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var previousRewardSum = getRewardSum(epochDay - 1L);
        long rewardRate = transactionBody.getRewardRate();
        long stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        long stakeRewardedUsedTotal = 0L;
        long stakeTotal = 0L;

        for (var nodeStakeProto : transactionBody.getNodeStakeList()) {
            long stake = nodeStakeProto.getStake();
            stakeRewardedUsedTotal += Math.min(nodeStakeProto.getStakeRewarded(), stake);
            stakeTotal += stake;
        }

        for (var nodeStakeProto : transactionBody.getNodeStakeList()) {
            long nodeId = nodeStakeProto.getNodeId();
            long stake = nodeStakeProto.getStake();
            long stakeRewarded = nodeStakeProto.getStakeRewarded();
            double rewardSum = previousRewardSum.getOrDefault(nodeId, 0L);

            if (stakeRewardedUsedTotal > 0) {
                long stakedRewardedUsed = Math.min(stakeRewarded, stake);
                rewardSum += rewardRate * (double) stakedRewardedUsed / stakeRewardedUsedTotal / TINYBARS_IN_ONE_HBAR;
            }

            NodeStake nodeStake = new NodeStake();
            nodeStake.setConsensusTimestamp(recordItem.getConsensusTimestamp());
            nodeStake.setEpochDay(epochDay);
            nodeStake.setNodeId(nodeId);
            nodeStake.setRewardRate(rewardRate);
            nodeStake.setRewardSum((long) rewardSum);
            nodeStake.setStake(stake);
            nodeStake.setStakeRewarded(stakeRewarded);
            nodeStake.setStakeTotal(stakeTotal);
            nodeStake.setStakingPeriod(stakingPeriod);
            entityListener.onNodeStake(nodeStake);
        }
    }

    private Map<Long, Long> getRewardSum(long epochDay) {
        return nodeStakeRepository.findByEpochDay(epochDay)
                .stream()
                .collect(Collectors.toMap(NodeStake::getNodeId, NodeStake::getRewardSum));
    }
}
