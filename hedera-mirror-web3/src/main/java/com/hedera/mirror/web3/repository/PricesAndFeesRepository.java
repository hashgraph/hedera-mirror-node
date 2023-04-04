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

package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_1H;

import com.hedera.mirror.common.domain.entity.Entity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PricesAndFeesRepository extends CrudRepository<Entity, Long> {

    @Query(
            value =
                    """
                with latest_create as (
                      select max(file_data.consensus_timestamp) as consensus_timestamp
                      from file_data
                      where file_data.entity_id = 112 and file_data.transaction_type in (17, 19)
                      group by file_data.entity_id
                      order by consensus_timestamp desc
                    )
                    select
                    string_agg(file_data.file_data, '' order by file_data.consensus_timestamp) as file_data
                    from file_data
                    join latest_create l on file_data.consensus_timestamp >= l.consensus_timestamp
                    where file_data.entity_id = 112 and file_data.transaction_type in (16, 17, 19)
                      and ?1 >= l.consensus_timestamp
                    group by file_data.entity_id""",
            nativeQuery = true)
    @Cacheable(
            cacheNames = "price_and_fee.exchange_rate",
            cacheManager = CACHE_MANAGER_1H,
            key = "'now'",
            unless = "#result == null")
    byte[] getExchangeRate(final long now);

    @Query(
            value =
                    """
                with latest_create as (
                      select max(file_data.consensus_timestamp) as consensus_timestamp
                      from file_data
                      where file_data.entity_id = 111 and file_data.transaction_type in (17, 19)
                      group by file_data.entity_id
                      order by consensus_timestamp desc
                    )
                    select
                    string_agg(file_data.file_data, '' order by file_data.consensus_timestamp) as file_data
                    from file_data
                    join latest_create l on file_data.consensus_timestamp >= l.consensus_timestamp
                    where file_data.entity_id = 111 and file_data.transaction_type in (16, 17, 19)
                      and ?1 >= l.consensus_timestamp
                    group by file_data.entity_id""",
            nativeQuery = true)
    @Cacheable(
            cacheNames = "price_and_fee.fee_schedule",
            cacheManager = CACHE_MANAGER_1H,
            key = "'now'",
            unless = "#result == null")
    byte[] getFeeSchedule(final long now);
}
