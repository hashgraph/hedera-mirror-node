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

public class EntityRepositoryCustomImplTest extends AbstractRepositoryTest {

    @Resource
    private EntityRepository entityRepository;
    @Resource
    private EntityRepositoryCustomImpl entityRepositoryCustom;

    @Test
    void tableName() {
        String upsertQuery = entityRepositoryCustom.getTableName();
        assertThat(upsertQuery).isEqualTo("entity");
    }

    @Test
    void tempTableName() {
        String upsertQuery = entityRepositoryCustom.getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("entity_temp");
    }

    @Test
    void upsert() {
        String upsertQuery = entityRepositoryCustom.getUpsertQuery();
        assertThat(upsertQuery).isNotEmpty();
        assertThat(upsertQuery).contains("insert into entity");
        assertThat(upsertQuery).contains("select");
        assertThat(upsertQuery).contains("excluded.");
        assertThat(upsertQuery).contains("on conflict (id) do update set");
    }
}
