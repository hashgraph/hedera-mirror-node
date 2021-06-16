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

import org.junit.jupiter.api.Tag;

@Tag("v2")
class TokenAccounUpsertQueryGeneratorV2Test extends TokenAccounUpsertQueryGeneratorTest {
    @Override
    public String getInsertQuery() {
        return "insert into token_account (account_id, associated, created_timestamp, freeze_status, kyc_status, " +
                "modified_timestamp, token_id) select token_account_temp.account_id, token_account_temp.associated, " +
                "token_account_temp.created_timestamp, case when token_account_temp.freeze_status is not null then " +
                "token_account_temp.freeze_status when token.freeze_key is null then 0 when token.freeze_default = " +
                "true then 1 else 2 end freeze_status, " +
                "case when token_account_temp.kyc_status is not null then token_account_temp.kyc_status when token" +
                ".kyc_key is null then 0 else 2 end kyc_status, " +
                "token_account_temp.modified_timestamp, token_account_temp.token_id from token_account_temp join " +
                "token on token_account_temp.token_id = token.token_id where token_account_temp.created_timestamp is " +
                "not null on conflict (token_id, account_id, created_timestamp) do nothing";
    }
}
