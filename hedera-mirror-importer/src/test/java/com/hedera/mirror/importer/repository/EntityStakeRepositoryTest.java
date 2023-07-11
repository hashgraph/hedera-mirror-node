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
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalance.Id;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.util.Utility;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionOperations;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityStakeRepositoryTest extends AbstractRepositoryTest {

    private static final String[] ENTITY_STATE_START_FIELDS =
            new String[] {"balance", "declineReward", "id", "stakedAccountId", "stakedNodeId", "stakePeriodStart"};
    private static final RowMapper<Entity> ROW_MAPPER = rowMapper(Entity.class);

    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;
    private final TransactionOperations transactionOperations;

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 130, 190
            1, 100, 190
            """)
    void createEntityStateStart(int timeOffset, long expectedBalance1, long expectedBalance2) {
        // given
        long epochDay = 1000L;
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        long previousNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 1000L;

        domainBuilder
                .nodeStake()
                .customize(
                        ns -> ns.consensusTimestamp(previousNodeStakeTimestamp).epochDay(epochDay - 1))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp).epochDay(epochDay))
                .persist();

        var account1 = domainBuilder
                .entity()
                .customize(e -> e.timestampRange(Range.atLeast(nodeStakeTimestamp - 1)))
                .persist();
        var account2 = domainBuilder
                .entity()
                .customize(e -> e.deleted(null).timestampRange(Range.atLeast(nodeStakeTimestamp - 2)))
                .persist();
        // account3 is valid after the node stake update timestamp
        var account3 = domainBuilder
                .entity()
                .customize(e -> e.timestampRange(Range.atLeast(nodeStakeTimestamp + 1)))
                .persist();
        // history row for account3
        var account3History = domainBuilder
                .entityHistory()
                .customize(e -> e.id(account3.getId())
                        .num(account3.getNum())
                        .stakedNodeId(3L)
                        .timestampRange(Range.closedOpen(nodeStakeTimestamp - 10, nodeStakeTimestamp + 1)))
                .persist();
        // deleted account will not appear in entity_state_start
        var account4 = domainBuilder
                .entity()
                .customize(e -> e.deleted(true).timestampRange(Range.atLeast(nodeStakeTimestamp - 3)))
                .persist(); // deleted
        // entity created after node stake timestamp will not appear in entity_state_start
        domainBuilder
                .entity()
                .customize(e -> e.timestampRange(Range.atLeast(nodeStakeTimestamp + 1)))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(null)
                        .stakedNodeId(null)
                        .stakePeriodStart(null)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp - 4))
                        .type(CONTRACT))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.type(TOPIC).timestampRange(Range.atLeast(nodeStakeTimestamp - 5)))
                .persist();

        long balanceTimestamp = nodeStakeTimestamp - 1000L;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp).timeOffset(timeOffset))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100L).id(new AccountBalance.Id(balanceTimestamp, account1.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(200L).id(new AccountBalance.Id(balanceTimestamp, account2.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(400L).id(new AccountBalance.Id(balanceTimestamp, account4.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(500L).id(new AccountBalance.Id(balanceTimestamp, contract.toEntityId())))
                .persist();

        persistCryptoTransfer(20L, balanceTimestamp, account1.getId());
        persistCryptoTransfer(30L, balanceTimestamp + 1, account1.getId());
        persistCryptoTransfer(-10L, balanceTimestamp + 54, account2.getId());
        // account3 is created after the account balance file
        persistCryptoTransfer(123L, account3History.getTimestampLower(), account3.getId());

        var expectedAccount1 = account1.toBuilder()
                .balance(expectedBalance1)
                .stakedAccountId(0L)
                .build();
        var expectedAccount2 = account2.toBuilder()
                .balance(expectedBalance2)
                .stakedAccountId(0L)
                .build();
        var expectedAccount3 = account3.toBuilder()
                .balance(123L)
                .stakedAccountId(0L)
                .stakedNodeId(3L)
                .build();
        var expectedContract = contract.toBuilder()
                .balance(500L)
                .stakedAccountId(0L)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .build();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart();

            // then
            assertEntityStartStart(List.of(expectedAccount1, expectedAccount2, expectedAccount3, expectedContract));
        });

        // given
        expectedAccount1.setDeclineReward(true);
        expectedAccount1.setStakePeriodStart(10L);
        expectedAccount1.setStakedNodeId(2L);
        expectedAccount2.setStakedAccountId(account1.getId());
        expectedAccount2.setStakePeriodStart(10L);
        contract.setDeleted(true);
        entityRepository.saveAll(List.of(expectedAccount1, expectedAccount2, contract));

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart();

            // then
            assertEntityStartStart(List.of(expectedAccount1, expectedAccount2, expectedAccount3));
        });
    }

    @Test
    void createEntityStateStartWhenEmptyEntity() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(timestamp).epochDay(epochDay))
                .persist();
        long balanceTimestamp = timestamp - 1000L;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(balanceTimestamp, domainBuilder.entityId(ACCOUNT))))
                .persist();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart();

            // then
            assertEntityStartStart(Collections.emptyList());
        });
    }

    @Test
    void createEntityStateStartWhenEmptyAccountBalance() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).epochDay(epochDay))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.timestampRange(Range.atLeast(timestamp - 5000L)))
                .persist();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart();

            // then
            assertEntityStartStart(Collections.emptyList());
        });
    }

    @Test
    void createEntityStateStartWhenEmptyNodeStake() {
        // given
        long balanceTimestamp = 1_000_000_000L;
        var account = domainBuilder
                .entity()
                .customize(e -> e.timestampRange(Range.atLeast(balanceTimestamp - 1000L)))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100L).id(new AccountBalance.Id(balanceTimestamp, account.toEntityId())))
                .persist();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart();

            // then
            assertEntityStartStart(Collections.emptyList());
        });
    }

    @Test
    void createEntityStateStartWhenNonEmptyEntityStakeAndCatchup() {
        // given
        long epochDay = 1000L;
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        long nextNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 2)) + 600L;
        long previousNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 700L;
        var stakingRewardAccount = EntityId.of(800, ACCOUNT);

        // Alice changed her staking settings after previousNodeStakeTimestamp, then she changed it again after
        // nextNodeStakeTimestamp. In entity state start, alice's stake settings should come from aliceHistory2
        domainBuilder
                .entity()
                .customize(e -> e.id(800L).timestampRange(Range.atLeast(previousNodeStakeTimestamp - 8000L)))
                .persist();
        var aliceHistory1 = domainBuilder
                .entityHistory()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(epochDay - 1)
                        .timestampRange(
                                Range.closedOpen(previousNodeStakeTimestamp - 900L, previousNodeStakeTimestamp + 200L)))
                .persist();
        var aliceHistory2 = domainBuilder
                .entityHistory()
                .customize(e -> e.id(aliceHistory1.getId())
                        .num(aliceHistory1.getNum())
                        .stakedAccountId(domainBuilder.id())
                        .stakePeriodStart(epochDay)
                        .timestampRange(Range.closedOpen(aliceHistory1.getTimestampUpper(), nodeStakeTimestamp + 300L)))
                .persist();
        var alice = domainBuilder
                .entity()
                .customize(e -> e.id(aliceHistory2.getId())
                        .num(aliceHistory2.getNum())
                        .stakedAccountId(domainBuilder.id())
                        .timestampRange(Range.atLeast(aliceHistory2.getTimestampUpper())))
                .persist();
        var aliceEntityId = EntityId.of(aliceHistory1.getId(), ACCOUNT);

        // Account 800's current end stake period is epochDay - 1 while the latest node stake's epoch day is
        // epochDay + 1, thus entity stake is two staking period behind
        domainBuilder
                .entityStake()
                .customize(es -> es.id(800L)
                        .endStakePeriod(epochDay - 1)
                        .timestampRange(Range.atLeast(previousNodeStakeTimestamp)))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(previousNodeStakeTimestamp)
                        .epochDay(epochDay - 1)
                        .nodeId(0))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(0))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nextNodeStakeTimestamp)
                        .epochDay(epochDay + 1)
                        .nodeId(0))
                .persist();

        // Account balance and crypto transfer before nodeStakeTimestamp
        long accountBalanceTimestamp = nodeStakeTimestamp - 500;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(accountBalanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(1000).id(new Id(accountBalanceTimestamp, stakingRewardAccount)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(1500).id(new Id(accountBalanceTimestamp, aliceEntityId)))
                .persist();
        persistCryptoTransfer(150, accountBalanceTimestamp + 10, 800);

        // Account balance before nextNodeStakeTimestamp
        long accountBalanceTimestampBeforeNextNodeStake = nextNodeStakeTimestamp - 700;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(accountBalanceTimestampBeforeNextNodeStake))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(2000).id(new Id(accountBalanceTimestampBeforeNextNodeStake, stakingRewardAccount)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(1700).id(new Id(accountBalanceTimestampBeforeNextNodeStake, aliceEntityId)))
                .persist();

        var expectedAlice = alice.toBuilder()
                .balance(1500L)
                .stakedAccountId(aliceHistory2.getStakedAccountId())
                .stakePeriodStart(aliceHistory2.getStakePeriodStart())
                .build();
        var expectedStakingRewardAccount = domainBuilder
                .entity()
                .customize(e -> e.balance(1150L)
                        .declineReward(false)
                        .id(800L)
                        .stakedAccountId(0L)
                        .stakedNodeId(-1L)
                        .stakePeriodStart(-1L))
                .get();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart();

            // then
            assertEntityStartStart(List.of(expectedAlice, expectedStakingRewardAccount));
        });
    }

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

        // existing entity stake, note entity4 has been deleted, its existing entity stake will no longer update
        var existingEntityStake1 = domainBuilder
                .entityStake()
                .customize(es -> es.id(entity1.getId())
                        .timestampRange(Range.atLeast(TestUtils.plus(
                                nodeStake.getConsensusTimestamp(),
                                Duration.ofDays(1).negated()))))
                .persist();
        var existingEntityStake4 = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(nodeStake.getEpochDay() - 1).id(entity4.getId()))
                .persist();
        var expectedEntityStakes = List.of(
                fromEntity(entity1, nodeStake, 500L, 600L),
                fromEntity(entity2, nodeStake, 0L, 0L),
                fromEntity(entity3, nodeStake, 500L, 0L),
                existingEntityStake4,
                fromEntity(entity5, nodeStake, 0L, 0L),
                fromEntity(entity6, nodeStake, 800L, 0L),
                fromEntity(entity8, nodeStake, 0L, 0L),
                fromEntity(entity9, nodeStake, 1000L, 0L),
                fromEntity(entity10, nodeStake, 900L, 0L));
        var entityStake1History = existingEntityStake1.toBuilder().build();
        entityStake1History.setTimestampUpper(nodeStake.getConsensusTimestamp());

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart();
            entityStakeRepository.updateEntityStake();
        });

        // then
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("pendingReward")
                .containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
        assertThat(findHistory(EntityStake.class)).containsExactly(entityStake1History);
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
        var expectedEntityStakes =
                List.of(fromEntity(account, nodeStake, 0L, 0L), fromEntity(contract, nodeStake, 0L, 5L));

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart();
            entityStakeRepository.updateEntityStake();
        });

        // then
        assertThat(entityStakeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
        assertThat(findHistory(EntityStake.class)).isEmpty();
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
        long nodeStakeTimestamp =
                DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(nodeStakeEpochDay + 1)) + 500;
        long stakePeriodStart = stakePeriodStartOffset != null ? nodeStakeEpochDay + stakePeriodStartOffset : -1;
        long entityLowerTimestamp =
                TestUtils.plus(nodeStakeTimestamp, Duration.ofHours(25).negated());
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakePeriodStart(stakePeriodStart)
                        .timestampRange(Range.atLeast(entityLowerTimestamp))
                        .type(isAccountOrContract ? ACCOUNT : CONTRACT))
                .persist();
        long nodeStakeLowerTimestamp =
                TestUtils.plus(nodeStakeTimestamp, Duration.ofDays(1).negated());
        var existingNodeStake = domainBuilder
                .entityStake()
                .customize(es -> es.declineRewardStart(declineRewardStart)
                        .endStakePeriod(nodeStakeEpochDay - 1)
                        .id(entity.getId())
                        .pendingReward(currentPendingReward)
                        .stakedNodeIdStart(stakedNodeIdStart)
                        .stakeTotalStart(stakeTotalStart)
                        .timestampRange(Range.atLeast(nodeStakeLowerTimestamp)))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(nodeStakeEpochDay)
                        .nodeId(1L)
                        .rewardRate(10L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(nodeStakeEpochDay)
                        .nodeId(2L)
                        .rewardRate(0L))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(nodeStakeTimestamp - 1000L))
                .persist();
        // The following two are old NodeStake, which shouldn't be used in pending reward calculation
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.epochDay(nodeStakeEpochDay - 1)
                        .consensusTimestamp(nodeStakeTimestamp - 100)
                        .nodeId(1L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.epochDay(nodeStakeEpochDay - 1)
                        .consensusTimestamp(nodeStakeTimestamp - 100)
                        .nodeId(2L))
                .persist();
        existingNodeStake.setTimestampUpper(nodeStakeTimestamp);

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart();
            entityStakeRepository.updateEntityStake();
        });

        // then
        assertThat(entityStakeRepository.findById(entity.getId()))
                .get()
                .returns(nodeStakeEpochDay, EntityStake::getEndStakePeriod)
                .returns(expectedPendingReward, EntityStake::getPendingReward);
        assertThat(findHistory(EntityStake.class)).containsExactly(existingNodeStake);
    }

    @Test
    void updateEntityStakePendingRewardExceedsMaxNumberOfStakingPeriods() {
        // given
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        long forfeitedStakingPeriod = epochDay - 365;
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay + 1).plusNanos(300));
        long previousNodeStakeTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay).plusNanos(200));
        long forfeitedNodeStakeTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(forfeitedStakingPeriod + 1).plusNanos(180));
        long entityLowerTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(forfeitedStakingPeriod).minusNanos(100));

        var stakingRewardAccount = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).timestampRange(Range.atLeast(entityLowerTimestamp)))
                .persist();
        // forfeitedStakingPeriod is the first period the account has earned staking reward, so the stake period start
        // is forfeitedStakingPeriod - 1
        var account = domainBuilder
                .entity()
                .customize(e -> {
                    long id = stakingRewardAccount.getId() + domainBuilder.id();
                    e.id(id)
                            .num(id)
                            .stakedNodeId(0L)
                            .stakePeriodStart(forfeitedStakingPeriod - 1)
                            .timestampRange(Range.atLeast(entityLowerTimestamp + 1));
                })
                .persist();
        // The end result is the pending reward will decrease by 100 * stake total start
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(forfeitedNodeStakeTimestamp)
                        .epochDay(forfeitedStakingPeriod)
                        .nodeId(0L)
                        .rewardRate(300))
                .persist();
        var nodeStake = domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(0L)
                        .rewardRate(200))
                .persist();
        var accountStake = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .id(account.getId())
                        .pendingReward(200_000_000L)
                        .stakedNodeIdStart(0L)
                        .stakeTotalStart(9_000_000_000L)
                        .timestampRange(Range.atLeast(previousNodeStakeTimestamp)))
                .persist();
        var stakingRewardAccountStake = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .id(800L)
                        .timestampRange(Range.atLeast(previousNodeStakeTimestamp)))
                .persist();
        long balanceTimestamp = nodeStakeTimestamp - 2000L;
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(accountStake.getStakeTotalStart())
                        .id(new Id(balanceTimestamp, account.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(balanceTimestamp, stakingRewardAccount.toEntityId())))
                .persist();
        var expectedAccountStake = fromEntity(account, nodeStake, 0, accountStake.getStakeTotalStart());
        expectedAccountStake.setPendingReward(
                accountStake.getPendingReward() - 100 * accountStake.getStakeTotalStart() / TINYBARS_IN_ONE_HBAR);
        var expectedStakingRewardAccountStake = fromEntity(stakingRewardAccount, nodeStake, 0, 0);
        // history rows
        accountStake.setTimestampUpper(nodeStakeTimestamp);
        stakingRewardAccountStake.setTimestampUpper(nodeStakeTimestamp);

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart();
            entityStakeRepository.updateEntityStake();
        });

        // then
        assertThat(entityStakeRepository.findAll())
                .containsExactlyInAnyOrder(expectedAccountStake, expectedStakingRewardAccountStake);
        assertThat(findHistory(EntityStake.class)).containsExactlyInAnyOrder(accountStake, stakingRewardAccountStake);
    }

    @Test
    void save() {
        var entityStake = domainBuilder.entityStake().get();
        entityStakeRepository.save(entityStake);
        assertThat(entityStakeRepository.findById(entityStake.getId())).get().isEqualTo(entityStake);
    }

    @Test
    void saveTriggerHistoryRow() {
        // given
        var entityStake = domainBuilder.entityStake().get();
        var update1 = TestUtils.clone(entityStake);
        update1.setPendingReward(200L);
        update1.setTimestampLower(update1.getTimestampLower() + 300L);
        var update2 = TestUtils.clone(update1);
        update2.setPendingReward(500L);
        update2.setTimestampLower(update2.getTimestampLower() + 2000L);

        // when
        entityStakeRepository.save(entityStake);
        entityStakeRepository.save(update1);
        entityStakeRepository.save(update2);

        // then
        entityStake.setTimestampUpper(update1.getTimestampLower());
        update1.setTimestampUpper(update2.getTimestampLower());
        assertThat(entityStakeRepository.findAll()).containsExactly(update2);
        assertThat(findHistory(EntityStake.class)).containsExactlyInAnyOrder(entityStake, update1);
    }

    private void assertEntityStartStart(List<Entity> expected) {
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER))
                .usingRecursiveFieldByFieldElementComparatorOnFields(ENTITY_STATE_START_FIELDS)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private EntityStake fromEntity(Entity entity, NodeStake nodeStake, long stakedToMe, long stakeTotalStart) {
        return EntityStake.builder()
                .declineRewardStart(entity.getDeclineReward())
                .endStakePeriod(nodeStake.getEpochDay())
                .id(entity.getId())
                .stakedNodeIdStart(Optional.ofNullable(entity.getStakedNodeId()).orElse(-1L))
                .stakedToMe(stakedToMe)
                .stakeTotalStart(stakeTotalStart)
                .timestampRange(Range.atLeast(nodeStake.getConsensusTimestamp()))
                .build();
    }

    private void persistCryptoTransfer(long amount, long consensusTimestamp, long entityId) {
        domainBuilder
                .cryptoTransfer()
                .customize(ct ->
                        ct.amount(amount).consensusTimestamp(consensusTimestamp).entityId(entityId))
                .persist();
    }
}
