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
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.importer.IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class GenericUpsertQueryGeneratorTest extends IntegrationTest {

    private static final SqlFormatter.Formatter SQL_FORMATTER = SqlFormatter.of(Dialect.PostgreSql);
    private final UpsertQueryGeneratorFactory factory;

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
    void getInsertQueryHistory() {
        UpsertQueryGenerator generator = factory.get(Contract.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getInsertQuery())).isEqualTo(format("with existing as (" +
                "  select" +
                "    e.auto_renew_account_id as e_auto_renew_account_id," +
                "    e.auto_renew_period as e_auto_renew_period," +
                "    e.created_timestamp as e_created_timestamp," +
                "    e.deleted as e_deleted," +
                "    e.evm_address as e_evm_address," +
                "    e.expiration_timestamp as e_expiration_timestamp," +
                "    e.file_id as e_file_id," +
                "    e.id as e_id," +
                "    e.initcode as e_initcode," +
                "    e.key as e_key," +
                "    e.max_automatic_token_associations as e_max_automatic_token_associations," +
                "    e.memo as e_memo," +
                "    e.num as e_num," +
                "    e.obtainer_id as e_obtainer_id," +
                "    e.permanent_removal as e_permanent_removal," +
                "    e.proxy_account_id as e_proxy_account_id," +
                "    e.public_key as e_public_key," +
                "    e.realm as e_realm," +
                "    e.shard as e_shard," +
                "    e.timestamp_range as e_timestamp_range," +
                "    e.type as e_type," +
                "    t.*" +
                "  from contract_temp t" +
                "  left join contract e on e.id = t.id" +
                ")," +
                "existing_history as (" +
                "  insert into contract_history (" +
                "    auto_renew_account_id,auto_renew_period, created_timestamp, deleted, evm_address," +
                "    expiration_timestamp, file_id, id, initcode, key, max_automatic_token_associations, memo, " +
                "    num, obtainer_id, permanent_removal, proxy_account_id, public_key, realm, shard, " +
                "    timestamp_range,type" +
                "  )" +
                "  select" +
                "    distinct on (id) e_auto_renew_account_id," +
                "    e_auto_renew_period," +
                "    e_created_timestamp," +
                "    e_deleted," +
                "    e_evm_address," +
                "    e_expiration_timestamp," +
                "    e_file_id," +
                "    e_id," +
                "    e_initcode," +
                "    e_key," +
                "    e_max_automatic_token_associations," +
                "    e_memo," +
                "    e_num," +
                "    e_obtainer_id," +
                "    e_permanent_removal," +
                "    e_proxy_account_id," +
                "    e_public_key," +
                "    e_realm," +
                "    e_shard," +
                "    int8range(lower(e_timestamp_range), lower(timestamp_range)) as timestamp_range," +
                "    e_type" +
                "  from existing" +
                "  where e_timestamp_range is not null and timestamp_range is not null" +
                "  order by id, timestamp_range asc" +
                ")," +
                "temp_history as (" +
                "  insert into contract_history (" +
                "    auto_renew_account_id, auto_renew_period, created_timestamp, deleted, evm_address," +
                "    expiration_timestamp, file_id, id, initcode, key, max_automatic_token_associations, memo, num," +
                "    obtainer_id, permanent_removal,proxy_account_id, public_key, realm, shard, timestamp_range, type" +
                "  )" +
                "  select distinct" +
                "    coalesce(auto_renew_account_id, e_auto_renew_account_id, null)," +
                "    coalesce(auto_renew_period, e_auto_renew_period, null)," +
                "    coalesce(created_timestamp, e_created_timestamp, null)," +
                "    coalesce(deleted, e_deleted, null)," +
                "    coalesce(evm_address, e_evm_address, null)," +
                "    coalesce(expiration_timestamp, e_expiration_timestamp, null)," +
                "    coalesce(file_id, e_file_id, null)," +
                "    coalesce(id, e_id, null)," +
                "    coalesce(initcode, e_initcode, null)," +
                "    coalesce(key, e_key, null)," +
                "    coalesce(max_automatic_token_associations, e_max_automatic_token_associations, null)," +
                "    coalesce(memo, e_memo, '')," +
                "    coalesce(num, e_num, null)," +
                "    coalesce(obtainer_id, e_obtainer_id, null)," +
                "    coalesce(permanent_removal, e_permanent_removal, null)," +
                "    coalesce(proxy_account_id, e_proxy_account_id, null)," +
                "    coalesce(public_key, e_public_key, null)," +
                "    coalesce(realm, e_realm, null)," +
                "    coalesce(shard, e_shard, null)," +
                "    coalesce(timestamp_range, e_timestamp_range, null)," +
                "    coalesce(type, e_type, 'CONTRACT')" +
                "  from existing" +
                "  where timestamp_range is not null and upper(timestamp_range) is not null" +
                ")" +
                "insert into contract (" +
                "  auto_renew_account_id, auto_renew_period, created_timestamp, deleted, evm_address," +
                "  expiration_timestamp, file_id, id, initcode, key, max_automatic_token_associations, memo, num," +
                "  obtainer_id, permanent_removal, proxy_account_id, public_key, realm, shard, timestamp_range, type" +
                ")" +
                "select" +
                "  coalesce(auto_renew_account_id, e_auto_renew_account_id, null)," +
                "  coalesce(auto_renew_period, e_auto_renew_period, null)," +
                "  coalesce(created_timestamp, e_created_timestamp, null)," +
                "  coalesce(deleted, e_deleted, null)," +
                "  coalesce(evm_address, e_evm_address, null)," +
                "  coalesce(expiration_timestamp, e_expiration_timestamp, null)," +
                "  coalesce(file_id, e_file_id, null)," +
                "  coalesce(id, e_id, null)," +
                "  coalesce(initcode, e_initcode, null)," +
                "  coalesce(key, e_key, null)," +
                "  coalesce(max_automatic_token_associations, e_max_automatic_token_associations, null)," +
                "  coalesce(memo, e_memo, '')," +
                "  coalesce(num, e_num, null)," +
                "  coalesce(obtainer_id, e_obtainer_id, null)," +
                "  coalesce(permanent_removal, e_permanent_removal, null)," +
                "  coalesce(proxy_account_id, e_proxy_account_id, null)," +
                "  coalesce(public_key, e_public_key, null)," +
                "  coalesce(realm, e_realm, null)," +
                "  coalesce(shard, e_shard, null)," +
                "  coalesce(timestamp_range, e_timestamp_range, null)," +
                "  coalesce(type, e_type, 'CONTRACT')" +
                "from existing " +
                "where (e_timestamp_range is not null and timestamp_range is null) or " +
                "(timestamp_range is not null and upper(timestamp_range) is null) on conflict (id) do " +
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
    void getInsertQueryNoHistory() {
        UpsertQueryGenerator generator = factory.get(Schedule.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getInsertQuery())).isEqualTo(format("with existing as (" +
                "  select " +
                "    e.consensus_timestamp as e_consensus_timestamp," +
                "    e.creator_account_id as e_creator_account_id," +
                "    e.executed_timestamp as e_executed_timestamp," +
                "    e.expiration_time as e_expiration_time," +
                "    e.payer_account_id as e_payer_account_id," +
                "    e.schedule_id as e_schedule_id," +
                "    e.transaction_body as e_transaction_body," +
                "    e.wait_for_expiry as e_wait_for_expiry," +
                "    t.*" +
                "  from schedule_temp t" +
                "  left join schedule e on e.schedule_id = t.schedule_id" +
                ")" +
                "insert into" +
                "  schedule (" +
                "    consensus_timestamp," +
                "    creator_account_id," +
                "    executed_timestamp," +
                "    expiration_time," +
                "    payer_account_id," +
                "    schedule_id," +
                "    transaction_body," +
                "    wait_for_expiry" +
                "  ) " +
                "select" +
                "  coalesce(consensus_timestamp, e_consensus_timestamp, null)," +
                "  coalesce(creator_account_id, e_creator_account_id, null)," +
                "  coalesce(executed_timestamp, e_executed_timestamp, null)," +
                "  coalesce(expiration_time, e_expiration_time, null)," +
                "  coalesce(payer_account_id, e_payer_account_id, null)," +
                "  coalesce(schedule_id, e_schedule_id, null)," +
                "  coalesce(transaction_body, e_transaction_body, null)," +
                "  coalesce(wait_for_expiry, e_wait_for_expiry, false) " +
                "from existing " +
                "where coalesce(consensus_timestamp, e_consensus_timestamp) is not null " +
                "on conflict (schedule_id) do update" +
                "  set executed_timestamp = excluded.executed_timestamp"));
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
