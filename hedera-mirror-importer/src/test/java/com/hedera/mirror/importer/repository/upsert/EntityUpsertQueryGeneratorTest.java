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
    protected UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return entityRepositoryCustom;
    }

    @Override
    protected String getInsertQuery() {
        return "insert into entity (auto_renew_account_id, auto_renew_period, created_timestamp, deleted, " +
                "expiration_timestamp, id, key, max_automatic_token_associations, memo, num, proxy_account_id, " +
                "public_key, realm, receiver_sig_required, shard, submit_key, timestamp_range, type) select " +
                "entity_temp.auto_renew_account_id, entity_temp.auto_renew_period, entity_temp.created_timestamp, " +
                "entity_temp.deleted, entity_temp.expiration_timestamp, entity_temp.id, entity_temp.key, " +
                "entity_temp.max_automatic_token_associations, coalesce(entity_temp.memo, ''), entity_temp.num, " +
                "entity_temp.proxy_account_id, entity_temp.public_key, entity_temp.realm, entity_temp" +
                ".receiver_sig_required, entity_temp.shard, entity_temp.submit_key, entity_temp.timestamp_range, " +
                "entity_temp.type from entity_temp on conflict (id) do nothing";
    }

    @Override
    protected String getUpdateQuery() {
        return "update entity set " +
                "auto_renew_account_id = coalesce(entity_temp.auto_renew_account_id, entity.auto_renew_account_id), " +
                "auto_renew_period = coalesce(entity_temp.auto_renew_period, entity.auto_renew_period), " +
                "deleted = coalesce(entity_temp.deleted, entity.deleted), expiration_timestamp = coalesce(entity_temp" +
                ".expiration_timestamp, entity.expiration_timestamp), key = coalesce(entity_temp.key, entity.key), " +
                "max_automatic_token_associations = coalesce(entity_temp.max_automatic_token_associations, " +
                "entity.max_automatic_token_associations), memo = coalesce(entity_temp.memo, entity.memo), " +
                "proxy_account_id = coalesce(entity_temp.proxy_account_id, entity.proxy_account_id), " +
                "public_key = coalesce(entity_temp.public_key, entity.public_key), " +
                "receiver_sig_required = coalesce(entity_temp.receiver_sig_required, entity.receiver_sig_required), " +
                "submit_key = coalesce(entity_temp.submit_key, entity.submit_key), " +
                "timestamp_range = coalesce(entity_temp.timestamp_range, entity.timestamp_range) " +
                "from entity_temp where entity.id = entity_temp.id and entity_temp.created_timestamp is null and " +
                "lower(entity_temp.timestamp_range) > 0";
    }

    @Test
    void tableName() {
        String tableName = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(tableName).isEqualTo("entity");
    }

    @Test
    void tempTableName() {
        String tempTableName = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(tempTableName).isEqualTo("entity_temp");
    }
}
