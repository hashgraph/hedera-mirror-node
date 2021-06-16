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
import org.junit.Ignore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("v1")
class TokenUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {
    @Resource
    private TokenUpsertQueryGenerator tokenRepositoryCustom;

    @Override
    public UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return tokenRepositoryCustom;
    }

    @Override
    public String getInsertQuery() {
        return "insert into token (created_timestamp, decimals, freeze_default, freeze_key, freeze_key_ed25519_hex, " +
                "initial_supply, kyc_key, kyc_key_ed25519_hex, modified_timestamp, name, supply_key, " +
                "supply_key_ed25519_hex, symbol, token_id, total_supply, treasury_account_id, wipe_key, " +
                "wipe_key_ed25519_hex) select token_temp.created_timestamp, token_temp.decimals, token_temp" +
                ".freeze_default, token_temp.freeze_key, case when token_temp.freeze_key_ed25519_hex = '<uuid>' then " +
                "'' " +
                "else coalesce(token_temp.freeze_key_ed25519_hex, null) end, token_temp.initial_supply, token_temp" +
                ".kyc_key, case when token_temp.kyc_key_ed25519_hex = '<uuid>' then '' else coalesce(token_temp" +
                ".kyc_key_ed25519_hex, null) end, token_temp.modified_timestamp, case when token_temp.name = '<uuid>'" +
                " then" +
                " '' else coalesce(token_temp.name, '') end, token_temp.supply_key, case when token_temp" +
                ".supply_key_ed25519_hex = '<uuid>' then '' else coalesce(token_temp.supply_key_ed25519_hex, null) " +
                "end, " +
                "case when token_temp.symbol = '<uuid>' then '' else coalesce(token_temp.symbol, '') end, token_temp" +
                ".token_id, token_temp.total_supply, token_temp.treasury_account_id, token_temp.wipe_key, case when " +
                "token_temp.wipe_key_ed25519_hex = '<uuid>' then '' else coalesce(token_temp.wipe_key_ed25519_hex, " +
                "null) " +
                "end from token_temp where token_temp.created_timestamp is not null on conflict (token_id) do nothing";
    }

    @Override
    public String getUpdateQuery() {
        return "update token set freeze_key = coalesce(token_temp.freeze_key, token.freeze_key), " +
                "freeze_key_ed25519_hex = case when token_temp.freeze_key_ed25519_hex = '<uuid>' then '' else " +
                "coalesce(token_temp.freeze_key_ed25519_hex, token.freeze_key_ed25519_hex) end, " +
                "kyc_key = coalesce(token_temp.kyc_key, token.kyc_key), " +
                "kyc_key_ed25519_hex = case when token_temp.kyc_key_ed25519_hex = '<uuid>' then " +
                "'' else coalesce(token_temp.kyc_key_ed25519_hex, token.kyc_key_ed25519_hex) end, " +
                "modified_timestamp = coalesce(token_temp.modified_timestamp, token.modified_timestamp), " +
                "name = case when token_temp.name = '<uuid>' then '' else coalesce(token_temp.name, token.name) end, " +
                "supply_key = coalesce(token_temp.supply_key, token.supply_key), " +
                "supply_key_ed25519_hex = case when token_temp.supply_key_ed25519_hex = '<uuid>' then '' else " +
                "coalesce(token_temp.supply_key_ed25519_hex, token.supply_key_ed25519_hex) end, " +
                "symbol = case when token_temp.symbol = '<uuid>' then '' else coalesce(token_temp.symbol, token" +
                ".symbol) end, " +
                "total_supply = coalesce(token_temp.total_supply, token.total_supply), " +
                "treasury_account_id = coalesce(token_temp.treasury_account_id, token.treasury_account_id), " +
                "wipe_key = coalesce(token_temp.wipe_key, token.wipe_key), " +
                "wipe_key_ed25519_hex = case when token_temp.wipe_key_ed25519_hex = '<uuid>' " +
                "then '' else coalesce(token_temp.wipe_key_ed25519_hex, token.wipe_key_ed25519_hex) end " +
                "from token_temp where token.token_id = token_temp.token_id and token_temp.created_timestamp is null";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(upsertQuery).isEqualTo("token");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("token_temp");
    }

    @Override
    @Ignore("ignore token case as max_supply, supply_type and type have missing implementation details incoming")
    @Test
    protected void insertContainsAllFields() {
    }
}
