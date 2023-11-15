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

import com.hedera.mirror.common.domain.entity.AbstractCryptoAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CryptoAllowanceRepository extends CrudRepository<CryptoAllowance, AbstractCryptoAllowance.Id> {
    List<CryptoAllowance> findByOwner(long owner);

    /**
     * Retrieves the most recent state of a crypto allowance by its owner id up to a given block timestamp.
     * The method considers both the current state of the crypto allowance and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param owner the owner ID of the crypto allowance to be retrieved.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return an Optional containing the crypto allowance's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    with crypto_allowance as (
                        (
                            select *
                            from crypto_allowance
                            where owner = :owner
                                and lower(timestamp_range) <= :blockTimestamp
                            order by lower(timestamp_range) desc
                            limit 1
                        )
                        union all
                        (
                            select *
                            from crypto_allowance_history
                            where owner = :owner
                                and lower(timestamp_range) <= :blockTimestamp
                            order by lower(timestamp_range) desc
                            limit 1
                        )
                        order by timestamp_range desc
                        limit 1
                    ), transfers as (
                        select amount
                        from crypto_transfer
                        where payer_account_id = (select payer_account_id from crypto_allowance)
                            and consensus_timestamp <= :blockTimestamp
                            and consensus_timestamp > lower((select timestamp_range from crypto_allowance))
                    )
                    select amount_granted, owner, payer_account_id, spender, timestamp_range, coalesce(amount - coalesce((select sum(amount) from transfers), 0), 0) as amount
                    from crypto_allowance
                    """,
            nativeQuery = true)
    Optional<CryptoAllowance> findByOwnerAndTimestamp(long owner, long blockTimestamp);
}
