package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.io.File;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.1")
class FixStakePeriodStartMigrationTest extends IntegrationTest {

    private final JdbcOperations jdbcOperations;
    @Value("classpath:db/migration/v1/V1.68.2.1__fix_stake_period_start.sql")
    private final File migrationSql;
    private final EntityRepository entityRepository;

    @Test
    void empty() {
        migrate();
        assertThat(entityRepository.findAll()).isEmpty();
    }

    @Test
    void fixStakePeriodStart() {
        // given
        // two staking reward transfers for an account, the entity lower timestamp is the first reward payout's timestamp
        // the migration should fix the stake period start
        long transferTimestamp1 = domainBuilder.timestamp() + 100L;
        long transferTimestamp2 = transferTimestamp1 + Duration.ofDays(1).toNanos();
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(1L).stakePeriodStart(Utility.getEpochDay(transferTimestamp2))
                        .timestampRange(Range.atLeast(transferTimestamp1)))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp1))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp2))
                .persist();

        // when
        migrate();

        // then
        entity.setStakePeriodStart(entity.getStakePeriodStart() - 1);
        assertEntities().containsExactly(entity);
    }

    @Test
    void fixStakePeriodStartHistory() {
        // given
        long createdTimestamp = domainBuilder.timestamp();
        long updateTimestamp1 = createdTimestamp + Duration.ofDays(5).toNanos();
        var entityHistoryWrapper = domainBuilder.entityHistory()
                .customize(e -> e.createdTimestamp(createdTimestamp)
                        .stakedNodeId(-1L)
                        .stakePeriodStart(-1L)
                        .timestampRange(Range.closedOpen(createdTimestamp, updateTimestamp1))
                );
        var entityHistory1 = entityHistoryWrapper.persist();
        long rewardTimestamp1 = updateTimestamp1 + Duration.ofDays(2).toNanos();
        long rewardTimestamp2 = rewardTimestamp1 + Duration.ofDays(2).toNanos();
        long updateTimestamp2 = rewardTimestamp2 + Duration.ofDays(3).toNanos();
        var entityHistory2 = entityHistoryWrapper
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(Utility.getEpochDay(rewardTimestamp2))
                        .timestampRange(Range.closedOpen(updateTimestamp1, updateTimestamp2))
                )
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entityHistory1.getId()).consensusTimestamp(rewardTimestamp1))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entityHistory1.getId()).consensusTimestamp(rewardTimestamp2))
                .persist();
        var entity = domainBuilder.entity()
                .customize(e -> e.createdTimestamp(createdTimestamp)
                        .id(entityHistory1.getId())
                        .num(entityHistory1.getNum())
                        .timestampRange(Range.atLeast(updateTimestamp2)))
                .persist();

        // when
        migrate();

        // then
        entityHistory2.setStakePeriodStart(entityHistory2.getStakePeriodStart() - 1);
        assertEntities().containsExactly(entity);
        assertHistoryEntities().containsExactlyInAnyOrder(entityHistory1, entityHistory2);
    }

    @Test
    void noopWithCryptoUpdateAfterStakingRewardTransfer() {
        // given
        // account has crypto update transaction after its staking reward, the migration should not change its stake
        // period start
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(2L).stakePeriodStart(2000L)
                        .timestampRange(Range.atLeast(transferTimestamp)))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist();
        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopForDeletedAccount() {
        // given
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder.entity()
                .customize(e -> e.deleted(true).stakedNodeId(3L).stakePeriodStart(3000L)
                        .timestampRange(Range.atLeast(transferTimestamp + 1L)))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist(); // deleted

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopNoLongerStakedToNode() {
        // given
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder.entity()
                .customize(e -> e.stakedNodeId(-1L).stakePeriodStart(-1L)
                        .timestampRange(Range.atLeast(transferTimestamp + 1L)))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist();

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopDeclinedReward() {
        // given
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder.entity()
                .customize(e -> e.declineReward(true).stakedNodeId(5L).stakePeriodStart(5000L)
                        .timestampRange(Range.atLeast(transferTimestamp + 1L)))
                .persist();
        domainBuilder.stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist(); // decline reward

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopNoStakingRewardTransfer() {
        // given
        var entity = domainBuilder.entity().persist();

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    private IterableAssert<Entity> assertEntities() {
        return assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "declineReward", "stakedNodeId",
                        "stakePeriodStart");
    }

    private IterableAssert<EntityHistory> assertHistoryEntities() {
        return assertThat((Iterable<EntityHistory>) findHistory(EntityHistory.class, "id", "entity"))
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "declineReward", "stakedNodeId",
                        "stakePeriodStart", "timestampRange");
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
