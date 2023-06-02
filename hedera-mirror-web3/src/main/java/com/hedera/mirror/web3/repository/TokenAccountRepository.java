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
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, Id> {

    @Override
    @Cacheable(cacheNames = "token_account", cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<TokenAccount> findById(Id id);

    @Query(
            value = "select count(*) as tokenCount, balance>0 as isPositiveBalance from token_account "
                    + "where account_id = ?1 and associated is true group by balance>0",
            nativeQuery = true)
    List<TokenAccountAssociationsCount> countByAccountIdAndAssociatedGroupedByBalanceIsPositive(long accountId);
}
