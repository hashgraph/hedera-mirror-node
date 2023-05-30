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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

@Transactional
public interface NftRepository extends CrudRepository<Nft, NftId> {

    @Modifying
    @Query("update Nft set accountId = :accountId, modifiedTimestamp = :timestamp where id = :id")
    void transferNftOwnership(
            @Param("id") NftId nftId,
            @Param("accountId") EntityId newAccountId,
            @Param("timestamp") long modifiedTimestamp);

    @Modifying
    @Query("update Nft set deleted = true, modifiedTimestamp = :timestamp where id = :id")
    void burnOrWipeNft(@Param("id") NftId nftId, @Param("timestamp") long modifiedTimestamp);

    @Modifying
    @Query(
            value =
                    """
            with nft_updated as (
              update nft
                set account_id = :newTreasury,
                    modified_timestamp = :consensusTimestamp
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
