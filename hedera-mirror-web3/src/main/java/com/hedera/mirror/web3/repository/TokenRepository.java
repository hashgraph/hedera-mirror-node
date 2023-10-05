/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.token.Token;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenRepository extends CrudRepository<Token, Long> {

    @Override
    @Cacheable(cacheNames = "token", cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<Token> findById(Long tokenId);

    /**
     * Retrieves the most recent state of a token by its ID up to a given block timestamp.
     * The method considers both the current state of the token and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     * It performs a UNION operation between the 'token' and 'token_history' tables,
     * filters the combined result set to get the records with a timestamp range
     * less than or equal to the provided block timestamp and then returns the most recent record.
     *
     * @param tokenId         the ID of the token to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the token's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    (
                        select *
                        from token
                        where token_id = ?1 and lower(timestamp_range) <= ?2
                    )
                    union all
                    (
                        select *
                        from token_history
                        where token_id = ?1 and lower(timestamp_range) <= ?2
                        AND NOT EXISTS (
                            SELECT 1
                            FROM token_history as inner_th
                            WHERE inner_th.token_id = token_history.token_id
                            AND lower(inner_th.timestamp_range) > lower(token_history.timestamp_range)
                            AND lower(inner_th.timestamp_range) <= ?2
                        )
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """,
            nativeQuery = true)
    Optional<Token> findByTokenIdAndTimestamp(long tokenId, long blockTimestamp);
}
