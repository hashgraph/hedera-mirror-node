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
import javax.transaction.Transactional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenId;

@Transactional
@CacheConfig(cacheNames = "tokens", cacheManager = EXPIRE_AFTER_30M)
public interface TokenRepository extends CrudRepository<Token, TokenId> {
    @Cacheable(key = "{#p0.tokenId.id}")
    @Override
    Optional<Token> findById(TokenId id);

    @CachePut(key = "{#p0.tokenId.tokenId.id}")
    @Override
    <S extends Token> S save(S entity);

    @Modifying
    @CacheEvict(key = "{#p0.tokenId.id}")
    @Query("update Token set totalSupply = :supply, modifiedTimestamp = :timestamp where tokenId = :token")
    void updateTokenSupply(@Param("token") TokenId tokenId, @Param("supply") long newTotalSupply,
                           @Param("timestamp") long modifiedTimestamp);
}
