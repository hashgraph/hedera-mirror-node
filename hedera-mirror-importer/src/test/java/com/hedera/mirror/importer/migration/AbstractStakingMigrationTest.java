/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.importer.IntegrationTest;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.RowMapper;

@Import(DisablePartitionMaintenanceConfiguration.class)
abstract class AbstractStakingMigrationTest extends IntegrationTest {

    private static final RowMapper<Entity> ENTITY_ROW_MAPPER = rowMapper(Entity.class);

    private static final RowMapper<MigrationEntityStake> ENTITY_STAKE_ROW_MAPPER =
            rowMapper(MigrationEntityStake.class);

    protected Iterable<Entity> findAllEntities() {
        return jdbcOperations.query("select * from entity", ENTITY_ROW_MAPPER);
    }

    protected List<MigrationEntityStake> findAllEntityStakes() {
        return jdbcOperations.query("select * from entity_stake", ENTITY_STAKE_ROW_MAPPER);
    }

    protected void persistEntity(Entity entity) {
        jdbcOperations.update(
                "insert into entity (balance, decline_reward, deleted, id, num, realm, shard, staked_node_id, stake_period_start, timestamp_range, type) "
                        + "values (?,?,?,?,?,?,?,?,?,?::int8range,?::entity_type)",
                entity.getBalance(),
                entity.getDeclineReward(),
                entity.getDeleted(),
                entity.getId(),
                entity.getNum(),
                entity.getRealm(),
                entity.getShard(),
                entity.getStakedNodeId(),
                entity.getStakePeriodStart(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()),
                entity.getType().toString());
    }

    protected void persistEntityHistory(EntityHistory entityHistory) {
        jdbcOperations.update(
                "insert into entity_history (balance, created_timestamp, deleted, id, num, realm, shard, staked_node_id, stake_period_start, timestamp_range, type) "
                        + "values (?,?,?,?,?,?,?,?,?,?::int8range,?::entity_type)",
                entityHistory.getBalance(),
                entityHistory.getCreatedTimestamp(),
                entityHistory.getDeleted(),
                entityHistory.getId(),
                entityHistory.getNum(),
                entityHistory.getRealm(),
                entityHistory.getShard(),
                entityHistory.getStakedNodeId(),
                entityHistory.getStakePeriodStart(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(entityHistory.getTimestampRange()),
                entityHistory.getType().toString());
    }

    protected void persistEntityStakes(MigrationEntityStake... entityStakes) {
        jdbcOperations.batchUpdate(
                """
                        insert into entity_stake (decline_reward_start, end_stake_period, id, pending_reward,
                          staked_node_id_start, staked_to_me, stake_total_start)
                          values (?, ?, ?, ?, ?, ?, ?)
                        """,
                Arrays.asList(entityStakes),
                entityStakes.length,
                (ps, entityStake) -> {
                    ps.setBoolean(1, entityStake.isDeclineRewardStart());
                    ps.setLong(2, entityStake.getEndStakePeriod());
                    ps.setLong(3, entityStake.getId());
                    ps.setLong(4, entityStake.getPendingReward());
                    ps.setLong(5, entityStake.getStakedNodeIdStart());
                    ps.setLong(6, entityStake.getStakedToMe());
                    ps.setLong(7, entityStake.getStakeTotalStart());
                });
    }

    @AllArgsConstructor
    @Builder
    @Data
    static class MigrationEntityStake {
        private boolean declineRewardStart;
        private long endStakePeriod;
        private long id;
        private long pendingReward;
        private long stakedNodeIdStart;
        private long stakedToMe;
        private long stakeTotalStart;
    }
}
