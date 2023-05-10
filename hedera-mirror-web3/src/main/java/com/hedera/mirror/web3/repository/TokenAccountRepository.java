/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.token.AbstractTokenAccount.Id;
import com.hedera.mirror.common.domain.token.TokenAccount;
import java.util.Optional;
import lombok.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, Id> {

    @Override
    @Cacheable(cacheNames = "token_account", cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<TokenAccount> findById(Id id);

    int countByAccountIdAndAssociatedIsTrue(long accountId);

    @Query("SELECT COUNT(ta) FROM TokenAccount ta WHERE ta.accountId=?1 AND balance > 0")
    int countByAccountIdAndPositiveBalance(long accountId);
}
