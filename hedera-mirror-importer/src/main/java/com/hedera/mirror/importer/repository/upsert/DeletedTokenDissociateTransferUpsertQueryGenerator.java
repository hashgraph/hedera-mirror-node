/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.repository.upsert;

import java.text.MessageFormat;

public class DeletedTokenDissociateTransferUpsertQueryGenerator implements UpsertQueryGenerator {

    private static final String FINAL_TABLE_NAME = "token_transfer";

    private static final String TEMP_TABLE_NAME = "token_dissociate_transfer";

    private static final String INSERT_SQL = MessageFormat.format(
            """
            with dissociated_nft as (
              update nft set deleted = true, modified_timestamp = tdt.consensus_timestamp
              from {0} tdt
              where nft.token_id = tdt.token_id and nft.account_id = tdt.account_id and nft.deleted is false
              returning nft.token_id
            ), nft_token as (
              select distinct token_id from dissociated_nft
            ), nft_transfer as (
              select consensus_timestamp, jsonb_agg(jsonb_build_object(
                ''is_approval'', false,
                ''receiver_account_id'', null,
                ''sender_account_id'', tdt.account_id,
                ''serial_number'', tdt.amount,
                ''token_id'', tdt.token_id
              )) as transfer
              from {0} tdt
              join nft_token on nft_token.token_id = tdt.token_id
              group by tdt.consensus_timestamp
            ), update_transaction as (
              update transaction t
              set nft_transfer = transfer
              from nft_transfer nt
              where nt.consensus_timestamp = t.consensus_timestamp
            )
            insert into {1}
            select tdt.*
            from {0} tdt
            left join nft_token nt on nt.token_id = tdt.token_id
            where nt.token_id is null
            """,
            TEMP_TABLE_NAME, FINAL_TABLE_NAME);

    @Override
    public String getCreateTempIndexQuery() {
        return MessageFormat.format(
                "create index if not exists {0}_idx on {0} (token_id, account_id)", TEMP_TABLE_NAME);
    }

    @Override
    public String getCreateTempTableQuery() {
        return MessageFormat.format(
                "create temporary table if not exists {0} on commit drop as table {1} limit 0",
                TEMP_TABLE_NAME, FINAL_TABLE_NAME);
    }

    @Override
    public String getFinalTableName() {
        return FINAL_TABLE_NAME;
    }

    @Override
    public String getUpsertQuery() {
        return INSERT_SQL;
    }

    @Override
    public String getTemporaryTableName() {
        return TEMP_TABLE_NAME;
    }
}
