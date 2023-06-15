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

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.Nft;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

@Transactional
public interface NftRepository extends CrudRepository<Nft, AbstractNft.Id> {

    @Modifying
    @Query(
            value =
                    """
            with nft_history as (
              insert into nft_history (account_id, created_timestamp, delegating_spender, deleted, metadata,
                serial_number, spender, token_id, timestamp_range)
              select
                account_id,
                created_timestamp,
                delegating_spender,
                deleted,
                metadata,
                serial_number,
                spender,
                token_id,
                int8range(lower(timestamp_range), :consensusTimestamp)
              from nft
              where token_id = :tokenId and account_id = :previousTreasury
            ), nft_updated as (
              update nft
                set account_id = :newTreasury,
                    delegating_spender = null,
                    spender = null,
                    timestamp_range = int8range(:consensusTimestamp, null)
              where token_id = :tokenId and account_id = :previousTreasury
              returning serial_number
            ), updated_count as (
              select count(*) from nft_updated
            )
            update token_account
              set balance = case when account_id = :previousTreasury then 0
                                 else coalesce(balance, 0) + updated_count.count
                            end
            from updated_count
            where account_id in (:newTreasury, :previousTreasury) and token_id = :tokenId
            """,
            nativeQuery = true)
    void updateTreasury(long consensusTimestamp, long newTreasury, long previousTreasury, long tokenId);
}
