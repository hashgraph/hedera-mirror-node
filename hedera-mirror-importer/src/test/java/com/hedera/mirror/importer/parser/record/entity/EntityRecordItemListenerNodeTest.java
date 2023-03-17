package com.hedera.mirror.importer.parser.record.entity;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.NetworkStakeRepository;
import com.hedera.mirror.importer.repository.NodeStakeRepository;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerNodeTest extends AbstractEntityRecordItemListenerTest {

    private final NetworkStakeRepository networkStakeRepository;
    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void nodeStakeUpdate() {
        var recordItem = recordItemBuilder.nodeStakeUpdate().build();
        var body = recordItem.getTransactionBody().getNodeStakeUpdate();
        var nodeStake = body.getNodeStakeList().get(0);
        var stakingPeriod = DomainUtils.timestampInNanosMax(body.getEndOfStakingPeriod());
        var epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1L;

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertThat(nodeStakeRepository.findAll())
                        .hasSize(body.getNodeStakeCount())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), NodeStake::getConsensusTimestamp)
                        .returns(epochDay, NodeStake::getEpochDay)
                        .returns(nodeStake.getNodeId(), NodeStake::getNodeId)
                        .returns(nodeStake.getRewardRate(), NodeStake::getRewardRate)
                        .returns(nodeStake.getStake(), NodeStake::getStake)
                        .returns(nodeStake.getStakeRewarded(), NodeStake::getStakeRewarded)
                        .returns(stakingPeriod, NodeStake::getStakingPeriod),
                () -> assertThat(networkStakeRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(recordItem.getConsensusTimestamp(), NetworkStake::getConsensusTimestamp)
                        .returns(epochDay, NetworkStake::getEpochDay)
                        .returns(body.getMaxStakingRewardRatePerHbar(), NetworkStake::getMaxStakingRewardRatePerHbar)
                        .returns(body.getNodeRewardFeeFraction().getDenominator(),
                                NetworkStake::getNodeRewardFeeDenominator)
                        .returns(body.getNodeRewardFeeFraction().getNumerator(),
                                NetworkStake::getNodeRewardFeeNumerator)
                        .returns(nodeStake.getStake(), NetworkStake::getStakeTotal)
                        .returns(stakingPeriod, NetworkStake::getStakingPeriod)
                        .returns(body.getStakingPeriod(), NetworkStake::getStakingPeriodDuration)
                        .returns(body.getStakingPeriodsStored(), NetworkStake::getStakingPeriodsStored)
                        .returns(body.getStakingRewardFeeFraction().getDenominator(),
                                NetworkStake::getStakingRewardFeeDenominator)
                        .returns(body.getStakingRewardFeeFraction().getNumerator(),
                                NetworkStake::getStakingRewardFeeNumerator)
                        .returns(body.getStakingRewardRate(), NetworkStake::getStakingRewardRate)
                        .returns(body.getStakingStartThreshold(), NetworkStake::getStakingStartThreshold)
        );
    }
}
