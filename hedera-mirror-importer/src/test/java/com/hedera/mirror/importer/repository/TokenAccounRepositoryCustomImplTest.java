package com.hedera.mirror.importer.repository;

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

class TokenAccounRepositoryCustomImplTest extends AbstractRepositoryCustomImplTest {
    @Resource
    private TokenAccountRepositoryCustomImpl tokenAccountRepositoryCustom;

    @Override
    public UpdatableDomainRepositoryCustom getUpdatableDomainRepositoryCustom() {
        return tokenAccountRepositoryCustom;
    }

    @Override
    public String getInsertQuery() {
        return "insert into token_account (account_id, associated, created_timestamp, freeze_status, kyc_status, " +
                "modified_timestamp, token_id) select token_account_temp.account_id, token_account_temp.associated, " +
                "token_account_temp.created_timestamp, coalesce(token_account_temp.freeze_status, " +
                "getNewAccountFreezeStatus(token_account_temp.token_id)) as freeze_status, coalesce" +
                "(token_account_temp.kyc_status, getNewAccountKycStatus(token_account_temp.token_id)) as kyc_status, " +
                "token_account_temp.modified_timestamp, token_account_temp.token_id from token_account_temp join " +
                "token on token_account_temp.token_id = token.token_id where token_account_temp.created_timestamp is " +
                "not null  on conflict (token_id, account_id) do nothing";
    }

    @Override
    public String getUpdateQuery() {
        return "update token_account set associated = coalesce(token_account_temp.associated, token_account" +
                ".associated), freeze_status = coalesce(token_account_temp.freeze_status, token_account" +
                ".freeze_status), kyc_status = coalesce(token_account_temp.kyc_status, token_account.kyc_status), " +
                "modified_timestamp = coalesce(token_account_temp.modified_timestamp, token_account" +
                ".modified_timestamp) from token_account_temp " +
                "where token_account.token_id = token_account_temp.token_id and token_account.account_id = " +
                "token_account_temp.account_id and token_account_temp.created_timestamp is null";
    }

    @Test
    void tableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTableName();
        assertThat(upsertQuery).isEqualTo("token_account");
    }

    @Test
    void tempTableName() {
        String upsertQuery = getUpdatableDomainRepositoryCustom().getTemporaryTableName();
        assertThat(upsertQuery).isEqualTo("token_account_temp");
    }
}
