package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

class EntityIdRepositoryCustomImplTest extends AbstractRepositoryCustomImplTest {
    @Resource
    private EntityIdRepositoryCustomImpl entityIdRepositoryCustom;

    @Override
    public UpdatableDomainRepositoryCustom getUpdatableDomainRepositoryCustom() {
        return entityIdRepositoryCustom;
    }

    @Override
    public String getInsertQuery() {
        return "insert into entity (id, memo, num, realm, shard, type) select entity_id_temp.id, case when " +
                "entity_id_temp.memo = ' ' then '' else coalesce(entity_id_temp.memo, '') end, entity_id_temp.num, " +
                "entity_id_temp.realm, entity_id_temp.shard, entity_id_temp.type from entity_id_temp on conflict (id)" +
                " do nothing";
    }

    @Override
    public String getUpdateQuery() {
        return "";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTableName();
        assertThat(upsertQuery).isEqualTo("entity");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("entity_id_temp");
    }
}
