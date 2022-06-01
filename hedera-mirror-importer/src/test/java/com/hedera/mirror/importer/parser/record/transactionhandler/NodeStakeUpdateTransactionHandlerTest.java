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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
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
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.repository.NodeStakeRepository;
import com.hedera.mirror.importer.util.Utility;

class NodeStakeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private NodeStakeRepository nodeStakeRepository;

    @Captor
    private ArgumentCaptor<NodeStake> nodeStakeArgCaptor;

    @Captor
    private ArgumentCaptor<Long> epochDayArgCaptor;

    @Captor
    private ArgumentCaptor<Long> nodeIdArgCaptor;

    @Captor
    private ArgumentCaptor<Long> rewardRateArgCaptor;

    @Captor
    private ArgumentCaptor<Long> rewardSumArgCaptor;

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
        var nodeId = nodeStakeProto.getNodeId();
        var rewardRate = transactionBody.getRewardRate();
        var stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        var epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp());
        var expectedNodeStake = NodeStake.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .epochDay(epochDay)
                .nodeId(nodeStakeProto.getNodeId())
                .stake(nodeStakeProto.getStake())
                .stakeRewarded(nodeStakeProto.getStakeRewarded())
                .stakeTotal(nodeStakeProto.getStake())
                .stakingPeriod(stakingPeriod)
                .build();
        doReturn(Collections.emptyList()).when(nodeStakeRepository).findByEpochDay(epochDay - 1L);
        doReturn(Collections.emptyList()).when(nodeStakeRepository).findByEpochDay(epochDay - 2L);

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener).onNodeStake(assertArg(n -> assertThat(n).isEqualTo(expectedNodeStake)));
        verify(nodeStakeRepository).setReward(epochDay - 1, nodeId, rewardRate, 0L);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "6000, 2000, 1000, 4000, 2000",
            "0, 2000, 1000, 0, 0",
            "3000, 0, 1000, 0, 3000",
            "3000, 0, 0, 0, 0"
    })
    void updateTransactionRewardSum(long rewardRate, long previousNodeStakeRewarded1, long previousNodeStakeRewarded2,
                                    long expectedDeltaNodeReward1, long expectedDeltaNodeReward2) {
        // given
        var nodeStakeProto1 = recordItemBuilder.nodeStake().setNodeId(1L).setStake(3000L).setStakeRewarded(1000L);
        var nodeStakeProto2 = recordItemBuilder.nodeStake().setNodeId(2L).setStake(5000L).setStakeRewarded(2000L);
        var rewardRateTinyBars = rewardRate * TINYBARS_IN_ONE_HBAR;
        var recordItem = recordItemBuilder.nodeStakeUpdate()
                .transactionBody(t -> t.setRewardRate(rewardRateTinyBars)
                        .clearNodeStake()
                        .addNodeStake(nodeStakeProto1.build())
                        .addNodeStake(nodeStakeProto2.build()))
                .build();
        var currentTimestamp = recordItem.getConsensusTimestamp();
        var epochDay = Utility.getEpochDay(currentTimestamp);
        var previousTimestamp = currentTimestamp - RecordItemBuilder.NANOS_IN_DAY;

        // previous, note the reward sum for previous is null, once the node stake update tx is processed, it'll be
        // updated
        var previousEpochDay = epochDay - 1;
        var previousNodeStake1 = NodeStake.builder()
                .consensusTimestamp(previousTimestamp)
                .epochDay(previousEpochDay)
                .nodeId(1L)
                .stake(2500L)
                .stakeRewarded(previousNodeStakeRewarded1)
                .stakeTotal(4000L)
                .stakingPeriod(previousTimestamp - 10L)
                .build();
        var previousNodeStake2 = NodeStake.builder()
                .consensusTimestamp(previousTimestamp)
                .epochDay(previousEpochDay)
                .nodeId(2L)
                .stake(1500L)
                .stakeRewarded(previousNodeStakeRewarded2)
                .stakeTotal(4000L)
                .stakingPeriod(previousTimestamp - 10L)
                .build();
        doReturn(List.of(previousNodeStake1, previousNodeStake2))
                .when(nodeStakeRepository).findByEpochDay(previousEpochDay);

        // the period before previous
        var baseEpochDay = previousEpochDay - 1;
        var baseTimestamp = previousTimestamp - RecordItemBuilder.NANOS_IN_DAY;
        var baseRewardSum1 = 100L;
        var baseNodeStake1 = NodeStake.builder()
                .consensusTimestamp(baseTimestamp)
                .epochDay(baseEpochDay)
                .nodeId(1L)
                .rewardRate(2000L)
                .rewardSum(baseRewardSum1)
                .stake(1500)
                .stakeRewarded(800L)
                .stakeTotal(2300L)
                .stakingPeriod(baseTimestamp - 10L)
                .build();
        var baseRewardSum2 = 300L;
        var baseNodeStake2 = NodeStake.builder()
                .consensusTimestamp(baseTimestamp)
                .epochDay(baseEpochDay)
                .nodeId(2L)
                .rewardRate(2000L)
                .rewardSum(baseRewardSum2)
                .stake(800L)
                .stakeRewarded(600L)
                .stakeTotal(2300L)
                .stakingPeriod(baseTimestamp - 10L)
                .build();
        doReturn(List.of(baseNodeStake1, baseNodeStake2)).when(nodeStakeRepository).findByEpochDay(baseEpochDay);

        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        var expectedNodeStake1 = NodeStake.builder()
                .consensusTimestamp(currentTimestamp)
                .epochDay(epochDay)
                .nodeId(1L)
                .stake(3000L)
                .stakeRewarded(1000L)
                .stakeTotal(8000L)
                .stakingPeriod(stakingPeriod)
                .build();
        var expectedNodeStake2 = NodeStake.builder()
                .consensusTimestamp(currentTimestamp)
                .epochDay(epochDay)
                .nodeId(2L)
                .stake(5000L)
                .stakeRewarded(2000L)
                .stakeTotal(8000L)
                .stakingPeriod(stakingPeriod)
                .build();
        var expectedRewardSum1 = baseRewardSum1 + expectedDeltaNodeReward1;
        var expectedRewardSum2 = baseRewardSum2 + expectedDeltaNodeReward2;

        // when
        transactionHandler.updateTransaction(null, recordItem);

        // then
        verify(entityListener, times(2)).onNodeStake(nodeStakeArgCaptor.capture());
        assertThat(nodeStakeArgCaptor.getAllValues()).containsExactlyInAnyOrder(expectedNodeStake1, expectedNodeStake2);
        verify(nodeStakeRepository, times(2)).setReward(epochDayArgCaptor.capture(), nodeIdArgCaptor.capture(),
                rewardRateArgCaptor.capture(), rewardSumArgCaptor.capture());
        assertAll(
                () -> assertThat(epochDayArgCaptor.getAllValues()).containsExactly(previousEpochDay, previousEpochDay),
                () -> assertThat(nodeIdArgCaptor.getAllValues()).containsExactly(1L, 2L),
                () -> assertThat(rewardRateArgCaptor.getAllValues()).containsExactly(rewardRateTinyBars,
                        rewardRateTinyBars),
                () -> assertThat(rewardSumArgCaptor.getAllValues()).containsExactly(expectedRewardSum1,
                        expectedRewardSum2)
        );
    }
}
