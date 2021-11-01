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

class ContractUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {
    @Resource
    private ContractUpsertQueryGenerator contractUpsertQueryGenerator;

    @Override
    protected UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return contractUpsertQueryGenerator;
    }

    @Override
    protected String getInsertQuery() {
        return "insert into contract (auto_renew_period, created_timestamp, deleted, expiration_timestamp, " +
                "file_id, id, key, memo, num, obtainer_id, parent_id, proxy_account_id, public_key, realm, shard, " +
                "timestamp_range, type) select coalesce(contract_temp.auto_renew_period, parent.auto_renew_period, " +
                "null), coalesce(contract_temp.created_timestamp, parent.created_timestamp, null), " +
                "coalesce(contract_temp.deleted, parent.deleted, null), coalesce(contract_temp.expiration_timestamp, " +
                "parent.expiration_timestamp, null), coalesce(contract_temp.file_id, parent.file_id, null), " +
                "contract_temp.id, coalesce(contract_temp.key, parent.key, null), " +
                "coalesce(contract_temp.memo, parent.memo, ''), contract_temp.num, coalesce(contract_temp" +
                ".obtainer_id, parent.obtainer_id, null), " +
                "contract_temp.parent_id, coalesce(contract_temp.proxy_account_id, " +
                "parent.proxy_account_id, null), coalesce(contract_temp.public_key, parent" +
                ".public_key, null), contract_temp.realm, contract_temp.shard, " +
                "coalesce(contract_temp.timestamp_range, parent.timestamp_range, null), contract_temp.type from " +
                "contract_temp left join contract parent on parent.id = contract_temp.parent_id " +
                "on conflict (id) do nothing";
    }

    @Override
    protected String getUpdateQuery() {
        return "update contract set " +
                "auto_renew_period = coalesce(contract_temp.auto_renew_period, contract.auto_renew_period), " +
                "deleted = coalesce(contract_temp.deleted, contract.deleted), " +
                "expiration_timestamp = coalesce(contract_temp.expiration_timestamp, contract.expiration_timestamp), " +
                "file_id = coalesce(contract_temp.file_id, contract.file_id), " +
                "key = coalesce(contract_temp.key, contract.key), " +
                "memo = coalesce(contract_temp.memo, contract.memo), " +
                "obtainer_id = coalesce(contract_temp.obtainer_id, contract.obtainer_id), " +
                "proxy_account_id = coalesce(contract_temp.proxy_account_id, contract.proxy_account_id), " +
                "public_key = coalesce(contract_temp.public_key, contract.public_key), " +
                "timestamp_range = coalesce(contract_temp.timestamp_range, contract.timestamp_range) " +
                "from contract_temp where contract.id = contract_temp.id and contract_temp.created_timestamp is null " +
                "and lower(contract_temp.timestamp_range) > 0";
    }

    @Test
    void tableName() {
        String tableName = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(tableName).isEqualTo("contract");
    }

    @Test
    void tempTableName() {
        String tempTableName = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(tempTableName).isEqualTo("contract_temp");
    }
}
