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

import java.text.MessageFormat;
import javax.inject.Named;
import lombok.Value;

@Named
@Value
public class TokenAccountUpsertQueryGenerator implements UpsertQueryGenerator {

    @Override
    public String getCreateTempIndexQuery() {
        var pattern = "create index if not exists {0}_idx on {0} (token_id, account_id, modified_timestamp)";
        return MessageFormat.format(pattern, getTemporaryTableName());
    }

    @Override
    public String getFinalTableName() {
        return "token_account";
    }

    @Override
    public String getInsertQuery() {
        return """
                with last as (
                  select distinct on (token_account.account_id, token_account.token_id) token_account.*
                  from token_account
                  join token_account_temp on token_account_temp.account_id = token_account.account_id
                    and token_account_temp.token_id = token_account.token_id
                  order by token_account.account_id, token_account.token_id, token_account.modified_timestamp desc
                )
                insert into token_account (
                  account_id, associated, automatic_association, created_timestamp, freeze_status, kyc_status,
                  modified_timestamp, token_id
                )
                select
                  token_account_temp.account_id,
                  coalesce(token_account_temp.associated, last.associated),
                  coalesce(token_account_temp.automatic_association, last.automatic_association),
                  coalesce(token_account_temp.created_timestamp, last.created_timestamp),
                  case when token_account_temp.freeze_status is not null then token_account_temp.freeze_status
                       when token_account_temp.created_timestamp is not null then
                         case
                           when token.freeze_key is null then 0
                           when token.freeze_default is true then 1
                           else 2
                         end
                       else last.freeze_status
                  end freeze_status,
                  case when token_account_temp.kyc_status is not null then token_account_temp.kyc_status
                       when token_account_temp.created_timestamp is not null then
                         case
                           when token.kyc_key is null then 0
                           else 2
                          end
                       else last.kyc_status
                  end kyc_status,
                  token_account_temp.modified_timestamp,
                  token_account_temp.token_id
                from token_account_temp
                join token on token_account_temp.token_id = token.token_id
                left join last on last.account_id = token_account_temp.account_id and
                  last.token_id = token_account_temp.token_id and last.associated is true
                where token_account_temp.created_timestamp is not null or last.created_timestamp is not null
                order by token_account_temp.modified_timestamp
                """;
    }

    @Override
    public String getUpdateQuery() {
        return null;
    }
}
