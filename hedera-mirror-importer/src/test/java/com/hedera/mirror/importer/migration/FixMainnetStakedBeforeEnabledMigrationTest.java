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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityStakeRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class FixMainnetStakedBeforeEnabledMigrationTest extends IntegrationTest {

    private static final long LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END = 1658419200981687000L;
    private static final long STAKE_PERIOD_22_07_21 = 19194L;

    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;
    private final MirrorProperties mirrorProperties;
    private final FixMainnetStakedBeforeEnabledMigration migration;

    @BeforeEach
    void setup() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
    }

    @AfterEach
    void teardown() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertEntities().isEmpty();
        assertEntityStakes().isEmpty();
    }

    @Test
    void notMainnet() {
        // given
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.OTHER);
        persistLastMainnet26RecordFile();
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(STAKE_PERIOD_22_07_21)
                        .timestampRange(Range.atLeast(LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END)))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void notStaked() {
        // given
        persistLastMainnet26RecordFile();
        var entity = domainBuilder.entity()
                .customize(e -> e.declineReward(false).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedAfterEnabled() {
        // given
        persistLastMainnet26RecordFile();
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(STAKE_PERIOD_22_07_21 + 1L))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedAfterEnabledWithHistory() {
        // given
        persistLastMainnet26RecordFile();
        long stakingSetTimestamp = LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END - 100L;
        long lastUpdateTimestamp = LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END + 300L;
        // the history row has different setting and the current staking is set after 0.27.0 upgrade
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(STAKE_PERIOD_22_07_21)
                        .timestampRange(Range.atLeast(lastUpdateTimestamp)))
                .persist();
        domainBuilder.entityHistory()
                .customize(e -> e.id(entity.getId()).num(entity.getNum()).stakedNodeId(1L)
                        .stakePeriodStart(STAKE_PERIOD_22_07_21)
                        .timestampRange(Range.closedOpen(stakingSetTimestamp, lastUpdateTimestamp)))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedBeforeEnabled() {
        // given
        persistLastMainnet26RecordFile();
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(STAKE_PERIOD_22_07_21)
                        .timestampRange(Range.atLeast(LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END)))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        entity.setStakedNodeId(-1L);
        entity.setStakePeriodStart(-1L);
        entityStake.setPendingReward(0L);
        entityStake.setStakedNodeIdStart(-1L);
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedBeforeEnabledInHistory() {
        // given
        persistLastMainnet26RecordFile();
        long stakingSetTimestamp = LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END - 100L;
        long lastUpdateTimestamp = LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END + 300L;
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(STAKE_PERIOD_22_07_21)
                        .timestampRange(Range.atLeast(lastUpdateTimestamp)))
                .persist();
        domainBuilder.entityHistory()
                .customize(e -> e.id(entity.getId()).num(entity.getNum()).stakedNodeId(0L)
                        .stakePeriodStart(STAKE_PERIOD_22_07_21)
                        .timestampRange(Range.closedOpen(stakingSetTimestamp, lastUpdateTimestamp)))
                .persist();
        var entityStake = domainBuilder.entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        entity.setStakedNodeId(-1L);
        entity.setStakePeriodStart(-1L);
        entityStake.setPendingReward(0L);
        entityStake.setStakedNodeIdStart(-1L);
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    private IterableAssert<Entity> assertEntities() {
        return assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "declineReward", "stakedNodeId",
                        "stakePeriodStart");
    }

    private IterableAssert<EntityStake> assertEntityStakes() {
        return assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pending_reward", "staked_node_id_start");
    }

    private void persistLastMainnet26RecordFile() {
        long consensusStart = LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END - 2 * 1_000_000_000;
        domainBuilder.recordFile()
                .customize(r -> r.consensusEnd(LAST_MAINNET_26_RECORD_FILE_CONSENSUS_END).consensusStart(consensusStart))
                .persist();
    }
}
