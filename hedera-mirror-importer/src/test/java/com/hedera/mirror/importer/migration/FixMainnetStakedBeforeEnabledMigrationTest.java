package com.hedera.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
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
    private final JdbcOperations jdbcOperations;
    @Value("classpath:db/migration/v1/R__fix_mainnet_staked_before_enabled.sql")
    private final File migrationSql;

    @Test
    void empty() {
        migrate();
        assertEntities().isEmpty();
        assertEntityStakes().isEmpty();
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
        migrate();

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
        migrate();

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
        migrate();

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
        migrate();

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
        migrate();

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

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
