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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id> {

    @Query(
            value = "select * from nft_allowance where owner = ? and token_id = ? order by owner,token_id limit ?",
            nativeQuery = true)
    List<NftAllowance> findByOwnerAndTokenEq(long owner, long tokenId, int limit);

    @Query(
            value = "select * from nft_allowance where spender = ? and token_id >= ? order by spender,token_id limit ?",
            nativeQuery = true)
    List<NftAllowance> findBySpenderAndTokenGte(long spenderId, long tokenId, int limit);
}
