package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;

import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;

public interface TokenRepository extends CrudRepository<Token, TokenId> {

    @Query(value = "select name from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.name", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<String> findName(final Long tokenId);

    @Query(value = "select symbol from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.symbol", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<String> findSymbol(final Long tokenId);

    @Query(value = "select total_supply from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.total_supply", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<Long> findTotalSupply(final Long tokenId);

    @Query(value = "select decimals from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.decimals", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<Integer> findDecimals(final Long tokenId);

    @Query(value = "select type from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.type", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<TokenTypeEnum> findType(final Long tokenId);

    @Query(value = "select freeze_default from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.freeze_default", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Boolean findFreezeDefault(final Long tokenId);

    @Query(value = "select kyc_key from token where token_id = ?1",
            nativeQuery = true)
    @Cacheable(cacheNames = "token.kyc_key", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<byte[]> findKycDefault(final Long tokenId);
}
