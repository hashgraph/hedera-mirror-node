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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.util.Utility;

class NodeStakeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<NodeStake> nodeStakes;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeStakeUpdateTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeStakeUpdate(NodeStakeUpdateTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void testGetEntity() {
        assertThat(transactionHandler.getEntity(null)).isNull();
    }

    @Test
    void getType() {
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.NODESTAKEUPDATE);
    }

    @Test
    void updateTransactionUnsuccessful() {
        var recordItem = recordItemBuilder.nodeStakeUpdate()
                .receipt(r -> r.setStatus(ResponseCodeEnum.DUPLICATE_TRANSACTION))
                .build();
        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        long nodeStake1 = 5_000_000 * TINYBARS_IN_ONE_HBAR;
        var nodeStakeProto1 = getNodeStakeProto(nodeStake1, nodeStake1);

        long nodeStake2 = 21_000_000 * TINYBARS_IN_ONE_HBAR;
        var nodeStakeProto2 = getNodeStakeProto(nodeStake2, nodeStake2 - 3_000 * TINYBARS_IN_ONE_HBAR);

        var recordItem = recordItemBuilder.nodeStakeUpdate()
                .transactionBody(b -> b.clearNodeStake().addNodeStake(nodeStakeProto1).addNodeStake(nodeStakeProto2))
                .build();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long epochDay = Utility.getEpochDay(consensusTimestamp) - 1L;
        long stakeTotal = nodeStake1 + nodeStake2;
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        long stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        var expectedNodeStakes = List.of(
                getExpectedNodeStake(consensusTimestamp, epochDay, nodeStakeProto1, stakeTotal, stakingPeriod),
                getExpectedNodeStake(consensusTimestamp, epochDay, nodeStakeProto2, stakeTotal, stakingPeriod)
        );

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener, times(2)).onNodeStake(nodeStakes.capture());
        assertThat(nodeStakes.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedNodeStakes);
    }

    @Test
    void updateTransactionNoStake() {
        // given
        var recordItem = recordItemBuilder.nodeStakeUpdate().transactionBody(b -> b.clearNodeStake()).build();

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener, never()).onNodeStake(any());
    }

    private com.hederahashgraph.api.proto.java.NodeStake getNodeStakeProto(long stake, long stakeRewarded) {
        long stakeNotRewarded = stake - stakeRewarded;
        return recordItemBuilder.nodeStake()
                .setMaxStake(stake * 2)
                .setMinStake(stake / 2)
                .setStake(stake)
                .setStakeNotRewarded(stakeNotRewarded)
                .setStakeRewarded(stakeRewarded)
                .build();
    }

    private NodeStake getExpectedNodeStake(long consensusTimestamp, long epochDay,
                                           com.hederahashgraph.api.proto.java.NodeStake nodeStakeProto,
                                           long stakeTotal, long stakingPeriod) {
        return NodeStake.builder()
                .consensusTimestamp(consensusTimestamp)
                .epochDay(epochDay)
                .maxStake(nodeStakeProto.getMaxStake())
                .minStake(nodeStakeProto.getMinStake())
                .nodeId(nodeStakeProto.getNodeId())
                .rewardRate(nodeStakeProto.getRewardRate())
                .stake(nodeStakeProto.getStake())
                .stakeNotRewarded(nodeStakeProto.getStakeNotRewarded())
                .stakeRewarded(nodeStakeProto.getStakeRewarded())
                .stakeTotal(stakeTotal)
                .stakingPeriod(stakingPeriod)
                .build();
    }
}
