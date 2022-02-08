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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

class TokenUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {
    @Resource
    private TokenUpsertQueryGenerator tokenRepositoryCustom;

    @Override
    protected UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return tokenRepositoryCustom;
    }

    @Override
    protected String getInsertQuery() {
        return "insert into token (created_timestamp, decimals, fee_schedule_key, " +
                "freeze_default, freeze_key, initial_supply, kyc_key, " +
                "max_supply, modified_timestamp, name, pause_key, pause_status, supply_key, " +
                "supply_type, symbol, token_id, total_supply, treasury_account_id, type, wipe_key " +
                ") select token_temp.created_timestamp, token_temp.decimals, token_temp" +
                ".fee_schedule_key, token_temp.freeze_default, token_temp" +
                ".freeze_key, token_temp.initial_supply, token_temp.kyc_key, " +
                "token_temp.max_supply, token_temp.modified_timestamp, coalesce" +
                "(token_temp.name, ''), token_temp.pause_key, token_temp" +
                ".pause_status, token_temp.supply_key, token_temp.supply_type, " +
                "coalesce(token_temp.symbol, ''), token_temp.token_id, " +
                "token_temp.total_supply, token_temp.treasury_account_id, token_temp.type, token_temp.wipe_key " +
                "from token_temp where token_temp.created_timestamp is not null on " +
                "conflict (token_id) do nothing";
    }

    @Override
    protected String getUpdateQuery() {
        return "update token set " +
                "  fee_schedule_key = coalesce(token_temp.fee_schedule_key, token.fee_schedule_key), " +
                "  freeze_key = coalesce(token_temp.freeze_key, " +
                "  token.freeze_key), kyc_key = coalesce(token_temp.kyc_key, token.kyc_key)," +
                "  modified_timestamp = coalesce(token_temp.modified_timestamp, token.modified_timestamp)," +
                "  name = coalesce(token_temp.name, token.name), " +
                "  pause_key = coalesce(token_temp.pause_key, token.pause_key)," +
                "  pause_status = coalesce(token_temp.pause_status, token.pause_status)," +
                "  supply_key = coalesce(token_temp.supply_key, token.supply_key), " +
                "  symbol = coalesce(token_temp.symbol, token.symbol), " +
                "  total_supply = " +
                "     case when token_temp.total_supply >= 0 then token_temp.total_supply" +
                "          else token.total_supply + coalesce(token_temp.total_supply, 0)" +
                "     end," +
                "  treasury_account_id = coalesce(token_temp.treasury_account_id, token.treasury_account_id), " +
                "  wipe_key = coalesce(token_temp.wipe_key, token.wipe_key) " +
                "from token_temp " +
                "where token.token_id = token_temp.token_id and token_temp.created_timestamp is null";
    }

    @Test
    void tableName() {
        String tableName = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(tableName).isEqualTo("token");
    }

    @Test
    void tempTableName() {
        String tempTableName = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(tempTableName).isEqualTo("token_temp");
    }
}
