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

import com.hedera.mirror.importer.EnabledIfV2;

@EnabledIfV2
@SuppressWarnings("java:S2187")
class TokenUpsertQueryGeneratorV2Test extends TokenUpsertQueryGeneratorTest {
    @Override
    protected String getInsertQuery() {
        return "insert into token (created_timestamp, decimals, fee_schedule_key, fee_schedule_key_ed25519_hex, " +
                "freeze_default, freeze_key, freeze_key_ed25519_hex, " +
                "initial_supply, kyc_key, kyc_key_ed25519_hex, max_supply, modified_timestamp, name, pause_key, " +
                "pause_status, supply_key, supply_key_ed25519_hex, supply_type, symbol, token_id, total_supply, " +
                "treasury_account_id, type, wipe_key, wipe_key_ed25519_hex) select token_temp.created_timestamp, " +
                "token_temp.decimals, token_temp.fee_schedule_key, token_temp.fee_schedule_key_ed25519_hex, " +
                "token_temp.freeze_default, token_temp.freeze_key, token_temp.freeze_key_ed25519_hex, token_temp" +
                ".initial_supply, token_temp.kyc_key, token_temp.kyc_key_ed25519_hex, token_temp.max_supply, " +
                "token_temp.modified_timestamp, coalesce(token_temp.name, ''), token_temp.pause_key, " +
                "token_temp.pause_status, token_temp.supply_key, token_temp.supply_key_ed25519_hex, token_temp" +
                ".supply_type, coalesce(token_temp.symbol, ''), token_temp.token_id, token_temp.total_supply, " +
                "token_temp.treasury_account_id, token_temp.type, token_temp.wipe_key, token_temp" +
                ".wipe_key_ed25519_hex from token_temp where token_temp.created_timestamp is not null " +
                "on conflict (token_id, created_timestamp) do nothing";
    }
}
