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
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.EntityRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.1")
class FixStakePeriodStartMigrationTest extends IntegrationTest {

    private final JdbcOperations jdbcOperations;
    @Value("classpath:db/migration/v1/V1.68.2__fix_stake_period_start.sql")
    private final File migrationSql;
    private final EntityRepository entityRepository;

    @Test
    void empty() {
        migrate();
        assertThat(entityRepository.findAll()).isEmpty();
    }

    @Test
    void fixStatePeriodStart() {
        // given
        // two staking reward transfers for an account, the entity lower timestamp is the first reward payout's timestamp
        // the migration should fix the stake period start
        long account = domainBuilder.id();
        var transfer = domainBuilder.stakingRewardTransfer(account).persist();
        domainBuilder.stakingRewardTransfer(account)
                .customize(t -> t.consensusTimestamp(transfer.getConsensusTimestamp() + Duration.ofDays(1L).toNanos()))
                .persist();
        var entity = domainBuilder.entity()
                .customize(e -> e.id(account).num(account)
                        .stakedNodeId(1L)
                        .stakePeriodStart(1000L)
                        .timestampRange(Range.atLeast(transfer.getConsensusTimestamp())))
                .persist();

        // when
        migrate();

        // then
        entity.setStakePeriodStart(entity.getStakePeriodStart() - 1);
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopWithCyptoUpdateAfterStakingRewardTransfer() {
        // given
        // account has crypto update transaction after its staking reward, the migration should not change its stake
        // period start
        long account = domainBuilder.id();
        var transfer2 = domainBuilder.stakingRewardTransfer(account).persist();
        var entity = domainBuilder.entity()
                .customize(e -> e.id(account).num(account)
                        .stakedNodeId(2L)
                        .stakePeriodStart(2000L)
                        .timestampRange(Range.atLeast(transfer2.getConsensusTimestamp() + 1L)))
                .persist();

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopForDeletedAccount() {
        // given
        long account = domainBuilder.id();
        var transfer = domainBuilder.stakingRewardTransfer(account).persist(); // deleted
        var entity = domainBuilder.entity()
                .customize(e -> e.deleted(true).id(account).num(account)
                        .stakedNodeId(3L)
                        .stakePeriodStart(3000L)
                        .timestampRange(Range.atLeast(transfer.getConsensusTimestamp() - 1L)))
                .persist();

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopNoLongerStakedToNode() {
        // given
        long account = domainBuilder.id();
        var transfer = domainBuilder.stakingRewardTransfer(account).persist();
        var entity = domainBuilder.entity()
                .customize(e -> e.id(account).num(account)
                        .stakedNodeId(-1L)
                        .stakePeriodStart(-1L)
                        .timestampRange(Range.atLeast(transfer.getConsensusTimestamp() + 1L)))
                .persist();

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopDeclinedReward() {
        // given
        long account = domainBuilder.id();
        var transfer = domainBuilder.stakingRewardTransfer(account).persist(); // decline reward
        var entity = domainBuilder.entity()
                .customize(e -> e.id(account).num(account)
                        .declineReward(true)
                        .stakedNodeId(5L)
                        .stakePeriodStart(5000L)
                        .timestampRange(Range.atLeast(transfer.getConsensusTimestamp() + 1L)))
                .persist();

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
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "declineReward", "stakedNodoeId",
                        "stakePeriodStart");
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
