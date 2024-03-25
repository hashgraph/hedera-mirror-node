/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance.Id;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id>, NftAllowanceRepositoryCustom {

    /**
     * This repository method will query based on the spender and further filter by the owner and token_id,
     * order by owner first and then the token_id
     * 1. This is for owner=false scenario
     * 2. It only covers owner gt and token_id gt scenario
     * 3. The limit and order will be provided by the caller of the method
     * e.g PageRequest.of(pageNumber:0, pageSize:1, Sort.by("owner").ascending().and(Sort.by("token_id").ascending()));
     */
    @Query(value = "select * from nft_allowance where spender = ? and owner > ? and token_id > ?", nativeQuery = true)
    List<NftAllowance> findBySpenderAndFilterByOwnerAndToken(
            long accountId, long owner, long tokenId, Pageable pageable);

    /**
     * This repository method will query based on the owner and further filter by the spender and token_id, order by spender first and then the token_id
     * 1. This is for owner=true scenario
     * 2. It only covers owner gt and token_id gt scenario.
     * 3. The limit and order will be provided by the caller of the method
     * e.g PageRequest.of(pageNumber:0, pageSize:1, Sort.by("owner").ascending().and(Sort.by("token_id").ascending()));
     */
    @Query(value = "select * from nft_allowance where owner = ? and spender > ? and token_id > ?", nativeQuery = true)
    List<NftAllowance> findByOwnerAndFilterBySpenderAndToken(
            long accountId, long spenderId, long tokenId, Pageable pageable);
}
