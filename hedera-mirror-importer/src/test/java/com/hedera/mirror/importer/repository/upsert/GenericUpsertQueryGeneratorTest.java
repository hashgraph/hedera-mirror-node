package com.hedera.mirror.importer.repository.upsert;

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

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.importer.IntegrationTest;

class GenericUpsertQueryGeneratorTest extends IntegrationTest {

    private static final SqlFormatter.Formatter SQL_FORMATTER = SqlFormatter.of(Dialect.PostgreSql);

    @Resource
    private UpsertQueryGeneratorFactory factory;

    @Test
    void getCreateTempTableQuery() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(generator.getCreateTempTableQuery()).isEqualTo(
                "create temporary table if not exists contract_temp on commit drop as table contract limit 0");
    }

    @Test
    void getCreateTempIndexQuery() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(generator.getCreateTempIndexQuery()).isEqualTo(
                "create index if not exists contract_temp_idx on contract_temp (id)");
    }

    @Test
    void getFinalTableName() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(generator.getFinalTableName()).isEqualTo("contract");
    }

    @Test
    void getTemporaryTableName() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(generator.getTemporaryTableName()).isEqualTo("contract_temp");
    }

    @Test
    void getInsertQuery() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getInsertQuery())).isEqualTo(format("with existing as (" +
                "  insert into contract_history (" +
                "    auto_renew_account_id,auto_renew_period, created_timestamp, deleted, evm_address," +
                "    expiration_timestamp, file_id, id, initcode, key, max_automatic_token_associations, memo, " +
                "    num, obtainer_id, permanent_removal, proxy_account_id, public_key, realm, shard, timestamp_range," +
                "    type" +
                "  )" +
                "  select" +
                "    e.auto_renew_account_id," +
                "    e.auto_renew_period," +
                "    e.created_timestamp," +
                "    e.deleted," +
                "    e.evm_address," +
                "    e.expiration_timestamp," +
                "    e.file_id," +
                "    e.id," +
                "    e.initcode," +
                "    e.key," +
                "    e.max_automatic_token_associations," +
                "    e.memo," +
                "    e.num," +
                "    e.obtainer_id," +
                "    e.permanent_removal," +
                "    e.proxy_account_id," +
                "    e.public_key," +
                "    e.realm," +
                "    e.shard," +
                "    int8range(min(lower(e.timestamp_range)), min(lower(t.timestamp_range))) as timestamp_range," +
                "    e.type" +
                "  from contract e " +
                "  join contract_temp t on e.id = t.id" +
                "  group by e.id" +
                "  order by e.id, e.timestamp_range returning *" +
                ")," +
                "history as (" +
                "  insert into contract_history (" +
                "    auto_renew_account_id, auto_renew_period, created_timestamp, deleted, evm_address," +
                "    expiration_timestamp, file_id, id, initcode, key, max_automatic_token_associations, memo, num," +
                "    obtainer_id, permanent_removal,proxy_account_id, public_key, realm, shard, timestamp_range, type" +
                "  )" +
                "  select distinct" +
                "    coalesce(t.auto_renew_account_id, e.auto_renew_account_id, null)," +
                "    coalesce(t.auto_renew_period, e.auto_renew_period, null)," +
                "    coalesce(t.created_timestamp, e.created_timestamp, null)," +
                "    coalesce(t.deleted, e.deleted, null)," +
                "    coalesce(t.evm_address, e.evm_address, null)," +
                "    coalesce(t.expiration_timestamp, e.expiration_timestamp, null)," +
                "    coalesce(t.file_id, e.file_id, null)," +
                "    coalesce(t.id, e.id, null)," +
                "    coalesce(t.initcode, e.initcode, null)," +
                "    coalesce(t.key, e.key, null)," +
                "    coalesce(t.max_automatic_token_associations, e.max_automatic_token_associations, null)," +
                "    coalesce(t.memo, e.memo, '')," +
                "    coalesce(t.num, e.num, null)," +
                "    coalesce(t.obtainer_id, e.obtainer_id, null)," +
                "    coalesce(t.permanent_removal, e.permanent_removal, null)," +
                "    coalesce(t.proxy_account_id, e.proxy_account_id, null)," +
                "    coalesce(t.public_key, e.public_key, null)," +
                "    coalesce(t.realm, e.realm, null)," +
                "    coalesce(t.shard, e.shard, null)," +
                "    coalesce(t.timestamp_range, e.timestamp_range, null)," +
                "    coalesce(t.type, e.type, 'CONTRACT')" +
                "  from contract_temp t " +
                "  left join existing e on e.id = t.id" +
                "  where upper(t.timestamp_range) is not null returning *" +
                ")" +
                "insert into contract (" +
                "  auto_renew_account_id, auto_renew_period, created_timestamp, deleted, evm_address," +
                "  expiration_timestamp, file_id, id, initcode, key, max_automatic_token_associations, memo, num," +
                "  obtainer_id, permanent_removal, proxy_account_id, public_key, realm, shard, timestamp_range, type" +
                ")" +
                "select" +
                "  coalesce(t.auto_renew_account_id, e.auto_renew_account_id, null)," +
                "  coalesce(t.auto_renew_period, e.auto_renew_period, null)," +
                "  coalesce(t.created_timestamp, e.created_timestamp, null)," +
                "  coalesce(t.deleted, e.deleted, null)," +
                "  coalesce(t.evm_address, e.evm_address, null)," +
                "  coalesce(t.expiration_timestamp, e.expiration_timestamp, null)," +
                "  coalesce(t.file_id, e.file_id, null)," +
                "  coalesce(t.id, e.id, null)," +
                "  coalesce(t.initcode, e.initcode, null)," +
                "  coalesce(t.key, e.key, null)," +
                "  coalesce(t.max_automatic_token_associations, e.max_automatic_token_associations, null)," +
                "  coalesce(t.memo, e.memo, '')," +
                "  coalesce(t.num, e.num, null)," +
                "  coalesce(t.obtainer_id, e.obtainer_id, null)," +
                "  coalesce(t.permanent_removal, e.permanent_removal, null)," +
                "  coalesce(t.proxy_account_id, e.proxy_account_id, null)," +
                "  coalesce(t.public_key, e.public_key, null)," +
                "  coalesce(t.realm, e.realm, null)," +
                "  coalesce(t.shard, e.shard, null)," +
                "  coalesce(t.timestamp_range, e.timestamp_range, null)," +
                "  coalesce(t.type, e.type, 'CONTRACT')" +
                "from contract_temp t " +
                "left join existing e on e.id = t.id " +
                "where upper(t.timestamp_range) is null on conflict (id) do " +
                "update set" +
                "  auto_renew_account_id = excluded.auto_renew_account_id," +
                "  auto_renew_period = excluded.auto_renew_period," +
                "  deleted = excluded.deleted," +
                "  expiration_timestamp = excluded.expiration_timestamp," +
                "  key = excluded.key," +
                "  max_automatic_token_associations = excluded.max_automatic_token_associations," +
                "  memo = excluded.memo," +
                "  obtainer_id = excluded.obtainer_id," +
                "  permanent_removal = excluded.permanent_removal," +
                "  proxy_account_id = excluded.proxy_account_id," +
                "  public_key = excluded.public_key," +
                "  timestamp_range = excluded.timestamp_range"));
    }

    @Test
    void getUpdateQuery() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(format(generator.getUpdateQuery())).isEmpty();
    }

    private String format(String sql) {
        return SQL_FORMATTER.format(sql);
    }
}
