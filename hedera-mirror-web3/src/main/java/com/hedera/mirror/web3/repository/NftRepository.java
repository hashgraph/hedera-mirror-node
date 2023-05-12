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

import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftRepository extends CrudRepository<Nft, NftId> {

    @Override
    @Cacheable(cacheNames = "nft", cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<Nft> findById(NftId nftId);

    @Query(
            value = "select count(*) from Nft n "
                    + "join Entity e on e.id = n.token_id "
                    + "where n.account_id=:accountId and n.deleted is false and e.deleted is not true",
            nativeQuery = true)
    long countByAccountIdNotDeleted(Long accountId);
}
