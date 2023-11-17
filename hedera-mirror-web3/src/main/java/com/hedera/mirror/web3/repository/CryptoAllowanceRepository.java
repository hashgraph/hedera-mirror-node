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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CryptoAllowanceRepository extends CrudRepository<CryptoAllowance, AbstractCryptoAllowance.Id> {
    List<CryptoAllowance> findByOwner(long owner);

    /**
     * Retrieves the most recent state of the crypto allowances by their owner id up to a given block timestamp.
     * It takes into account the crypto transfers that happened up to the given block timestamp, sums them up
     * and decreases the crypto allowances' amounts with the transfers that occurred.
     *
     * @param owner the owner ID of the crypto allowance to be retrieved.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return an Optional containing the crypto allowance's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    with crypto_allowances as (
                        select *
                        from
                        (
                            select *, row_number() over (
                                partition by spender
                                order by lower(timestamp_range) desc
                            ) as row_number
                            from
                            (
                                (
                                    select *
                                    from crypto_allowance
                                    where owner = :owner
                                        and lower(timestamp_range) <= :blockTimestamp
                                )
                                union all
                                (
                                    select *
                                    from crypto_allowance_history
                                    where owner = :owner
                                        and lower(timestamp_range) <= :blockTimestamp
                                )
                            ) as all_crypto_allowances
                        ) as grouped_crypto_allowances
                        where row_number = 1
                        ), transfers as (
                        select entity_id, sum(ct.amount) as amount
                        from crypto_transfer ct join crypto_allowances ca on ct.entity_id = ca.spender
                        where entity_id in (select spender from crypto_allowances)
                            and is_approval is true
                            and consensus_timestamp <= :blockTimestamp
                            and consensus_timestamp > lower(ca.timestamp_range)
                        group by entity_id
                        )
                     select amount_granted, owner, payer_account_id, spender, timestamp_range, coalesce(amount - coalesce((select amount from transfers tr where tr.entity_id = ca.spender), 0), 0) as amount
                     from crypto_allowances ca
                    """,
            nativeQuery = true)
    List<CryptoAllowance> findByOwnerAndTimestamp(long owner, long blockTimestamp);
}
