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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
class NodeStakeUpdateTransactionHandler implements TransactionHandler {

    private final EntityListener entityListener;

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
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        if (!recordItem.isSuccessful()) {
            var record = recordItem.getRecord();
            log.warn("NodeStakeUpdateTransaction at consensus timestamp {} failed with status {}", consensusTimestamp,
                    record.getReceipt().getStatus());
            return;
        }

        // We subtract one since we get stake update in current day, but it applies to previous day
        long epochDay = Utility.getEpochDay(consensusTimestamp) - 1L;
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        long stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        long stakeTotal = transactionBody.getNodeStakeList().stream().map(n -> n.getStake()).reduce(0L, Long::sum);

        for (var nodeStakeProto : transactionBody.getNodeStakeList()) {
            NodeStake nodeStake = new NodeStake();
            nodeStake.setConsensusTimestamp(consensusTimestamp);
            nodeStake.setEpochDay(epochDay);
            nodeStake.setNodeId(nodeStakeProto.getNodeId());
            nodeStake.setRewardRate(nodeStakeProto.getRewardRate());
            nodeStake.setStake(nodeStakeProto.getStake());
            nodeStake.setStakeRewarded(nodeStakeProto.getStakeRewarded());
            nodeStake.setStakeTotal(stakeTotal);
            nodeStake.setStakingPeriod(stakingPeriod);
            entityListener.onNodeStake(nodeStake);
        }
    }
}
