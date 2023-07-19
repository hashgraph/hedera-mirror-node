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

import com.hedera.mirror.importer.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.jdbc.core.RowMapper;

abstract class AbstractStakingMigrationTest extends IntegrationTest {

    private static final RowMapper<MigrationEntityStake> ENTITY_STAKE_ROW_MAPPER =
            rowMapper(MigrationEntityStake.class);

    protected List<MigrationEntityStake> findAllEntityStakes() {
        return jdbcOperations.query("select * from entity_stake", ENTITY_STAKE_ROW_MAPPER);
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
