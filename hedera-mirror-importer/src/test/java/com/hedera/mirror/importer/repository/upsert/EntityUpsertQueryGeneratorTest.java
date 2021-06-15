package com.hedera.mirror.importer.repository.upsert;

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

class EntityUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {
    @Resource
    private EntityUpsertQueryGenerator entityRepositoryCustom;

    @Override
    public UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return entityRepositoryCustom;
    }

    @Override
    public String getInsertQuery() {
        return "insert into entity (auto_renew_account_id, auto_renew_period, created_timestamp, deleted, " +
                "expiration_timestamp, id, key, memo, modified_timestamp, num, proxy_account_id, public_key, realm, " +
                "shard, submit_key, type) select entity_temp.auto_renew_account_id, entity_temp.auto_renew_period, " +
                "entity_temp.created_timestamp, entity_temp.deleted, entity_temp.expiration_timestamp, entity_temp" +
                ".id, entity_temp.key, case when entity_temp.memo = '<uuid>' then '' else coalesce(entity_temp.memo, " +
                "'') " +
                "end, entity_temp.modified_timestamp, entity_temp.num, entity_temp.proxy_account_id, case when " +
                "entity_temp.public_key = '<uuid>' then '' else coalesce(entity_temp.public_key, null) end, " +
                "entity_temp" +
                ".realm, entity_temp.shard, entity_temp.submit_key, entity_temp.type from entity_temp " +
                "on conflict (id) do nothing";
    }

    @Override
    public String getUpdateQuery() {
        return "update entity set " +
                "auto_renew_account_id = coalesce(entity_temp.auto_renew_account_id, entity.auto_renew_account_id), " +
                "auto_renew_period = coalesce(entity_temp.auto_renew_period, entity.auto_renew_period), " +
                "deleted = coalesce(entity_temp.deleted, entity.deleted), " +
                "expiration_timestamp = coalesce(entity_temp.expiration_timestamp, entity.expiration_timestamp), " +
                "key = coalesce(entity_temp.key, entity.key), " +
                "memo = case when entity_temp.memo = '<uuid>' then '' else " +
                "coalesce(entity_temp.memo, entity.memo) end, " +
                "modified_timestamp = coalesce(entity_temp.modified_timestamp, entity.modified_timestamp), " +
                "proxy_account_id = coalesce(entity_temp.proxy_account_id, entity.proxy_account_id), " +
                "public_key = case when entity_temp.public_key = '<uuid>' then '' else " +
                "coalesce(entity_temp.public_key, entity.public_key) end, " +
                "submit_key = coalesce(entity_temp.submit_key, entity.submit_key) " +
                "from entity_temp where entity.id = entity_temp.id and entity_temp.created_timestamp is null";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(upsertQuery).isEqualTo("entity");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("entity_temp");
    }
}
