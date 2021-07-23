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
class NftUpsertQueryGeneratorV2Test extends NftUpsertQueryGeneratorTest {
    @Override
    protected String getInsertQuery() {
        return "insert into nft (account_id, created_timestamp, deleted, metadata, modified_timestamp, serial_number," +
                " token_id) select nft_temp.account_id, nft_temp.created_timestamp, nft_temp.deleted, " +
                "nft_temp.metadata, nft_temp.modified_timestamp, nft_temp.serial_number, nft_temp.token_id " +
                "from nft_temp " +
                "join token on nft_temp.token_id = token.token_id " +
                "where nft_temp.created_timestamp is not null " +
                "on conflict (token_id, serial_number, created_timestamp) do nothing";
    }
}
