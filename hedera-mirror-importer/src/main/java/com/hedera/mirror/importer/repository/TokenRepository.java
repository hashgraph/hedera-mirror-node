package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.Token;

public interface TokenRepository extends CrudRepository<Token, Token.Id> {
    @Cacheable(cacheNames = "tokens", cacheManager = EXPIRE_AFTER_30M, key = "{#p0.tokenId.id}")
    @Override
    Optional<Token> findById(Token.Id id);

    @CachePut(cacheNames = "tokens", cacheManager = EXPIRE_AFTER_30M, key = "{#p0.tokenId.tokenId.id}")
    @Override
    <S extends Token> S save(S entity);
}
