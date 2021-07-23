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

import com.hedera.mirror.importer.EnabledIfV1;

@EnabledIfV1
class NftUpsertQueryGeneratorTest extends AbstractUpsertQueryGeneratorTest {
    @Resource
    private NftUpsertQueryGenerator nftUpsertQueryGenerator;

    @Override
    protected UpsertQueryGenerator getUpdatableDomainRepositoryCustom() {
        return nftUpsertQueryGenerator;
    }

    @Override
    protected String getInsertQuery() {
        return "insert into nft (account_id, created_timestamp, deleted, metadata, modified_timestamp, serial_number," +
                " token_id) select nft_temp.account_id, nft_temp.created_timestamp, nft_temp.deleted, " +
                "nft_temp.metadata, nft_temp.modified_timestamp, nft_temp.serial_number, nft_temp.token_id " +
                "from nft_temp " +
                "join token on nft_temp.token_id = token.token_id " +
                "where nft_temp.created_timestamp is not null " +
                "on conflict (token_id, serial_number) do nothing";
    }

    @Override
    protected String getUpdateQuery() {
        return "update nft set account_id = case when nft_temp.deleted = true then null " +
                "else coalesce(nft_temp.account_id, nft.account_id) end, " +
                "deleted = coalesce(nft_temp.deleted, nft.deleted), " +
                "modified_timestamp = coalesce(nft_temp.modified_timestamp, nft.modified_timestamp) " +
                "from nft_temp where nft.created_timestamp is not null and " +
                "nft.token_id = nft_temp.token_id and nft.serial_number = nft_temp.serial_number";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getFinalTableName();
        assertThat(upsertQuery).isEqualTo("nft");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("nft_temp");
    }
}
