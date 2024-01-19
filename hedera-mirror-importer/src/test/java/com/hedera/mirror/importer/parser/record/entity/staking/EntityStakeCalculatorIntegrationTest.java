/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.staking;

import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.STAKING_REWARD_ACCOUNT;
import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TREASURY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalance.Id;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.EntityRecordItemListener;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.NodeStake;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
class EntityStakeCalculatorIntegrationTest extends ImporterIntegrationTest {

    private final EntityRecordItemListener entityRecordItemListener;
    private final EntityStakeRepository entityStakeRepository;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordStreamFileListener recordStreamFileListener;
    private final TransactionTemplate transactionTemplate;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void calculate(boolean skipOnePeriod) {
        // given
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        long numPeriods = skipOnePeriod ? 2 : 1;
        long endStakePeriod = skipOnePeriod ? epochDay + 1 : epochDay;
        var newPeriodInstant = TestUtils.asStartOfEpochDay(epochDay + numPeriods);
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(newPeriodInstant.plusNanos(2000L));
        long balanceTimestamp = DomainUtils.convertToNanosMax(newPeriodInstant.plusNanos(1000L));
        long previousBalanceTimestamp = balanceTimestamp - 1000;

        // the lower timestamp is the consensus timestamp of the previous NodeStakeUpdateTransaction
        long entityStakeLowerTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay - 1)) + 20L;
        var account800 = domainBuilder
                .entity(STAKING_REWARD_ACCOUNT, domainBuilder.timestamp())
                .persist();
        var entityStake800 = fromEntity(account800)
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .pendingReward(0L)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(entityStakeLowerTimestamp)))
                .persist();
        var treasury = domainBuilder.entity(TREASURY, domainBuilder.timestamp()).persist();
        var entityStakeTreasury = fromEntity(treasury)
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .pendingReward(0L)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(entityStakeLowerTimestamp)))
                .persist();

        // account1 was created two staking periods ago, there should be a row in entity_stake
        var account1 = domainBuilder
                .entity()
                .customize(e -> {
                    long stakePeriodStart = epochDay - 2;
                    long createdTimestamp =
                            DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(stakePeriodStart)) + 2000L;
                    e.createdTimestamp(createdTimestamp)
                            .stakedNodeId(1L)
                            .stakePeriodStart(stakePeriodStart)
                            .timestampRange(Range.atLeast(createdTimestamp));
                })
                .persist();
        long account1Balance = 100 * TINYBARS_IN_ONE_HBAR;
        var entityStake1 = fromEntity(account1)
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .pendingReward(100000L)
                        .stakeTotalStart(account1Balance)
                        .timestampRange(Range.atLeast(entityStakeLowerTimestamp)))
                .persist();

        // account2 and account3 were both created in the previous staking period, account3 stakes to account2
        var account2 = domainBuilder
                .entity()
                .customize(e -> {
                    long createdTimestamp =
                            DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay - 1)) + 2000L;
                    e.createdTimestamp(createdTimestamp)
                            .stakedNodeId(2L)
                            .stakePeriodStart(epochDay)
                            .timestampRange(Range.atLeast(createdTimestamp));
                })
                .persist();
        long account2Balance = 200 * TINYBARS_IN_ONE_HBAR;
        var account3 = domainBuilder
                .entity()
                .customize(e -> {
                    long createdTimestamp =
                            DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 3000L;
                    e.createdTimestamp(createdTimestamp)
                            .stakedAccountId(account2.getId())
                            .timestampRange(Range.atLeast(createdTimestamp));
                })
                .persist();
        long account3Balance = 300 * TINYBARS_IN_ONE_HBAR;
        long accountId4 = domainBuilder.id();

        // account balance
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(balanceTimestamp, treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(previousBalanceTimestamp, treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(balanceTimestamp, account800.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(previousBalanceTimestamp, account800.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(account1Balance).id(new Id(balanceTimestamp, account1.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(account1Balance - 100).id(new Id(previousBalanceTimestamp, account1.toEntityId())))
                .persist();
        // Deduped
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(account2Balance).id(new Id(previousBalanceTimestamp, account2.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(account3Balance).id(new Id(previousBalanceTimestamp, account3.toEntityId())))
                .persist();

        long creditAmount = 50 * TINYBARS_IN_ONE_HBAR;
        // crypto transfers right after the account balance snapshot timestamp and before the node stake update
        persistCryptoTransfer(-2 * creditAmount, accountId4, balanceTimestamp + 1);
        persistCryptoTransfer(creditAmount, account2.getId(), balanceTimestamp + 1);
        persistCryptoTransfer(creditAmount, account3.getId(), balanceTimestamp + 1);

        // crypto transfers after the node stake update
        persistCryptoTransfer(-2 * creditAmount, accountId4, nodeStakeTimestamp + 1);
        persistCryptoTransfer(creditAmount, account2.getId(), nodeStakeTimestamp + 1);
        persistCryptoTransfer(creditAmount, account3.getId(), nodeStakeTimestamp + 1);

        long account2BalanceStart = account2Balance + creditAmount;
        long account3BalanceStart = account3Balance + creditAmount;
        var expectedEntityStake1 = fromEntity(account1)
                .customize(es -> es.endStakePeriod(endStakePeriod)
                        .pendingReward(100000L + 10000L)
                        .stakeTotalStart(account1Balance)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStake2 = fromEntity(account2)
                .customize(es -> es.endStakePeriod(endStakePeriod)
                        .stakedToMe(account3BalanceStart)
                        .stakeTotalStart(account2BalanceStart + account3BalanceStart)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStake3 = fromEntity(account3)
                .customize(es -> es.endStakePeriod(endStakePeriod)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStake800 = fromEntity(account800)
                .customize(es -> es.endStakePeriod(endStakePeriod)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStakeTreasury = fromEntity(treasury)
                .customize(es -> es.endStakePeriod(endStakePeriod)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();

        // when
        // The staking period epochDay just ended
        var endOfStakingPeriod = TestUtils.toTimestamp(DomainUtils.convertToNanosMax(newPeriodInstant.minusNanos(1)));
        var recordItem = recordItemBuilder
                .nodeStakeUpdate()
                .transactionBody(t -> t.clearNodeStake()
                        .addNodeStake(NodeStake.newBuilder().setNodeId(1L).setRewardRate(100L))
                        .addNodeStake(NodeStake.newBuilder().setNodeId(2L).setRewardRate(200L))
                        .setEndOfStakingPeriod(endOfStakingPeriod))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(nodeStakeTimestamp)))
                .build();
        // process the NodeStakeUpdateTransaction
        persistRecordItem(recordItem);

        // then
        await().atMost(Durations.FIVE_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .containsExactlyInAnyOrder(
                                expectedEntityStake1,
                                expectedEntityStake2,
                                expectedEntityStake3,
                                expectedEntityStake800,
                                expectedEntityStakeTreasury));
        entityStake1.setTimestampUpper(nodeStakeTimestamp);
        entityStake800.setTimestampUpper(nodeStakeTimestamp);
        entityStakeTreasury.setTimestampUpper(nodeStakeTimestamp);
        assertThat(findHistory(EntityStake.class))
                .containsExactlyInAnyOrder(entityStake1, entityStake800, entityStakeTreasury);
    }

    @Test
    void calculateTwoPeriods() {
        // given
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        // Consensus timestamps for NodeStakeUpdate transactions
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 200L;
        long lastNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 300L;
        long secondLastNodeStakeTimestamp =
                DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay - 1)) + 100L;

        // Account 800's entity stake is up to end period epochDay - 2, calculation for period epochDay - 1 is skipped
        // for some reason, when the NodeStakeUpdate transaction for end period epochDay is processed, there should be
        // entity stake calculation done for two periods, epochDay - 1 and epochDay
        long balanceTimestamp = secondLastNodeStakeTimestamp - 2000;
        long previousBalanceTimestamp = balanceTimestamp - 1000;
        domainBuilder.entity(STAKING_REWARD_ACCOUNT, balanceTimestamp - 5000).persist();
        var entityStake800 = domainBuilder
                .entityStake()
                .customize(e -> e.endStakePeriod(epochDay - 2)
                        .id(STAKING_REWARD_ACCOUNT)
                        .timestampRange(Range.atLeast(secondLastNodeStakeTimestamp)))
                .persist();
        domainBuilder.entity(TREASURY, balanceTimestamp - 6000).persist();
        var entityStakeTreasury = domainBuilder
                .entityStake()
                .customize(e -> e.endStakePeriod(epochDay - 2)
                        .id(TREASURY)
                        .timestampRange(Range.atLeast(secondLastNodeStakeTimestamp)))
                .persist();
        // account balance
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(balanceTimestamp, EntityId.of(TREASURY))))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(previousBalanceTimestamp, EntityId.of(TREASURY))))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(previousBalanceTimestamp, EntityId.of(STAKING_REWARD_ACCOUNT))))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(lastNodeStakeTimestamp)
                        .epochDay(epochDay - 1)
                        .nodeId(0L))
                .persist();

        // when
        // The staking period epochDay just ended
        var endOfStakingPeriod = TestUtils.toTimestamp(DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay + 1).minusNanos(1)));
        var recordItem = recordItemBuilder
                .nodeStakeUpdate()
                .transactionBody(t -> t.clearNodeStake()
                        .addNodeStake(NodeStake.newBuilder().setNodeId(0L).setRewardRate(100L))
                        .setEndOfStakingPeriod(endOfStakingPeriod))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(nodeStakeTimestamp)))
                .build();
        // process the NodeStakeUpdateTransaction
        persistRecordItem(recordItem);

        // then
        var expectedEntityStake800 = entityStake800.toBuilder()
                .endStakePeriod(epochDay)
                .timestampRange(Range.atLeast(nodeStakeTimestamp))
                .build();
        var expectedEntityStakeTreasury = entityStakeTreasury.toBuilder()
                .endStakePeriod(epochDay)
                .timestampRange(Range.atLeast(nodeStakeTimestamp))
                .build();
        var expectedHistory = List.of(
                entityStake800.toBuilder()
                        .timestampRange(Range.closedOpen(secondLastNodeStakeTimestamp, lastNodeStakeTimestamp))
                        .build(),
                entityStake800.toBuilder()
                        .endStakePeriod(epochDay - 1)
                        .timestampRange(Range.closedOpen(lastNodeStakeTimestamp, nodeStakeTimestamp))
                        .build(),
                entityStakeTreasury.toBuilder()
                        .timestampRange(Range.closedOpen(secondLastNodeStakeTimestamp, lastNodeStakeTimestamp))
                        .build(),
                entityStakeTreasury.toBuilder()
                        .endStakePeriod(epochDay - 1)
                        .timestampRange(Range.closedOpen(lastNodeStakeTimestamp, nodeStakeTimestamp))
                        .build());
        await().atMost(Durations.FIVE_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .containsExactlyInAnyOrder(expectedEntityStake800, expectedEntityStakeTreasury));
        assertThat(findHistory(EntityStake.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private void persistCryptoTransfer(long amount, long entityId, long timestamp) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount).consensusTimestamp(timestamp).entityId(entityId))
                .persist();
    }

    private void persistRecordItem(RecordItem recordItem) {
        transactionTemplate.executeWithoutResult(s -> {
            long timestamp = recordItem.getConsensusTimestamp();
            var instant = Instant.ofEpochSecond(0, timestamp);
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            var recordFile = domainBuilder
                    .recordFile()
                    .customize(rf ->
                            rf.consensusStart(timestamp).consensusEnd(timestamp).name(filename))
                    .get();
            recordStreamFileListener.onStart();
            entityRecordItemListener.onItem(recordItem);
            recordStreamFileListener.onEnd(recordFile);
        });
    }

    private DomainWrapper<EntityStake, EntityStake.EntityStakeBuilder<?, ?>> fromEntity(Entity entity) {
        return domainBuilder.entityStake().customize(es -> es.declineRewardStart(entity.getDeclineReward())
                .id(entity.getId())
                .stakedNodeIdStart(entity.getStakedNodeId()));
    }
}
