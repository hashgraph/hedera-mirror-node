/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityStakeRepositoryTest extends AbstractRepositoryTest {

    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;

    @ParameterizedTest
    @CsvSource({
        ",,true,true", // empty node_stake, empty entity_stake, has staking reward account
        ",,false,true", // empty node_stake, empty entity_stake, no staking reward account
        ",5,true,true", // empty node_stake, non-empty entity_stake, has staking reward account
        ",5,false,true", // empty node_stake, non-empty entity_stake, no staking reward account
        "5,5,true,true", // entity_stake is up-to-date, has staking reward account
        "5,5,false,true", // entity_stake is up-to-date, no staking reward account
        "5,,true,false", // non-empty node_stake, empty entity_stake, has staking reward account
        "5,,false,true", // non-empty node_stake, empty entity_stake, no staking reward account
        "5,4,true,false", // node_stake is ahead of entity_stake, has staking reward account
        "5,4,false,true", // node_stake is ahead of entity_stake, no staking reward account
    })
    void updated(Long epochDay, Long endStakePeriod, boolean hasStakingRewardAccount, boolean expected) {
        // given
        if (epochDay != null) {
            domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 1)).persist();
            domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay)).persist(); // with a later timestamp
        }

        if (endStakePeriod != null) {
            domainBuilder
                    .entityStake()
                    .customize(e -> e.id(800L).endStakePeriod(endStakePeriod))
                    .persist();
            domainBuilder
                    .entityStake()
                    .customize(e -> e.id(801L).endStakePeriod(endStakePeriod - 1))
                    .persist();
        }

        if (hasStakingRewardAccount) {
            domainBuilder.entity().customize(e -> e.id(800L).num(800L)).persist();
        }

        // when
        boolean actual = entityStakeRepository.updated();

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void updateEntityStake() {
        // given
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(null).stakedNodeId(1L))
                .persist();
        var entity2 = domainBuilder
                .entity()
                .customize(e -> e.declineReward(true).stakedAccountId(entity1.getId()))
                .persist();
        var entity3 = domainBuilder
                .entity()
                .customize(e ->
                        e.stakedAccountId(entity1.getId()).stakedNodeId(null).type(CONTRACT))
                .persist();
        var entity4 = domainBuilder
                .entity()
                .customize(e -> e.deleted(true).stakedAccountId(entity1.getId()))
                .persist();
        var entity5 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(entity3.getId()))
                .persist();
        var entity6 = domainBuilder.entity().persist();
        domainBuilder.topic().persist();
        var entity8 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(entity6.getId()))
                .persist();
        long entityId9 = entity8.getId() + 1;
        long entityId10 = entityId9 + 1;
        var entity9 = domainBuilder
                .entity()
                .customize(e -> e.id(entityId9).num(entityId9).stakedAccountId(entityId10))
                .persist();
        var entity10 = domainBuilder
                .entity()
                .customize(e -> e.id(entityId10).num(entityId10).stakedAccountId(entityId9))
                .persist();
        long timestamp = domainBuilder.timestamp();
        var nodeStake = domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).nodeId(1L).rewardRate(0L))
                .persist();
        // account balance
        long balanceTimestamp = nodeStake.getConsensusTimestamp() - 1000L;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100L).id(new AccountBalance.Id(balanceTimestamp, entity1.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(200L).id(new AccountBalance.Id(balanceTimestamp, entity2.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(300L).id(new AccountBalance.Id(balanceTimestamp, entity3.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(400L).id(new AccountBalance.Id(balanceTimestamp, entity4.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(500L).id(new AccountBalance.Id(balanceTimestamp, entity5.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(600L).id(new AccountBalance.Id(balanceTimestamp, entity6.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(800L).id(new AccountBalance.Id(balanceTimestamp, entity8.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(900L).id(new AccountBalance.Id(balanceTimestamp, entity9.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(1000L).id(new AccountBalance.Id(balanceTimestamp, entity10.toEntityId())))
                .persist();
        domainBuilder
                .recordFile()
                .customize(rf -> rf.consensusStart(balanceTimestamp - 100L)
                        .consensusEnd(balanceTimestamp + 100L)
                        .hapiVersionMinor(25))
                .persist();

        long epochDay = nodeStake.getEpochDay();
        // existing entity stake, note entity4 has been deleted, its existing entity stake will no longer update
        domainBuilder.entityStake().customize(es -> es.id(entity1.getId())).persist();
        domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(epochDay - 1).id(entity4.getId()))
                .persist();
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
                fromEntity(entity10, epochDay, 900L, 0L));

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
        var account =
                domainBuilder.entity().customize(e -> e.declineReward(true)).persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(2L).type(CONTRACT))
                .persist();
        domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        domainBuilder.topic().persist();
        var nodeStake = domainBuilder.nodeStake().persist();
        long balanceTimestamp = nodeStake.getConsensusTimestamp() - 1000L;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(0L).id(new AccountBalance.Id(balanceTimestamp, account.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(5L).id(new AccountBalance.Id(balanceTimestamp, contract.toEntityId())))
                .persist();
        domainBuilder
                .recordFile()
                .customize(rf -> rf.consensusStart(balanceTimestamp - 100L)
                        .consensusEnd(balanceTimestamp + 100L)
                        .hapiVersionMinor(25))
                .persist();
        entityRepository.refreshEntityStateStart();
        var expectedEntityStakes = List.of(
                fromEntity(account, nodeStake.getEpochDay(), 0L, 0L),
                fromEntity(contract, nodeStake.getEpochDay(), 0L, 5L));

        // when
        entityStakeRepository.updateEntityStake();

        // then
        assertThat(entityStakeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
    }

    @ParameterizedTest
    @CsvSource({
        "0, false, 1, -1, 15090000000, true, 1500", // The first period the account earns reward
        "20, false, 1, -2, 15090000000, true, 1520", // Accumulate the newly earned reward
        "20, false, 1, -2, 15090000000, false, 1520", // Accumulate the newly earned reward, contract
        "20, false, 1, 0, 15090000000, false, 0", // The account just started a new staking period, reset to 0
        "0, false, 1, -1, 0, false, 0", // The account has 0 stake total start, reward should be 0
        "0, false, 2, -1, 15090000000, true, 0", // The node reward rate is 0, reward should be 0
        "20, false, 3, 0, 15090000000, true, 20", // No node stake for node 3, the pending reward keeps the same
        "20, false, -1, , 15090000000, true, 0", // Not staked to a node
        "0, true, 1, , 15090000000, true, 0", // Decline reward start is true
    })
    void updateEntityStakePendingReward(
            long currentPendingReward,
            boolean declineRewardStart,
            long stakedNodeIdStart,
            Long stakePeriodStartOffset,
            long stakeTotalStart,
            boolean isAccountOrContract,
            long expectedPendingReward) {
        // given
        long nodeStakeEpochDay = 200L;
        long stakePeriodStart = stakePeriodStartOffset != null ? nodeStakeEpochDay + stakePeriodStartOffset : -1;
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakePeriodStart(stakePeriodStart).type(isAccountOrContract ? ACCOUNT : CONTRACT))
                .persist();
        domainBuilder
                .entityStake()
                .customize(es -> es.declineRewardStart(declineRewardStart)
                        .id(entity.getId())
                        .pendingReward(currentPendingReward)
                        .stakedNodeIdStart(stakedNodeIdStart)
                        .stakeTotalStart(stakeTotalStart))
                .persist();
        // the entity timestamp lower is before node stake timestamp
        long timestamp = domainBuilder.timestamp();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp)
                        .epochDay(nodeStakeEpochDay)
                        .nodeId(1L)
                        .rewardRate(10L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp)
                        .epochDay(nodeStakeEpochDay)
                        .nodeId(2L)
                        .rewardRate(0L))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(timestamp - 1000L))
                .persist();
        // The following two are old NodeStake, which shouldn't be used in pending reward calculation
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp - 100).nodeId(1L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp - 100).nodeId(2L))
                .persist();
        entityRepository.refreshEntityStateStart();

        // when
        entityStakeRepository.updateEntityStake();

        // then
        assertThat(entityStakeRepository.findById(entity.getId()))
                .get()
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
                .declineRewardStart(entity.isDeclineReward())
                .endStakePeriod(endStakePeriod)
                .id(entity.getId())
                .stakedNodeIdStart(Optional.ofNullable(entity.getStakedNodeId()).orElse(-1L))
                .stakedToMe(stakedToMe)
                .stakeTotalStart(stakeTotalStart)
                .build();
    }
}
