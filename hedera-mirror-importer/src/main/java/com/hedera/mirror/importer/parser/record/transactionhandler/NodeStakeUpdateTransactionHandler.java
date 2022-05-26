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

import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_HBARS;

import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
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

    static final ZoneId ZONE_UTC = ZoneId.of("UTC");
    private static final NodeStake EMPTY_NODE_STAKE = new NodeStake();

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
        // The transaction's consensus timestamp is in the current staking period, so epochDay is current. The rewardRate
        // in the protobuf is for the previous staking period. As a result, the new rewardSum created would be for the
        // previous staking period, it's accumulated on top of the rewardSum of the staking period before the previous.
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()); // the new staking period
        // The node reward sum of the period before previous
        var baseRewardSum = getRewardSum(epochDay - 2);
        var previousStake = getStake(epochDay - 1);
        long previousTotalStakeRewarded = previousStake.values().stream().map(NodeStake::getStakeRewarded)
                .reduce(0L, Long::sum);
        var rewardRate = transactionBody.getRewardRate();
        long stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        var stakeTotal = getStakeTotal(transactionBody);

        for (var nodeStakeProto : transactionBody.getNodeStakeList()) {
            long nodeId = nodeStakeProto.getNodeId();
            NodeStake nodeStake = new NodeStake();
            nodeStake.setConsensusTimestamp(recordItem.getConsensusTimestamp());
            nodeStake.setEpochDay(epochDay);
            nodeStake.setNodeId(nodeId);
            nodeStake.setRewardRate(rewardRate);
            nodeStake.setStake(nodeStakeProto.getStake());
            nodeStake.setStakeRewarded(nodeStakeProto.getStakeRewarded());
            nodeStake.setStakeTotal(stakeTotal);
            nodeStake.setStakingPeriod(stakingPeriod);
            entityListener.onNodeStake(nodeStake);

            double rewardSum = baseRewardSum.getOrDefault(nodeId, 0L);
            var previousStakeRewarded = previousStake.getOrDefault(nodeId, EMPTY_NODE_STAKE).getStakeRewarded();
            if (rewardRate > 0 && previousTotalStakeRewarded > 0 && previousStakeRewarded > 0) {
                // TODO: waiting on HIP update so we can get correct previous total stake rewarded and the node's
                // previous stake rewarded by multiplying node's stake / total_stake
                rewardSum += rewardRate * (double) previousStakeRewarded /
                        previousTotalStakeRewarded / TINYBARS_IN_HBARS;
            }
            nodeStakeRepository.setRewardSum(epochDay - 1, nodeId, (long) rewardSum);
        }
    }

    private Map<Long, NodeStake> getStake(long epochDay) {
        return nodeStakeRepository.findByEpochDay(epochDay)
                .stream()
                .collect(Collectors.toMap(NodeStake::getNodeId, Function.identity()));
    }

    // Sum node stake to get total stake
    private long getStakeTotal(NodeStakeUpdateTransactionBody transactionBody) {
        return transactionBody.getNodeStakeList().stream().map(n -> n.getStake()).reduce(0L, Long::sum);
    }

    private Map<Long, Long> getRewardSum(long epochDay) {
        return getStake(epochDay).values().stream()
                .collect(Collectors.toMap(NodeStake::getNodeId, NodeStake::getRewardSum));
    }
}
