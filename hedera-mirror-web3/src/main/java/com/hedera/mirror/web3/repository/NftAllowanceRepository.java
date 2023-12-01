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

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance.Id;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id> {
    List<NftAllowance> findByOwnerAndApprovedForAllIsTrue(long owner);

    /**
     * Retrieves the most recent state of nft allowances by its owner up to a given block timestamp.
     * The method considers both the current state of the nft allowance and its historical states
     * and returns the latest valid just before or equal to the provided block timestamp.
     *
     * @param owner the ID of the owner
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return List containing the nft allowances at the specified timestamp.
     */
    @Query(
            value =
                    """
                    with nft_allowances as (
                        select *
                        from (
                            select *,
                                row_number() over (
                                    partition by spender, token_id
                                    order by lower(timestamp_range) desc
                                ) as row_number
                            from (
                                select *
                                from nft_allowance
                                where owner = :owner
                                    and approved_for_all = true
                                    and lower(timestamp_range) <= :blockTimestamp
                                union all
                                select *
                                from nft_allowance_history
                                where owner = :owner
                                    and approved_for_all = true
                                    and lower(timestamp_range) <= :blockTimestamp
                            ) as nft_allowance_history
                        ) as row_numbered_data
                        where row_number = 1
                    )
                    select *
                    from nft_allowances
                    order by timestamp_range desc
                    """,
            nativeQuery = true)
    List<NftAllowance> findByOwnerAndTimestampAndApprovedForAllIsTrue(long owner, long blockTimestamp);
}
