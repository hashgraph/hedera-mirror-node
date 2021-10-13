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

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenAccountId;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, TokenAccountId> {

    @Query(value = "select * from token_account " +
            "where token_id = ?1 and account_id = ?2 " +
            "order by modified_timestamp desc " +
            "limit 1", nativeQuery = true)
    Optional<TokenAccount> findLastByTokenIdAndAccountId(long encodedTokenId, long encodedAccountId);
}
