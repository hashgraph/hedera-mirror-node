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

import com.hedera.mirror.common.domain.token.AbstractTokenAccount.Id;
import com.hedera.mirror.common.domain.token.TokenAccount;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, Id> {

    @Query(value = "select freeze_status from token_account where account_id = ?1 and token_id = ?2",
            nativeQuery = true)
    @Cacheable(cacheNames = "token_account.freeze_status", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<Integer> findFrozenStatus(final Long accountId, final Long tokenId);

    @Query(value = "select kyc_status from token_account where account_id = ?1 and token_id = ?2",
            nativeQuery = true)
    @Cacheable(cacheNames = "token_account.kyc_status", cacheManager = CACHE_MANAGER_TOKEN , unless = "#result == null")
    Optional<Integer> findKycStatus(final Long accountId, final Long tokenId);
}
