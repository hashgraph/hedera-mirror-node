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
                set account_id = ?3,
                    modified_timestamp = ?4
              where token_id = ?1 and account_id = ?2
              returning serial_number
            ), updated_count as (
              select count(*) from nft_updated
            ), update_balance as (
              update token_account
                set balance = case when account_id = ?2 then 0
                                   else coalesce(balance, 0) + updated_count.count
                              end
              from updated_count
              where account_id in (?2, ?3) and token_id = ?1
            )
            insert into nft_transfer (token_id, sender_account_id, receiver_account_id, consensus_timestamp,
             payer_account_id, serial_number, is_approval)
            select ?1, ?2, ?3, ?4, ?5, nft_updated.serial_number, ?6
            from nft_updated
            """,
            nativeQuery = true)
    void updateTreasury(
            long tokenId,
            long previousTreasury,
            long newTreasury,
            long consensusTimestamp,
            long payerAccountId,
            boolean isApproval);
}
