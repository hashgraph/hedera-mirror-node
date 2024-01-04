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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.importer.migration.FixStakedBeforeEnabledMigration.LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.ImporterProperties.HederaNetwork;
import com.hedera.mirror.importer.util.Utility;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.2.1")
@DisableRepeatableSqlMigration
class FixStakedBeforeEnabledMigrationTest extends AbstractStakingMigrationTest {

    private static final String[] ENTITY_FIELDS =
            new String[] {"id", "declineReward", "stakedNodeId", "stakePeriodStart"};
    private final ImporterProperties importerProperties;
    private final FixStakedBeforeEnabledMigration migration;

    private long lastHapi26EpochDay;

    @AfterEach
    void teardown() {
        importerProperties.setNetwork(HederaNetwork.TESTNET);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertEntities().isEmpty();
        assertEntityStakes().isEmpty();
    }

    @Test
    void notStaked() {
        // given
        setupForMainnet();
        var entity = domainBuilder
                .entity()
                .customize(e -> e.declineReward(false).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        persistEntity(entity);
        var entityStake = MigrationEntityStake.builder().id(entity.getId()).build();
        persistEntityStakes(entityStake);

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void otherNetwork() {
        // given
        setupForMainnet();
        importerProperties.setNetwork(HederaNetwork.OTHER);
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET)))
                .get();
        persistEntity(entity);
        var entityStake = MigrationEntityStake.builder()
                .id(entity.getId())
                .pendingReward(1000L)
                .stakedNodeIdStart(0L)
                .build();
        persistEntityStakes(entityStake);

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedAfterEnabled() {
        // given
        setupForMainnet();
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(lastHapi26EpochDay + 1L))
                .get();
        persistEntity(entity);
        var entityStake = MigrationEntityStake.builder()
                .id(entity.getId())
                .pendingReward(1000L)
                .stakedNodeIdStart(0L)
                .build();
        persistEntityStakes(entityStake);

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedAfterEnabledWithHistory() {
        // given
        setupForMainnet();
        long stakingSetTimestamp = LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET - 100L;
        long lastUpdateTimestamp = LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET + 300L;
        // the history row has different setting and the current staking is set after 0.27.0 upgrade
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(lastUpdateTimestamp)))
                .get();
        persistEntity(entity);
        var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId())
                        .num(entity.getNum())
                        .stakedNodeId(1L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.closedOpen(stakingSetTimestamp, lastUpdateTimestamp)))
                .get();
        persistEntityHistory(entityHistory);
        var entityStake = MigrationEntityStake.builder()
                .id(entity.getId())
                .pendingReward(1000L)
                .stakedNodeIdStart(0L)
                .build();
        persistEntityStakes(entityStake);

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void stakedBeforeEnabled() {
        // given
        setupForMainnet();
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET)))
                .get();
        persistEntity(entity);
        var entityStake = MigrationEntityStake.builder()
                .id(entity.getId())
                .pendingReward(1000L)
                .stakedNodeIdStart(0L)
                .build();
        persistEntityStakes(entityStake);

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
        setupForMainnet();
        long stakingSetTimestamp = LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET - 100L;
        long lastUpdateTimestamp = LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET + 300L;
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(lastUpdateTimestamp)))
                .get();
        persistEntity(entity);
        var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId())
                        .num(entity.getNum())
                        .stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.closedOpen(stakingSetTimestamp, lastUpdateTimestamp)))
                .get();
        persistEntityHistory(entityHistory);
        var entityStake = MigrationEntityStake.builder()
                .id(entity.getId())
                .pendingReward(1000L)
                .stakedNodeIdStart(0L)
                .build();
        persistEntityStakes(entityStake);

        // when
        migration.doMigrate();

        // then
        entity.setStakedNodeId(-1L);
        entity.setStakePeriodStart(-1L);
        entityStake.setPendingReward(0L);
        entityStake.setStakedNodeIdStart(-1L);
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
        var expectedEntityHistory = Entity.builder()
                .id(entity.getId())
                .declineReward(entity.getDeclineReward())
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .build();
        assertThat(findHistory(Entity.class))
                .usingRecursiveFieldByFieldElementComparatorOnFields(ENTITY_FIELDS)
                .containsExactly(expectedEntityHistory);
    }

    private IterableAssert<Entity> assertEntities() {
        return assertThat(findAllEntities()).usingRecursiveFieldByFieldElementComparatorOnFields(ENTITY_FIELDS);
    }

    private ListAssert<MigrationEntityStake> assertEntityStakes() {
        return assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pending_reward", "staked_node_id_start");
    }

    private void setupForMainnet() {
        importerProperties.setNetwork(HederaNetwork.MAINNET);
        lastHapi26EpochDay = Utility.getEpochDay(LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET);
        // Persist last Hapi version 26 RecordFile
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET)
                        .consensusStart(LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET - 2 * 1_000_000_000))
                .persist();
    }
}
