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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.EntityRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class EntityContractTypeMigrationTest extends IntegrationTest {

    private final EntityContractTypeMigration entityMigration;
    private final EntityRepository entityRepository;
    private final JdbcTemplate jdbcTemplate;

    @Test
    void migrateWhenNull() {
        entityMigration.doMigrate(null);
        assertThat(entityRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenEmpty() {
        entityMigration.doMigrate(Collections.emptyList());
        assertThat(entityRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var entities = new ArrayList<Entity>();
        for (int i = 0; i < 65000; i++) {
            entities.add(entityCreator());
        }

        persistEntities(entities);
        var contractIds = entities.stream().map(Entity::getId).toList();

        // when
        entityMigration.doMigrate(contractIds);

        // then
        assertThat(entityRepository.findAll())
                .extracting(Entity::getType).containsOnly(CONTRACT);
    }

    private void persistEntities(List<Entity> entities) {
        jdbcTemplate.batchUpdate(
                "insert into entity (decline_reward, id, memo, num, realm, shard, timestamp_range, type) " +
                        "values (?, ?, ?, ?, ?, ?, ?::int8range, ?::entity_type)",
                entities,
                entities.size(),
                (ps, entity) -> {
                    ps.setBoolean(1, entity.getDeclineReward());
                    ps.setLong(2, entity.getId());
                    ps.setString(3, entity.getMemo());
                    ps.setLong(4, entity.getNum());
                    ps.setLong(5, entity.getRealm());
                    ps.setLong(6, entity.getShard());
                    ps.setString(7, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
                    ps.setString(8, entity.getType().toString());
                }
        );
    }

    private Entity entityCreator() {
        return Entity.builder()
                .id(domainBuilder.id())
                .declineReward(false)
                .memo(StringUtils.EMPTY)
                .num(domainBuilder.id())
                .realm(0L)
                .shard(0L)
                .timestampRange(Range.atLeast(domainBuilder.timestamp()))
                .type(ACCOUNT)
                .build();
    }
}
