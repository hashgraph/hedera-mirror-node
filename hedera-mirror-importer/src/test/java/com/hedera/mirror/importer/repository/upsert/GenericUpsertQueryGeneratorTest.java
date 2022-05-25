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
        assertThat(format(generator.getInsertQuery())).isEqualTo(format("with existing as (\n" +
                "  select\n" +
                "    e.auto_renew_account_id as e_auto_renew_account_id,\n" +
                "    e.auto_renew_period as e_auto_renew_period,\n" +
                "    e.created_timestamp as e_created_timestamp,\n" +
                "    e.decline_reward as e_decline_reward,\n" +
                "    e.deleted as e_deleted,\n" +
                "    e.evm_address as e_evm_address,\n" +
                "    e.expiration_timestamp as e_expiration_timestamp,\n" +
                "    e.file_id as e_file_id,\n" +
                "    e.id as e_id,\n" +
                "    e.initcode as e_initcode,\n" +
                "    e.key as e_key,\n" +
                "    e.max_automatic_token_associations as e_max_automatic_token_associations,\n" +
                "    e.memo as e_memo,\n" +
                "    e.num as e_num,\n" +
                "    e.obtainer_id as e_obtainer_id,\n" +
                "    e.permanent_removal as e_permanent_removal,\n" +
                "    e.proxy_account_id as e_proxy_account_id,\n" +
                "    e.public_key as e_public_key,\n" +
                "    e.realm as e_realm,\n" +
                "    e.shard as e_shard,\n" +
                "    e.stake_period_start as e_stake_period_start,\n" +
                "    e.staked_account_id as e_staked_account_id,\n" +
                "    e.staked_node_id as e_staked_node_id,\n" +
                "    e.timestamp_range as e_timestamp_range,\n" +
                "    e.type as e_type,\n" +
                "    t.*\n" +
                "  from\n" +
                "    contract_temp t\n" +
                "    left join contract e on e.id = t.id\n" +
                "),\n" +
                "existing_history as (\n" +
                "  insert into\n" +
                "    contract_history (\n" +
                "      auto_renew_account_id,\n" +
                "      auto_renew_period,\n" +
                "      created_timestamp,\n" +
                "      decline_reward,\n" +
                "      deleted,\n" +
                "      evm_address,\n" +
                "      expiration_timestamp,\n" +
                "      file_id,\n" +
                "      id,\n" +
                "      initcode,\n" +
                "      key,\n" +
                "      max_automatic_token_associations,\n" +
                "      memo,\n" +
                "      num,\n" +
                "      obtainer_id,\n" +
                "      permanent_removal,\n" +
                "      proxy_account_id,\n" +
                "      public_key,\n" +
                "      realm,\n" +
                "      shard,\n" +
                "      stake_period_start,\n" +
                "      staked_account_id,\n" +
                "      staked_node_id,\n" +
                "      timestamp_range,\n" +
                "      type\n" +
                "    )\n" +
                "  select\n" +
                "    distinct on (id) e_auto_renew_account_id,\n" +
                "    e_auto_renew_period,\n" +
                "    e_created_timestamp,\n" +
                "    e_decline_reward,\n" +
                "    e_deleted,\n" +
                "    e_evm_address,\n" +
                "    e_expiration_timestamp,\n" +
                "    e_file_id,\n" +
                "    e_id,\n" +
                "    e_initcode,\n" +
                "    e_key,\n" +
                "    e_max_automatic_token_associations,\n" +
                "    e_memo,\n" +
                "    e_num,\n" +
                "    e_obtainer_id,\n" +
                "    e_permanent_removal,\n" +
                "    e_proxy_account_id,\n" +
                "    e_public_key,\n" +
                "    e_realm,\n" +
                "    e_shard,\n" +
                "    e_stake_period_start,\n" +
                "    e_staked_account_id,\n" +
                "    e_staked_node_id,\n" +
                "    int8range(lower(e_timestamp_range), lower(timestamp_range)) as timestamp_range,\n" +
                "    e_type\n" +
                "  from\n" +
                "    existing\n" +
                "  where\n" +
                "    e_timestamp_range is not null\n" +
                "    and timestamp_range is not null\n" +
                "  order by\n" +
                "    id,\n" +
                "    timestamp_range asc\n" +
                "),\n" +
                "temp_history as (\n" +
                "  insert into\n" +
                "    contract_history (\n" +
                "      auto_renew_account_id,\n" +
                "      auto_renew_period,\n" +
                "      created_timestamp,\n" +
                "      decline_reward,\n" +
                "      deleted,\n" +
                "      evm_address,\n" +
                "      expiration_timestamp,\n" +
                "      file_id,\n" +
                "      id,\n" +
                "      initcode,\n" +
                "      key,\n" +
                "      max_automatic_token_associations,\n" +
                "      memo,\n" +
                "      num,\n" +
                "      obtainer_id,\n" +
                "      permanent_removal,\n" +
                "      proxy_account_id,\n" +
                "      public_key,\n" +
                "      realm,\n" +
                "      shard,\n" +
                "      stake_period_start,\n" +
                "      staked_account_id,\n" +
                "      staked_node_id,\n" +
                "      timestamp_range,\n" +
                "      type\n" +
                "    )\n" +
                "  select\n" +
                "    distinct coalesce(\n" +
                "      auto_renew_account_id,\n" +
                "      e_auto_renew_account_id,\n" +
                "      null\n" +
                "    ),\n" +
                "    coalesce(auto_renew_period, e_auto_renew_period, null),\n" +
                "    coalesce(created_timestamp, e_created_timestamp, null),\n" +
                "    coalesce(decline_reward, e_decline_reward, false),\n" +
                "    coalesce(deleted, e_deleted, null),\n" +
                "    coalesce(evm_address, e_evm_address, null),\n" +
                "    coalesce(expiration_timestamp, e_expiration_timestamp, null),\n" +
                "    coalesce(file_id, e_file_id, null),\n" +
                "    coalesce(id, e_id, null),\n" +
                "    coalesce(initcode, e_initcode, null),\n" +
                "    coalesce(key, e_key, null),\n" +
                "    coalesce(\n" +
                "      max_automatic_token_associations,\n" +
                "      e_max_automatic_token_associations,\n" +
                "      null\n" +
                "    ),\n" +
                "    coalesce(memo, e_memo, ''),\n" +
                "    coalesce(num, e_num, null),\n" +
                "    coalesce(obtainer_id, e_obtainer_id, null),\n" +
                "    coalesce(permanent_removal, e_permanent_removal, null),\n" +
                "    coalesce(proxy_account_id, e_proxy_account_id, null),\n" +
                "    coalesce(public_key, e_public_key, null),\n" +
                "    coalesce(realm, e_realm, null),\n" +
                "    coalesce(shard, e_shard, null),\n" +
                "    coalesce(stake_period_start, e_stake_period_start, null),\n" +
                "    coalesce(staked_account_id, e_staked_account_id, null),\n" +
                "    coalesce(staked_node_id, e_staked_node_id, null),\n" +
                "    coalesce(timestamp_range, e_timestamp_range, null),\n" +
                "    coalesce(type, e_type, 'CONTRACT')\n" +
                "  from\n" +
                "    existing\n" +
                "  where\n" +
                "    timestamp_range is not null\n" +
                "    and upper(timestamp_range) is not null\n" +
                ")\n" +
                "insert into\n" +
                "  contract (\n" +
                "    auto_renew_account_id,\n" +
                "    auto_renew_period,\n" +
                "    created_timestamp,\n" +
                "    decline_reward,\n" +
                "    deleted,\n" +
                "    evm_address,\n" +
                "    expiration_timestamp,\n" +
                "    file_id,\n" +
                "    id,\n" +
                "    initcode,\n" +
                "    key,\n" +
                "    max_automatic_token_associations,\n" +
                "    memo,\n" +
                "    num,\n" +
                "    obtainer_id,\n" +
                "    permanent_removal,\n" +
                "    proxy_account_id,\n" +
                "    public_key,\n" +
                "    realm,\n" +
                "    shard,\n" +
                "    stake_period_start,\n" +
                "    staked_account_id,\n" +
                "    staked_node_id,\n" +
                "    timestamp_range,\n" +
                "    type\n" +
                "  )\n" +
                "select\n" +
                "  coalesce(\n" +
                "    auto_renew_account_id,\n" +
                "    e_auto_renew_account_id,\n" +
                "    null\n" +
                "  ),\n" +
                "  coalesce(auto_renew_period, e_auto_renew_period, null),\n" +
                "  coalesce(created_timestamp, e_created_timestamp, null),\n" +
                "  coalesce(decline_reward, e_decline_reward, false),\n" +
                "  coalesce(deleted, e_deleted, null),\n" +
                "  coalesce(evm_address, e_evm_address, null),\n" +
                "  coalesce(expiration_timestamp, e_expiration_timestamp, null),\n" +
                "  coalesce(file_id, e_file_id, null),\n" +
                "  coalesce(id, e_id, null),\n" +
                "  coalesce(initcode, e_initcode, null),\n" +
                "  coalesce(key, e_key, null),\n" +
                "  coalesce(\n" +
                "    max_automatic_token_associations,\n" +
                "    e_max_automatic_token_associations,\n" +
                "    null\n" +
                "  ),\n" +
                "  coalesce(memo, e_memo, ''),\n" +
                "  coalesce(num, e_num, null),\n" +
                "  coalesce(obtainer_id, e_obtainer_id, null),\n" +
                "  coalesce(permanent_removal, e_permanent_removal, null),\n" +
                "  coalesce(proxy_account_id, e_proxy_account_id, null),\n" +
                "  coalesce(public_key, e_public_key, null),\n" +
                "  coalesce(realm, e_realm, null),\n" +
                "  coalesce(shard, e_shard, null),\n" +
                "  coalesce(stake_period_start, e_stake_period_start, null),\n" +
                "  coalesce(staked_account_id, e_staked_account_id, null),\n" +
                "  coalesce(staked_node_id, e_staked_node_id, null),\n" +
                "  coalesce(timestamp_range, e_timestamp_range, null),\n" +
                "  coalesce(type, e_type, 'CONTRACT')\n" +
                "from\n" +
                "  existing\n" +
                "where\n" +
                "  (\n" +
                "    e_timestamp_range is not null\n" +
                "    and timestamp_range is null\n" +
                "  )\n" +
                "  or (\n" +
                "    timestamp_range is not null\n" +
                "    and upper(timestamp_range) is null\n" +
                "  ) on conflict (id) do\n" +
                "update\n" +
                "set\n" +
                "  auto_renew_account_id = excluded.auto_renew_account_id,\n" +
                "  auto_renew_period = excluded.auto_renew_period,\n" +
                "  decline_reward = excluded.decline_reward,\n" +
                "  deleted = excluded.deleted,\n" +
                "  expiration_timestamp = excluded.expiration_timestamp,\n" +
                "  key = excluded.key,\n" +
                "  max_automatic_token_associations = excluded.max_automatic_token_associations,\n" +
                "  memo = excluded.memo,\n" +
                "  obtainer_id = excluded.obtainer_id,\n" +
                "  permanent_removal = excluded.permanent_removal,\n" +
                "  proxy_account_id = excluded.proxy_account_id,\n" +
                "  public_key = excluded.public_key,\n" +
                "  stake_period_start = excluded.stake_period_start,\n" +
                "  staked_account_id = excluded.staked_account_id,\n" +
                "  staked_node_id = excluded.staked_node_id,\n" +
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
