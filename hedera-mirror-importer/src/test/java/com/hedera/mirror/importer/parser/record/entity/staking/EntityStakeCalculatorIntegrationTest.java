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
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.balance.AccountBalanceBuilder;
import com.hedera.mirror.importer.parser.balance.AccountBalanceFileBuilder;
import com.hedera.mirror.importer.parser.balance.AccountBalanceFileParser;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import com.hedera.mirror.importer.util.Utility;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityStakeCalculatorIntegrationTest extends IntegrationTest {

    private final AccountBalanceBuilder accountBalanceBuilder;
    private final AccountBalanceFileBuilder accountBalanceFileBuilder;
    private final AccountBalanceFileParser accountBalanceFileParser;
    private final EntityStakeRepository entityStakeRepository;

    @Test
    void entityStakeCalculation() {
        // given
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        var newPeriodInstant = TestUtils.asStartOfEpochDay(epochDay + 1);
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(newPeriodInstant.plusNanos(2000L));
        long balanceTimestamp = DomainUtils.convertToNanosMax(newPeriodInstant.plusNanos(1000L));
        var accountBalanceFile = accountBalanceFileBuilder.accountBalanceFile(balanceTimestamp);

        domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L))
                .persist();
        var entityStake800 = domainBuilder
                .entityStake()
                .customize(es -> es.id(800L)
                        .endStakePeriod(epochDay - 1)
                        .stakeTotalStart(0L)
                        .stakedNodeIdStart(-1L))
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
        fromEntity(account1)
                .customize(es ->
                        es.endStakePeriod(epochDay - 1).pendingReward(100000L).stakeTotalStart(account1Balance))
                .persist();
        accountBalanceFile.accountBalance(accountBalanceBuilder
                .accountBalance(balanceTimestamp)
                .accountId(account1.toEntityId())
                .balance(account1Balance)
                .build());

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
        accountBalanceFile.accountBalance(accountBalanceBuilder
                .accountBalance(balanceTimestamp)
                .accountId(account2.toEntityId())
                .balance(account2Balance)
                .build());
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
        accountBalanceFile.accountBalance(accountBalanceBuilder
                .accountBalance(balanceTimestamp)
                .accountId(account3.toEntityId())
                .balance(account3Balance)
                .build());

        long accountId4 = domainBuilder.id();
        long creditAmount = 50 * TINYBARS_IN_ONE_HBAR;
        // crypto transfers right after the account balance file and before the node stake update
        persistCryptoTransfer(-2 * creditAmount, accountId4, balanceTimestamp + 1);
        persistCryptoTransfer(creditAmount, account2.getId(), balanceTimestamp + 1);
        persistCryptoTransfer(creditAmount, account3.getId(), balanceTimestamp + 1);

        var stakingPeriod = DomainUtils.convertToNanosMax(newPeriodInstant.minusNanos(1));
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(1L)
                        .rewardRate(100L)
                        .stakingPeriod(stakingPeriod))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(2L)
                        .rewardRate(200L)
                        .stakingPeriod(stakingPeriod))
                .persist();

        // crypto transfers after the node stake update
        persistCryptoTransfer(-2 * creditAmount, accountId4, nodeStakeTimestamp + 1);
        persistCryptoTransfer(creditAmount, account2.getId(), nodeStakeTimestamp + 1);
        persistCryptoTransfer(creditAmount, account3.getId(), nodeStakeTimestamp + 1);

        long account2BalanceStart = account2Balance + creditAmount;
        long account3BalanceStart = account3Balance + creditAmount;
        var expectedEntityStake1 = fromEntity(account1)
                .customize(es -> es.endStakePeriod(epochDay)
                        .pendingReward(100000L + 10000L)
                        .stakeTotalStart(account1Balance))
                .get();
        var expectedEntityStake2 = fromEntity(account2)
                .customize(es -> es.endStakePeriod(epochDay)
                        .stakedToMe(account3BalanceStart)
                        .stakeTotalStart(account2BalanceStart + account3BalanceStart))
                .get();
        var expectedEntityStake3 = fromEntity(account3)
                .customize(es -> es.endStakePeriod(epochDay).stakeTotalStart(0L))
                .get();
        entityStake800.setEndStakePeriod(epochDay);

        // when
        accountBalanceFileParser.parse(accountBalanceFile.build());

        // then
        await().atMost(Durations.FIVE_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .containsExactlyInAnyOrder(
                                expectedEntityStake1, expectedEntityStake2, expectedEntityStake3, entityStake800));
    }

    private void persistCryptoTransfer(long amount, long entityId, long timestamp) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount).consensusTimestamp(timestamp).entityId(entityId))
                .persist();
    }

    private DomainWrapper<EntityStake, EntityStake.EntityStakeBuilder> fromEntity(Entity entity) {
        return domainBuilder.entityStake().customize(es -> es.declineRewardStart(entity.getDeclineReward())
                .id(entity.getId())
                .stakedNodeIdStart(entity.getStakedNodeId()));
    }
}
