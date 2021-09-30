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

public class TokenDissociateTransferUpsertQueryGenerator implements UpsertQueryGenerator {

    private static final String FINAL_TABLE_NAME = "token_transfer";

    private static final String TEMP_TABLE_NAME = "token_dissociate_transfer";

    private static final String INSERT_SQL = "with dissociated_nft as (" +
            "  update nft set deleted = true, modified_timestamp = tdt.consensus_timestamp" +
            "  from " + TEMP_TABLE_NAME + " tdt" +
            "  where nft.token_id = tdt.token_id and nft.account_id = tdt.account_id and nft.deleted is false" +
            "  returning nft.token_id, nft.serial_number, nft.account_id, nft.modified_timestamp" +
            "), updated_nft as (" +
            "  insert into nft_transfer (consensus_timestamp, sender_account_id, serial_number, token_id)" +
            "  select modified_timestamp, account_id, serial_number, token_id" +
            "  from dissociated_nft" +
            "  returning token_id" +
            ") " +
            "insert into " + FINAL_TABLE_NAME + " " +
            "select * from " + TEMP_TABLE_NAME + " tdt " +
            "where tdt.token_id not in (select distinct token_id from updated_nft)";

    @Override
    public String getCreateTempTableQuery() {
        return String.format("create temporary table if not exists %s on commit drop as table %s limit 0",
                TEMP_TABLE_NAME, FINAL_TABLE_NAME);
    }

    @Override
    public String getFinalTableName() {
        return FINAL_TABLE_NAME;
    }

    @Override
    public String getInsertQuery() {
        return INSERT_SQL;
    }

    @Override
    public String getTemporaryTableName() {
        return TEMP_TABLE_NAME;
    }

    @Override
    public String getUpdateQuery() {
        return "";
    }
}
