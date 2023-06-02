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

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance.Id;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id> {

    @Override
    @Cacheable(cacheNames = "nft_allowance", cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<NftAllowance> findById(Id id);

    List<NftAllowance> findByOwnerAndApprovedForAllIsTrue(long owner);
}
