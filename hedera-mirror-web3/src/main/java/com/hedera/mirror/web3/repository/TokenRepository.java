/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;

import com.hedera.mirror.common.domain.token.Token;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenRepository extends CrudRepository<Token, Long> {

    @Override
    @Cacheable(cacheNames = "token", cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<Token> findById(Long tokenId);

    @Cacheable(
            cacheNames = "tokenUnionCache",
            key = "#tokenId + '-' + #createdTimestamp",
            cacheManager = CACHE_MANAGER_TOKEN,
            unless = "#result == null")
    @Query(
            value = "(SELECT * FROM token "
                  + "WHERE token_id = ?1 "
                  + "AND created_timestamp = ?2 "
                  + "UNION ALL "
                  + "SELECT * FROM token_history "
                  + "WHERE token_id = ?1 "
                  + "AND created_timestamp = ?2) "
                  + "LIMIT 1",
            nativeQuery = true)
    Optional<Token> findByTokenIdAndTimestamp(Long tokenId, Long createdTimestamp);
}
