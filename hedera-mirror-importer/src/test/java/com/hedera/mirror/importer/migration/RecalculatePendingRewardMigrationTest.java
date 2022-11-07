package com.hedera.mirror.importer.migration;

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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.repository.EntityStakeRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.2")
class RecalculatePendingRewardMigrationTest extends IntegrationTest {

    private static final long FIRST_EPOCH_DAY = 19280L;
    private static final long FIRST_MAINNET_REWARD_EPOCH_DAY = 19285L;

    private final EntityStakeRepository entityStakeRepository;
    private final MirrorProperties mirrorProperties;
    private final RecalculatePendingRewardMigration migration;

    @BeforeEach
    void setup() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
    }

    @Test
    void empty() {
        migrate();
        assertThat(entityStakeRepository.findAll()).isEmpty();
    }

    @Test
    void notMainnet() {
        // given
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        setupNodeStake();
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(FIRST_EPOCH_DAY))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(100L).stakedNodeIdStart(0L)
                        .stakeTotalStart(200 * TINYBARS_IN_ONE_HBAR))
                .persist();

        // when
        migrate();

        // then
        assertThat(entityStakeRepository.findAll()).containsExactly(entityStake);
    }

    @Test
    void mainnet() {
        // given
        setupNodeStake();
        // need 0.0.800's entity stake, also set the last calculated epoch day to 19288
        domainBuilder.entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake800 = domainBuilder.entityStake()
                .customize(es -> es.id(800L).endStakePeriod(19288L))
                .persist();
        var entity1 = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(FIRST_EPOCH_DAY - 1))
                .persist();
        var entityStake1 = domainBuilder.entityStake()
                .customize(es -> es.id(entity1.getId()).stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR))
                .persist();

        // when
        migrate();

        // then
        long totalRewardRate = 10L + 20L + 30L + 40L; // rewarded for period 19285 - 19288
        entityStake1.setPendingReward(entityStake1.getStakeTotalStart() / TINYBARS_IN_ONE_HBAR * totalRewardRate);
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @Test
    void mainnetStakeStartPeriodAfterFirst() {
        // given
        setupNodeStake();
        // need 0.0.800's entity stake, also set the last calculated epoch day to 19288
        domainBuilder.entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake800 = domainBuilder.entityStake()
                .customize(es -> es.id(800L).endStakePeriod(19288L))
                .persist();
        var entity1 = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(FIRST_MAINNET_REWARD_EPOCH_DAY))
                .persist();
        var entityStake1 = domainBuilder.entityStake()
                .customize(es -> es.id(entity1.getId()).stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR))
                .persist();

        // when
        migrate();

        // then
        long totalRewardRate = 20L + 30L + 40L; // rewarded for period 19286 - 19288
        entityStake1.setPendingReward(entityStake1.getStakeTotalStart() / TINYBARS_IN_ONE_HBAR * totalRewardRate);
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @Test
    void mainnetWithStakingRewardPayout() {
        // given
        setupNodeStake();
        // need 0.0.800's entity stake, also set the last calculated epoch day to 19288
        domainBuilder.entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake800 = domainBuilder.entityStake()
                .customize(es -> es.id(800L).endStakePeriod(19288L))
                .persist();
        long rewardPayoutEpochDay = FIRST_MAINNET_REWARD_EPOCH_DAY + 1;
        long stakePeriodStart = rewardPayoutEpochDay - 1;
        var entity1 = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(stakePeriodStart))
                .persist();
        var entityStake1 = domainBuilder.entityStake()
                .customize(es -> es.id(entity1.getId()).stakeTotalStart(101 * TINYBARS_IN_ONE_HBAR))
                .persist();
        var payoutInstant = TestUtils.asStartOfEpochDay(rewardPayoutEpochDay).plusSeconds(300);
        var payoutTimestamp = DomainUtils.convertToNanosMax(payoutInstant);
        var rewardAmount = 2 * TINYBARS_IN_ONE_HBAR;
        domainBuilder.stakingRewardTransfer(entity1.getId())
                .customize(t -> t.amount(rewardAmount).consensusTimestamp(payoutTimestamp))
                .persist();
        domainBuilder.cryptoTransfer()
                .customize(t -> t.entityId(entity1.getId()).amount(rewardAmount).consensusTimestamp(payoutTimestamp))
                .persist();
        domainBuilder.cryptoTransfer()
                .customize(t -> t.entityId(800L).amount(-rewardAmount).consensusTimestamp(payoutTimestamp))
                .persist();
        // a debit transfer from the account. As a result, the stake total start to use for stakePeriodStart + 1 should
        // be 101 - (2 - 1) = 100 hbar
        domainBuilder.cryptoTransfer()
                .customize(t -> t.entityId(entity1.getId()).amount(-TINYBARS_IN_ONE_HBAR)
                        .consensusTimestamp(DomainUtils.convertToNanosMax(payoutInstant.plusSeconds(3600L))))
                .persist();

        // when
        migrate();

        // then
        // account1 earns reward for period 19286 with a stake total of 100 hbar
        // account1 earns reward for period 19287 and 19288 with a stake total of 101 hbar
        long pendingReward = 100 * 20 + 101 * (30 + 40);
        entityStake1.setPendingReward(pendingReward);
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @Test
    void mainnetEntityDeleted() {
        // given
        setupNodeStake();
        // need 0.0.800's entity stake, also set the last calculated epoch day to 19288
        domainBuilder.entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake800 = domainBuilder.entityStake()
                .customize(es -> es.id(800L).endStakePeriod(19288L))
                .persist();
        var entity1 = domainBuilder.entity()
                .customize(e -> e.deleted(true).stakedNodeId(0L).stakePeriodStart(FIRST_MAINNET_REWARD_EPOCH_DAY))
                .persist();
        var entityStake1 = domainBuilder.entityStake()
                .customize(es -> es.id(entity1.getId()).stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR))
                .persist();

        // when
        migrate();

        // then
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @Test
    void mainnetDeclineReward() {
        // given
        setupNodeStake();
        // need 0.0.800's entity stake, also set the last calculated epoch day to 19288
        domainBuilder.entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake800 = domainBuilder.entityStake()
                .customize(es -> es.id(800L).endStakePeriod(19288L))
                .persist();
        var entity1 = domainBuilder.entity()
                .customize(e -> e.declineReward(true).stakedNodeId(0L).stakePeriodStart(FIRST_MAINNET_REWARD_EPOCH_DAY))
                .persist();
        var entityStake1 = domainBuilder.entityStake()
                .customize(es -> es.id(entity1.getId()).stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR))
                .persist();

        // when
        migrate();

        // then
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @Test
    void mainnetNotStakedToNode() {
        // given
        setupNodeStake();
        // need 0.0.800's entity stake, also set the last calculated epoch day to 19288
        domainBuilder.entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake800 = domainBuilder.entityStake()
                .customize(es -> es.id(800L).endStakePeriod(19288L))
                .persist();
        var entity1 = domainBuilder.entity().customize(e -> e.stakedNodeId(-1L)).persist();
        var entityStake1 = domainBuilder.entityStake()
                .customize(es -> es.id(entity1.getId()).stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR))
                .persist();

        // when
        migrate();

        // then
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @Test
    void mainnetEmptyEntityStake() {
        // given pending reward is disabled, therefore the entity_stake table should be empty
        setupNodeStake();

        // when
        migrate();

        // then
        assertThat(entityStakeRepository.findAll()).isEmpty();
    }

    private void migrate() {
        migration.doMigrate();
    }

    private long getNodeStakeTimestamp(long epochDay) {
        return DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay).plusNanos(100L));
    }

    private void setupNodeStake() {
        // set up node 0 reward rates starting from the first non-zero epoch day 19285L as 10, 20, 30, 40, 50
        for (int i = 0; i < 10; i++) {
            long rewardRateEpochDay = FIRST_EPOCH_DAY + i;
            long rewardRate = Math.max(rewardRateEpochDay - FIRST_MAINNET_REWARD_EPOCH_DAY + 1, 0) * 10;
            // reward rate for epoch day is sent in node stake update tx at the beginning of epoch day + 1
            long consensusTimestamp = getNodeStakeTimestamp(rewardRateEpochDay + 1);
            domainBuilder.nodeStake()
                    .customize(ns -> ns.consensusTimestamp(consensusTimestamp).epochDay(rewardRateEpochDay)
                            .nodeId(0L).rewardRate(rewardRate))
                    .persist();
        }
    }
}
