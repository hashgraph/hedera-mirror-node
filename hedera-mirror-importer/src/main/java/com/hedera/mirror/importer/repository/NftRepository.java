package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import javax.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;

@Transactional
public interface NftRepository extends CrudRepository<Nft, NftId> {

    @Modifying
    @Query("update Nft set accountId = :accountId, modifiedTimestamp = :timestamp where id = :id")
    void transferNftOwnership(@Param("id") NftId nftId, @Param("accountId") EntityId newAccountId,
                              @Param("timestamp") long modifiedTimestamp);

    @Modifying
    @Query("update Nft set deleted = true, modifiedTimestamp = :timestamp where id = :id")
    void burnOrWipeNft(@Param("id") NftId nftId, @Param("timestamp") long modifiedTimestamp);

    @Modifying
    @Query(value = "with nft_updated as (" +
            "  update nft set account_id = ?3, modified_timestamp = ?4 " +
            "  where token_id = ?1 and account_id = ?2" +
            "  returning serial_number) " +
            "insert into nft_transfer " +
            "(token_id, sender_account_id, receiver_account_id, consensus_timestamp, serial_number) " +
            "select ?1, ?2, ?3, ?4, nft_updated.serial_number " +
            "from nft_updated", nativeQuery = true)
    void updateTreasury(long tokenId, long previousAccountId, long newAccountId, long consensusTimestamp);
}
