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

import jakarta.inject.Named;
import java.text.MessageFormat;
import lombok.Value;

@Named
@Value
public class TokenAccountUpsertQueryGenerator implements UpsertQueryGenerator {

    @Override
    public String getCreateTempIndexQuery() {
        var pattern = "create index if not exists {0}_idx on {0} (token_id, account_id)";
        return MessageFormat.format(pattern, getTemporaryTableName());
    }

    @Override
    public String getFinalTableName() {
        return "token_account";
    }

    @Override
    public String getUpsertQuery() {
        return """
                with current as (
                  select e.*
                  from token_account e
                  join token_account_temp t on e.account_id = t.account_id
                    and e.token_id = t.token_id
                  where upper(t.timestamp_range) is null
                ), existing as (
                  select
                    e.account_id as e_account_id,
                    e.associated as e_associated,
                    e.automatic_association as e_automatic_association,
                    e.balance as e_balance,
                    e.created_timestamp as e_created_timestamp,
                    e.freeze_status as e_freeze_status,
                    e.kyc_status as e_kyc_status,
                    e.timestamp_range as e_timestamp_range,
                    e.token_id as e_token_id,
                    t.*
                  from
                    token_account_temp t
                    left join current e on e.account_id = t.account_id
                    and e.token_id = t.token_id
                ),
                token as (
                  select
                    token_id,
                    freeze_key,
                    freeze_default,
                    kyc_key
                  from token
                ),
                existing_history as (
                  insert into
                    token_account_history (
                      account_id,
                      associated,
                      automatic_association,
                      balance,
                      created_timestamp,
                      freeze_status,
                      kyc_status,
                      timestamp_range,
                      token_id
                    )
                  select
                    distinct on (existing.account_id, existing.token_id) e_account_id,
                    e_associated,
                    e_automatic_association,
                    e_balance,
                    e_created_timestamp,
                    e_freeze_status,
                    e_kyc_status,
                    int8range(lower(e_timestamp_range), lower(timestamp_range)) as timestamp_range,
                    e_token_id
                  from
                    existing
                  where
                    (existing.created_timestamp is not null or e_created_timestamp is not null) and
                    (e_timestamp_range is not null and timestamp_range is not null)
                  order by
                    existing.account_id,
                    existing.token_id,
                    timestamp_range asc
                ),
                temp_history as (
                  insert into
                    token_account_history (
                      account_id,
                      associated,
                      automatic_association,
                      balance,
                      created_timestamp,
                      freeze_status,
                      kyc_status,
                      timestamp_range,
                      token_id
                    )
                  select
                    coalesce(existing.account_id, e_account_id, null),
                    coalesce(existing.associated, e_associated, false),
                    coalesce(
                      automatic_association,
                      e_automatic_association,
                      false
                    ),
                    case when e_created_timestamp is null or e_created_timestamp <> existing.created_timestamp then
                      coalesce(existing.balance)
                      else coalesce(e_balance, 0) + coalesce(existing.balance, 0)
                    end,
                    coalesce(existing.created_timestamp, e_created_timestamp, null),
                    case when existing.freeze_status is not null then existing.freeze_status
                      when existing.created_timestamp is not null then
                          case
                              when token.freeze_key is null then 0
                              when token.freeze_default is true then 1
                              else 2
                          end
                      else e_freeze_status
                    end freeze_status,
                    case when existing.kyc_status is not null then existing.kyc_status
                      when existing.created_timestamp is not null then
                        case
                          when token.kyc_key is null then 0
                          else 2
                        end
                        else e_kyc_status
                    end kyc_status,
                    coalesce(timestamp_range, e_timestamp_range, null),
                    coalesce(existing.token_id, e_token_id, null)
                  from
                    existing
                    join token on existing.token_id = token.token_id
                  where
                    (existing.created_timestamp is not null or e_created_timestamp is not null) and
                    (existing.timestamp_range is not null and upper(timestamp_range) is not null)
                )
                insert into
                  token_account (
                    account_id,
                    associated,
                    automatic_association,
                    balance,
                    created_timestamp,
                    freeze_status,
                    kyc_status,
                    timestamp_range,
                    token_id
                  )
                select
                  coalesce(existing.account_id, e_account_id, null),
                  coalesce(existing.associated, e_associated, false),
                  coalesce(
                    existing.automatic_association,
                    e_automatic_association,
                    false
                  ),
                  case when e_created_timestamp is null or e_created_timestamp <> existing.created_timestamp then
                    coalesce(existing.balance)
                    else coalesce(e_balance, 0) + coalesce(existing.balance, 0)
                  end,
                  coalesce(existing.created_timestamp, e_created_timestamp, null),
                  case when existing.freeze_status is not null then existing.freeze_status
                      when existing.created_timestamp is not null then
                          case
                              when token.freeze_key is null then 0
                              when token.freeze_default is true then 1
                              else 2
                          end
                      else e_freeze_status
                  end freeze_status,
                  case when existing.kyc_status is not null then existing.kyc_status
                      when existing.created_timestamp is not null then
                        case
                          when token.kyc_key is null then 0
                          else 2
                        end
                        else e_kyc_status
                  end kyc_status,
                  coalesce(existing.timestamp_range, e_timestamp_range, null),
                  coalesce(existing.token_id, e_token_id, null)
                from
                  existing
                  join token on existing.token_id = token.token_id
                where
                  (existing.created_timestamp is not null or e_created_timestamp is not null) and
                  upper(existing.timestamp_range) is null
                  on conflict (account_id, token_id) do
                update
                set
                  associated = excluded.associated,
                  automatic_association = excluded.automatic_association,
                  balance = excluded.balance,
                  created_timestamp = excluded.created_timestamp,
                  freeze_status = excluded.freeze_status,
                  kyc_status = excluded.kyc_status,
                  timestamp_range = excluded.timestamp_range""";
    }
}
