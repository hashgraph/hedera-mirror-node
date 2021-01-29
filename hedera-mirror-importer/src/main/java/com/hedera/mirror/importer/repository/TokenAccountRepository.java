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

import static com.hedera.mirror.importer.config.CacheConfiguration.EXPIRE_AFTER_30M;

import java.util.Optional;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.TokenAccount;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, TokenAccount.Id> {
    @Cacheable(cacheNames = "tokenaccounts", cacheManager = EXPIRE_AFTER_30M, key = "{#p0, #p1}")
    @Query(value = "select * from token_account where token_id = ?1 and account_id = ?2", nativeQuery = true)
    Optional<TokenAccount> findByTokenIdAndAccountId(long encodedTokenId, long encodedAccountId);

    @CachePut(cacheNames = "tokenaccounts", cacheManager = EXPIRE_AFTER_30M, key = "{#p0.id.tokenId.id, #p0.id" +
            ".accountId.id}")
    @Override
    <S extends TokenAccount> S save(S entity);
}
