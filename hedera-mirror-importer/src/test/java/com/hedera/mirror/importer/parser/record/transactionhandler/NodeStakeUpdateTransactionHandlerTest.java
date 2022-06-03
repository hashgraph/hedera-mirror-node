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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.NodeStakeRepository;
import com.hedera.mirror.importer.util.Utility;

class NodeStakeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private NodeStakeRepository nodeStakeRepository;

    @Captor
    private ArgumentCaptor<NodeStake> nodeStakes;

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
        long epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1L;
        doReturn(List.of(nodeStake)).when(nodeStakeRepository).findByEpochDay(epochDay - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onNodeStake(nodeStakes.capture());
        assertThat(nodeStakes.getAllValues())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), NodeStake::getConsensusTimestamp)
                .returns(epochDay, NodeStake::getEpochDay)
                .returns(nodeStakeProto.getNodeId(), NodeStake::getNodeId)
                .returns(transactionBody.getRewardRate(), NodeStake::getRewardRate)
                .returns(nodeStakeProto.getStake(), NodeStake::getStake)
                .returns(nodeStakeProto.getStakeRewarded(), NodeStake::getStakeRewarded)
                .returns(stakingPeriod, NodeStake::getStakingPeriod)
                .returns(nodeStakeProto.getStake(), NodeStake::getStakeTotal)
                .extracting(NodeStake::getRewardSum, InstanceOfAssertFactories.LONG)
                .isPositive();
    }

    @CsvSource(value = {
            "6000, 2000, 1000, 4100, 2200",
            "0, 2000, 1000, 100, 200",
            "3000, 0, 1000, 100, 3200",
            "3000, 0, 0, 100, 200"
    })
    @ParameterizedTest
    void updateTransactionRewardSum(long rewardRate, long previousStakeRewarded1, long previousStakeRewarded2,
                                    long rewardSum1, long rewardSum2) {
        // given
        var nodeStakeProto1 = recordItemBuilder.nodeStake().setNodeId(1L).setStake(3000L)
                .setStakeRewarded(previousStakeRewarded1);
        var nodeStakeProto2 = recordItemBuilder.nodeStake().setNodeId(2L).setStake(5000L)
                .setStakeRewarded(previousStakeRewarded2);
        var rewardRateTinyBars = rewardRate * TINYBARS_IN_ONE_HBAR;
        var recordItem = recordItemBuilder.nodeStakeUpdate()
                .transactionBody(t -> t.setRewardRate(rewardRateTinyBars)
                        .clearNodeStake()
                        .addNodeStake(nodeStakeProto1.build())
                        .addNodeStake(nodeStakeProto2.build()))
                .build();
        long epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1L;
        var nodeStake1 = NodeStake.builder().nodeId(1L).rewardSum(100L).build();
        var nodeStake2 = NodeStake.builder().nodeId(2L).rewardSum(200L).build();
        doReturn(List.of(nodeStake1, nodeStake2)).when(nodeStakeRepository).findByEpochDay(epochDay - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener, times(2)).onNodeStake(nodeStakes.capture());
        assertThat(nodeStakes.getAllValues())
                .hasSize(2)
                .allSatisfy(n -> assertThat(n.getEpochDay()).isEqualTo(epochDay))
                .extracting(NodeStake::getRewardSum)
                .containsExactly(rewardSum1, rewardSum2);
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
    void updateTransactionNoPreviousStake() {
        // given
        var recordItem = recordItemBuilder.nodeStakeUpdate().build();
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        long epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1L;
        doReturn(List.of()).when(nodeStakeRepository).findByEpochDay(epochDay - 1L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        var stake = transactionBody.getNodeStake(0).getStake();
        verify(entityListener).onNodeStake(nodeStakes.capture());
        assertThat(nodeStakes.getAllValues())
                .allMatch(n -> n.getRewardSum() == transactionBody.getRewardRate() / TINYBARS_IN_ONE_HBAR);
    }
}
