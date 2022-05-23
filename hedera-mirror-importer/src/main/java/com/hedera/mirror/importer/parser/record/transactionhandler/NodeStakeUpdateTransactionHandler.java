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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        long epochDay = toEpochDay(recordItem);
        var previousStake = getPreviousStake(epochDay);
        long rewardRate = transactionBody.getRewardRate();
        long stakeTotal = getStakeTotal(transactionBody);
        long stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());

        for (var nodeStakeProto : transactionBody.getNodeStakeList()) {
            long nodeId = nodeStakeProto.getNodeId();
            double rewardSum = 0L;
            long stake = nodeStakeProto.getStake();

            if (stakeTotal > 0) {
                var previousRewardSum = previousStake.getOrDefault(nodeId, EMPTY_NODE_STAKE).getRewardSum();
                rewardSum = previousRewardSum + rewardRate * (double) stake / stakeTotal / TINYBARS_IN_HBARS;
            }

            NodeStake nodeStake = new NodeStake();
            nodeStake.setConsensusTimestamp(recordItem.getConsensusTimestamp());
            nodeStake.setEpochDay(epochDay);
            nodeStake.setNodeId(nodeId);
            nodeStake.setRewardRate(rewardRate);
            nodeStake.setRewardSum((long) rewardSum);
            nodeStake.setStake(stake);
            nodeStake.setStakeRewarded(nodeStakeProto.getStakeRewarded());
            nodeStake.setStakeTotal(stakeTotal);
            nodeStake.setStakingPeriod(stakingPeriod);
            entityListener.onNodeStake(nodeStake);
        }
    }

    private Map<Long, NodeStake> getPreviousStake(long epochDay) {
        return nodeStakeRepository.findByEpochDay(epochDay - 1L)
                .stream()
                .collect(Collectors.toMap(NodeStake::getNodeId, Function.identity()));
    }

    // Sum node stake to get total stake
    private long getStakeTotal(NodeStakeUpdateTransactionBody transactionBody) {
        var stakeTotal = new AtomicLong(0L);
        transactionBody.getNodeStakeList().forEach(n -> stakeTotal.addAndGet(n.getStake()));
        return stakeTotal.get();
    }

    private long toEpochDay(RecordItem recordItem) {
        var instant = Utility.convertToInstant(recordItem.getRecord().getConsensusTimestamp());
        return LocalDate.ofInstant(instant, ZONE_UTC).toEpochDay();
    }
}
