package com.hedera.mirror.importer.parser.record.entity;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerNodeTest extends AbstractEntityRecordItemListenerTest {

    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void nodeStakeUpdate() {
        var recordItem = recordItemBuilder.nodeStakeUpdate().build();
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        var nodeStake = transactionBody.getNodeStakeList().get(0);
        var stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertThat(nodeStakeRepository.findAll())
                        .hasSize(transactionBody.getNodeStakeCount())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), NodeStake::getConsensusTimestamp)
                        .returns(LocalDate.now(ZoneId.of("UTC")).toEpochDay(), NodeStake::getEpochDay)
                        .returns(nodeStake.getNodeId(), NodeStake::getNodeId)
                        .returns(null, NodeStake::getRewardRate)
                        .returns(null, NodeStake::getRewardSum)
                        .returns(nodeStake.getStake(), NodeStake::getStake)
                        .returns(nodeStake.getStakeRewarded(), NodeStake::getStakeRewarded)
                        .returns(stakingPeriod, NodeStake::getStakingPeriod)
                        .returns(nodeStake.getStake(), NodeStake::getStakeTotal)
        );
    }
}
