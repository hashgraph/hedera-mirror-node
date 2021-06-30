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
class NftUpsertQueryGeneratorV2Test extends NftUpsertQueryGeneratorTest {
    @Override
    public String getInsertQuery() {
        return "insert into nft (account_id, created_timestamp, deleted, metadata, modified_timestamp, serial_number," +
                " token_id) select coalesce(nft_temp.account_id, token.treasury_account_id), " +
                "nft_temp.created_timestamp, coalesce(nft_temp.deleted, entity.deleted), nft_temp.metadata, " +
                "nft_temp.modified_timestamp, nft_temp.serial_number, nft_temp.token_id " +
                "from nft_temp " +
                "right outer join entity on nft_temp.token_id = entity.id " +
                "right outer join token on nft_temp.token_id = token.token_id " +
                "where nft_temp.created_timestamp is not null on conflict (created_timestamp, token_id, " +
                "serial_number) " +
                "do nothing";
    }
}
