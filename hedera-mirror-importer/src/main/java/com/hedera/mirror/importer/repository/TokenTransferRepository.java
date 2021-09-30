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

import com.hedera.mirror.importer.domain.TokenTransfer;

@Transactional
public interface TokenTransferRepository extends CrudRepository<TokenTransfer, TokenTransfer.Id> {

    @Modifying
    @Query(value = "with dissociated_nft as (" +
            "  update nft set deleted = true, modified_timestamp = ?3" +
            "  where token_id = ?4 and account_id = ?1 and deleted is false" +
            "  returning serial_number), " +
            "transferred_nft as (" +
            "  insert into nft_transfer" +
            "    (token_id, sender_account_id, consensus_timestamp, serial_number)" +
            "  select ?4, ?1, ?3, dissociated_nft.serial_number " +
            "  from dissociated_nft" +
            "  returning serial_number) " +
            "insert into token_transfer (account_id, amount, consensus_timestamp, token_id) " +
            "select ?1, ?2, ?3, ?4 " +
            "where not exists (select * from transferred_nft)", nativeQuery = true)
    void insertTransferForTokenDissociate(long accountId, long amount, long consensusTimestamp, long tokenId);
}
