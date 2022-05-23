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
import static com.hedera.mirror.importer.parser.record.transactionhandler.NodeStakeUpdateTransactionHandler.ZONE_UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

class NodeStakeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private NodeStakeRepository nodeStakeRepository;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeStakeUpdateTransactionHandler(entityListener, nodeStakeRepository);
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
    void updateTransaction() {
        // given
        var recordItem = recordItemBuilder.nodeStakeUpdate().build();
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var nodeStakeProto = transactionBody.getNodeStakeList().get(0);
        var stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        var nodeStake = domainBuilder.nodeStake().customize(n -> n.nodeId(nodeStakeProto.getNodeId())).get();
        var epochDay = epochDay();
        doReturn(List.of(nodeStake)).when(nodeStakeRepository).findByEpochDay(epochDay - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onNodeStake(assertArg(n -> assertThat(n)
                .isNotNull()
                .returns(recordItem.getConsensusTimestamp(), NodeStake::getConsensusTimestamp)
                .returns(epochDay, NodeStake::getEpochDay)
                .returns(nodeStakeProto.getNodeId(), NodeStake::getNodeId)
                .returns(transactionBody.getRewardRate(), NodeStake::getRewardRate)
                .returns(nodeStakeProto.getStake(), NodeStake::getStake)
                .returns(nodeStakeProto.getStakeRewarded(), NodeStake::getStakeRewarded)
                .returns(stakingPeriod, NodeStake::getStakingPeriod)
                .returns(nodeStakeProto.getStake(), NodeStake::getStakeTotal)
                .extracting(NodeStake::getRewardSum, InstanceOfAssertFactories.LONG)
                .isPositive()));
    }

    @Test
    void updateTransactionRewardSum() {
        // given
        var recordItem = recordItemBuilder.nodeStakeUpdate()
                .transactionBody(t -> t.addNodeStake(recordItemBuilder.nodeStake())).build();
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var nodeStakeProto1 = transactionBody.getNodeStakeList().get(0);
        var nodeStakeProto2 = transactionBody.getNodeStakeList().get(1);
        var nodeStake1 = domainBuilder.nodeStake().customize(n -> n.nodeId(nodeStakeProto1.getNodeId())).get();
        var nodeStake2 = domainBuilder.nodeStake().customize(n -> n.nodeId(nodeStakeProto2.getNodeId())).get();

        ArgumentCaptor<NodeStake> nodeStakes = ArgumentCaptor.forClass(NodeStake.class);
        var totalStake = nodeStakeProto1.getStake() + nodeStakeProto2.getStake();
        var rewardRate = transactionBody.getRewardRate();
        var rewardSum1 = rewardSum(rewardRate, totalStake, nodeStakeProto1.getStake(), nodeStake1);
        var rewardSum2 = rewardSum(rewardRate, totalStake, nodeStakeProto2.getStake(), nodeStake2);
        doReturn(List.of(nodeStake1, nodeStake2)).when(nodeStakeRepository).findByEpochDay(epochDay() - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener, times(2)).onNodeStake(nodeStakes.capture());
        assertThat(nodeStakes.getAllValues())
                .hasSize(2)
                .allSatisfy(n -> assertThat(n.getEpochDay()).isEqualTo(epochDay()))
                .allSatisfy(n -> assertThat(n.getStakeTotal()).isEqualTo(totalStake))
                .extracting(NodeStake::getRewardSum)
                .containsExactly(rewardSum1, rewardSum2);
    }

    private long rewardSum(long rewardRate, long totalStake, long stake, NodeStake previous) {
        return (long) (previous.getRewardSum() + rewardRate * (double) stake / totalStake / TINYBARS_IN_HBARS);
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

    @Test
    void updateTransactionZeroStake() {
        // given
        var recordItem = recordItemBuilder.nodeStakeUpdate()
                .transactionBody(b -> b.getNodeStakeBuilderList().forEach(n -> n.setStake(0L))).build();
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var nodeStake = domainBuilder.nodeStake().get();
        doReturn(List.of(nodeStake)).when(nodeStakeRepository).findByEpochDay(epochDay() - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onNodeStake(argThat(n -> n.getRewardSum() == 0));
    }

    @Test
    void updateTransactionNoPreviousStake() {
        // given
        var recordItem = recordItemBuilder.nodeStakeUpdate().build();
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        doReturn(List.of()).when(nodeStakeRepository).findByEpochDay(epochDay() - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        var stake = transactionBody.getNodeStake(0).getStake();
        verify(entityListener).onNodeStake(argThat(n -> n.getRewardSum() == rewardSum(transactionBody.getRewardRate(),
                stake, stake, new NodeStake())));
    }

    private long epochDay() {
        return LocalDate.now(ZONE_UTC).toEpochDay();
    }
}
