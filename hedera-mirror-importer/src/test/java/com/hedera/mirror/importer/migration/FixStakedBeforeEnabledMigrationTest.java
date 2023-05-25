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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.importer.migration.FixStakedBeforeEnabledMigration.LAST_HAPI_26_RECORD_FILE_CONSENSUS_END;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import com.hedera.mirror.importer.util.Utility;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.2.1")
class FixStakedBeforeEnabledMigrationTest extends IntegrationTest {

    private static final String[] ENTITY_FIELDS =
            new String[] {"id", "declineReward", "stakedNodeId", "stakePeriodStart"};

    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;
    private final MirrorProperties mirrorProperties;
    private final FixStakedBeforeEnabledMigration migration;

    private long lastHapi26RecordFileConsensusEnd;
    private long lastHapi26EpochDay;

    @AfterEach
    void teardown() {
        mirrorProperties.setNetwork(HederaNetwork.TESTNET);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertEntities().isEmpty();
        assertEntityStakes().isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void notStaked(String network) {
        // given
        setupForNetwork(network);
        var entity = domainBuilder
                .entity()
                .customize(e -> e.declineReward(false).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        var entityStake = domainBuilder
                .entityStake()
                .customize(es -> es.id(entity.getId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @Test
    void otherNetwork() {
        // given
        setupForNetwork(HederaNetwork.MAINNET);
        mirrorProperties.setNetwork(HederaNetwork.OTHER);
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(lastHapi26RecordFileConsensusEnd)))
                .persist();
        var entityStake = domainBuilder
                .entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void stakedAfterEnabled(String network) {
        // given
        setupForNetwork(network);
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(lastHapi26EpochDay + 1L))
                .persist();
        var entityStake = domainBuilder
                .entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void stakedAfterEnabledWithHistory(String network) {
        // given
        setupForNetwork(network);
        long stakingSetTimestamp = lastHapi26RecordFileConsensusEnd - 100L;
        long lastUpdateTimestamp = lastHapi26RecordFileConsensusEnd + 300L;
        // the history row has different setting and the current staking is set after 0.27.0 upgrade
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(lastUpdateTimestamp)))
                .persist();
        domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId())
                        .num(entity.getNum())
                        .stakedNodeId(1L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.closedOpen(stakingSetTimestamp, lastUpdateTimestamp)))
                .persist();
        var entityStake = domainBuilder
                .entityStake()
                .customize(es -> es.id(entity.getId()).pendingReward(1000L).stakedNodeIdStart(0L))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertEntities().containsExactly(entity);
        assertEntityStakes().containsExactly(entityStake);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void stakedBeforeEnabled(String network) {
        // given
        setupForNetwork(network);
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(lastHapi26RecordFileConsensusEnd)))
                .persist();
        var entityStake = domainBuilder
                .entityStake()
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

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void stakedBeforeEnabledInHistory(String network) {
        // given
        setupForNetwork(network);
        long stakingSetTimestamp = lastHapi26RecordFileConsensusEnd - 100L;
        long lastUpdateTimestamp = lastHapi26RecordFileConsensusEnd + 300L;
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.atLeast(lastUpdateTimestamp)))
                .persist();
        domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId())
                        .num(entity.getNum())
                        .stakedNodeId(0L)
                        .stakePeriodStart(lastHapi26EpochDay)
                        .timestampRange(Range.closedOpen(stakingSetTimestamp, lastUpdateTimestamp)))
                .persist();
        var entityStake = domainBuilder
                .entityStake()
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
        return assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(ENTITY_FIELDS);
    }

    private IterableAssert<EntityStake> assertEntityStakes() {
        return assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pending_reward", "staked_node_id_start");
    }

    private void persistLastHapi26RecordFile(long consensusEnd) {
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(consensusEnd).consensusStart(consensusEnd - 2 * 1_000_000_000))
                .persist();
    }

    private void setupForNetwork(String network) {
        mirrorProperties.setNetwork(network);
        lastHapi26RecordFileConsensusEnd = LAST_HAPI_26_RECORD_FILE_CONSENSUS_END.get(network);
        lastHapi26EpochDay = Utility.getEpochDay(lastHapi26RecordFileConsensusEnd);
        persistLastHapi26RecordFile(lastHapi26RecordFileConsensusEnd);
    }
}
