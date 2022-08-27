package com.hedera.mirror.importer.repository;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityStakeRepositoryTest extends AbstractRepositoryTest {

    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;

    @Test
    void updateEntityStake() {
        // given
        var entity1 = domainBuilder.entity()
                .customize(e -> e.balance(100L).stakedAccountId(null).stakedNodeId(1L))
                .persist();
        var entity2 = domainBuilder.entity()
                .customize(e -> e.balance(200L).declineReward(true).stakedAccountId(entity1.getId()))
                .persist();
        var entity3 = domainBuilder.entity()
                .customize(e -> e.balance(300L).stakedAccountId(entity1.getId()).stakedNodeId(null).type(CONTRACT))
                .persist();
        var entity4 = domainBuilder.entity()
                // deleted is true and balance != 0 shouldn't happen, it's just used to test that deleted entities
                // are handled properly for the entities themselves and entities they used to stake to
                .customize(e -> e.balance(400L).deleted(true).stakedAccountId(entity1.getId()))
                .persist();
        var entity5 = domainBuilder.entity()
                .customize(e -> e.balance(500L).stakedAccountId(entity3.getId()))
                .persist();
        var entity6 = domainBuilder.entity().customize(e -> e.balance(600L)).persist();
        domainBuilder.topic().persist();
        var entity8 = domainBuilder.entity()
                .customize(e -> e.balance(800L).stakedAccountId(entity6.getId()))
                .persist();
        long entityId9 = entity8.getId() + 1;
        long entityId10 = entityId9 + 1;
        var entity9 = domainBuilder.entity()
                .customize(e -> e.balance(900L).id(entityId9).num(entityId9).stakedAccountId(entityId10))
                .persist();
        var entity10 = domainBuilder.entity()
                .customize(e -> e.balance(1000L).id(entityId10).num(entityId10).stakedAccountId(entityId9))
                .persist();
        long timestamp = domainBuilder.timestamp();
        var nodeStake = domainBuilder.nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).nodeId(1L).rewardRate(0L))
                .persist();
        long epochDay = nodeStake.getEpochDay();
        // existing entity stake, note entity4 has been deleted, its existing entity stake will no longer update
        domainBuilder.entityStake().customize(es -> es.id(entity1.getId())).persist();
        domainBuilder.entityStake().customize(es -> es.endStakePeriod(epochDay - 1).id(entity4.getId())).persist();
        entityRepository.refreshEntityStateStart();
        var expectedEntityStakes = List.of(
                fromEntity(entity1, epochDay, 500L, 600L),
                fromEntity(entity2, epochDay, 0L, 0L),
                fromEntity(entity3, epochDay, 500L, 0L),
                fromEntity(entity4, epochDay - 1, 0L, 0L),
                fromEntity(entity5, epochDay, 0L, 0L),
                fromEntity(entity6, epochDay, 800L, 0L),
                fromEntity(entity8, epochDay, 0L, 0L),
                fromEntity(entity9, epochDay, 1000L, 0L),
                fromEntity(entity10, epochDay, 900L, 0L)
        );

        // when
        entityStakeRepository.updateEntityStake();

        // then
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("pendingReward")
                .containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
    }

    @Test
    void updateEntityStakeForNewEntities() {
        // given
        var account = domainBuilder.entity().customize(e -> e.declineReward(true)).persist();
        var contract = domainBuilder.entity().customize(e -> e.balance(5L).stakedNodeId(2L).type(CONTRACT)).persist();
        domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        domainBuilder.topic().persist();
        var nodeStake = domainBuilder.nodeStake().persist();
        entityRepository.refreshEntityStateStart();
        var expectedEntityStakes = List.of(
                fromEntity(account, nodeStake.getEpochDay(), 0L, 0L),
                fromEntity(contract, nodeStake.getEpochDay(), 0L, 5L)
        );

        // when
        entityStakeRepository.updateEntityStake();

        // then
        assertThat(entityStakeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 1, -1, 15090000000, true, 1500",
            "20, false, 1, -1, 15090000000, true, 1520",
            "20, false, 1, -2, 15090000000, false, 1520",
            "20, false, 1, 0, 15090000000, false, 1500",
            "0, false, 1, 0, 0, false, 0",
            "0, false, 2, 0, 15090000000, true, 0",
            "20, false, 3, 0, 15090000000, true, 20", // no node stake for node 3, the pending reward keeps the same
            "20, false, -1, , 15090000000, true, 0",
            "0, true, 1, , 15090000000, true, 0",
    })
    void updateEntityStakePendingReward(long currentPendingReward, boolean declineRewardStart, long stakedNodeIdStart,
                                        Long stakePeriodStartOffset, long stakeTotalStart, boolean isAccount,
                                        long expectedPendingReward) {
        // given
        long nodeStakeEpochDay = 200L;
        long stakePeriodStart = stakePeriodStartOffset != null ? nodeStakeEpochDay + stakePeriodStartOffset : -1;
        var entity = domainBuilder.entity()
                .customize(e -> e.stakePeriodStart(stakePeriodStart).type(isAccount ? ACCOUNT : CONTRACT))
                .persist();
        domainBuilder.entityStake()
                .customize(es -> es.declineRewardStart(declineRewardStart)
                        .id(entity.getId())
                        .pendingReward(currentPendingReward)
                        .stakedNodeIdStart(stakedNodeIdStart)
                        .stakeTotalStart(stakeTotalStart))
                .persist();
        long timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).epochDay(nodeStakeEpochDay).nodeId(1L).rewardRate(10L))
                .persist();
        domainBuilder.nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).epochDay(nodeStakeEpochDay).nodeId(2L).rewardRate(0L))
                .persist();
        // The following two are old NodeStake, which shouldn't be used in pending reward calculation
        domainBuilder.nodeStake().customize(ns -> ns.consensusTimestamp(timestamp - 100).nodeId(1L)).persist();
        domainBuilder.nodeStake().customize(ns -> ns.consensusTimestamp(timestamp - 100).nodeId(2L)).persist();
        entityRepository.refreshEntityStateStart();

        // when
        entityStakeRepository.updateEntityStake();

        // then
        assertThat(entityStakeRepository.findById(entity.getId())).get()
                .returns(nodeStakeEpochDay, EntityStake::getEndStakePeriod)
                .returns(expectedPendingReward, EntityStake::getPendingReward);
    }

    @Test
    void save() {
        var entityStake = domainBuilder.entityStake().persist();
        assertThat(entityStakeRepository.findById(entityStake.getId())).get().isEqualTo(entityStake);
    }

    private EntityStake fromEntity(Entity entity, long endStakePeriod, long stakedToMe, long stakeTotalStart) {
        return EntityStake.builder()
                .declineRewardStart(entity.getDeclineReward())
                .endStakePeriod(endStakePeriod)
                .id(entity.getId())
                .stakedNodeIdStart(Optional.ofNullable(entity.getStakedNodeId()).orElse(-1L))
                .stakedToMe(stakedToMe)
                .stakeTotalStart(stakeTotalStart)
                .build();
    }
}
