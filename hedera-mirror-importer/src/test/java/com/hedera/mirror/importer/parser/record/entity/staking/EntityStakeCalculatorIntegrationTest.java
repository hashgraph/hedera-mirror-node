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

package com.hedera.mirror.importer.parser.record.entity.staking;

import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalance.Id;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.EntityRecordItemListener;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.NodeStake;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityStakeCalculatorIntegrationTest extends IntegrationTest {

    private final EntityRecordItemListener entityRecordItemListener;
    private final EntityStakeRepository entityStakeRepository;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordStreamFileListener recordStreamFileListener;
    private final TransactionTemplate transactionTemplate;

    @Test
    void entityStakeCalculation() {
        // given
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        var newPeriodInstant = TestUtils.asStartOfEpochDay(epochDay + 1);
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(newPeriodInstant.plusNanos(2000L));
        long balanceTimestamp = DomainUtils.convertToNanosMax(newPeriodInstant.plusNanos(1000L));

        // the lower timestamp is the consensus timestamp of the previous NodeStakeUpdateTransaction
        long entityStakeLowerTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay - 1)) + 20L;
        var account800 = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L))
                .persist();
        var entityStake800 = fromEntity(account800)
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

        // account balance file
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(balanceTimestamp, account800.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(account1Balance).id(new Id(balanceTimestamp, account1.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(account2Balance).id(new Id(balanceTimestamp, account2.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(account3Balance).id(new Id(balanceTimestamp, account3.toEntityId())))
                .persist();

        long creditAmount = 50 * TINYBARS_IN_ONE_HBAR;
        // crypto transfers right after the account balance file and before the node stake update
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
                .customize(es -> es.endStakePeriod(epochDay)
                        .pendingReward(100000L + 10000L)
                        .stakeTotalStart(account1Balance)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStake2 = fromEntity(account2)
                .customize(es -> es.endStakePeriod(epochDay)
                        .stakedToMe(account3BalanceStart)
                        .stakeTotalStart(account2BalanceStart + account3BalanceStart)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStake3 = fromEntity(account3)
                .customize(es -> es.endStakePeriod(epochDay)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();
        var expectedEntityStake800 = fromEntity(account800)
                .customize(es -> es.endStakePeriod(epochDay)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp)))
                .get();

        // when
        // The staking period just ended
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
        transactionTemplate.executeWithoutResult(s -> {
            var instant = Instant.ofEpochSecond(0, nodeStakeTimestamp);
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            var recordFile = domainBuilder
                    .recordFile()
                    .customize(rf -> rf.consensusStart(nodeStakeTimestamp)
                            .consensusEnd(nodeStakeTimestamp)
                            .name(filename))
                    .get();
            recordStreamFileListener.onStart();
            entityRecordItemListener.onItem(recordItem);
            recordStreamFileListener.onEnd(recordFile);
        });

        // then
        await().atMost(Durations.FIVE_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .containsExactlyInAnyOrder(
                                expectedEntityStake1,
                                expectedEntityStake2,
                                expectedEntityStake3,
                                expectedEntityStake800));
        entityStake1.setTimestampUpper(nodeStakeTimestamp);
        entityStake800.setTimestampUpper(nodeStakeTimestamp);
        assertThat(findHistory(EntityStake.class)).containsExactlyInAnyOrder(entityStake1, entityStake800);
    }

    private void persistCryptoTransfer(long amount, long entityId, long timestamp) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount).consensusTimestamp(timestamp).entityId(entityId))
                .persist();
    }

    private DomainWrapper<EntityStake, EntityStake.EntityStakeBuilder<?, ?>> fromEntity(Entity entity) {
        return domainBuilder.entityStake().customize(es -> es.declineRewardStart(entity.getDeclineReward())
                .id(entity.getId())
                .stakedNodeIdStart(entity.getStakedNodeId()));
    }
}
