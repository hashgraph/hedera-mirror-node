/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;

class NftRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private NftRepository nftRepository;

    @Resource
    private TokenAccountRepository tokenAccountRepository;

    @Test
    void save() {
        Nft savedNft = nftRepository.save(nft("0.0.2", 1, 1));
        assertThat(nftRepository.findById(savedNft.getId())).contains(savedNft);
    }

    @Test
    void updateDeleted() {
        Nft savedNft = nftRepository.save(nft("0.0.3", 2, 2));
        nftRepository.burnOrWipeNft(savedNft.getId(), 3L);
        savedNft.setDeleted(true);
        savedNft.setModifiedTimestamp(3L);
        assertThat(nftRepository.findById(savedNft.getId())).contains(savedNft);
    }

    @Test
    void updateDeletedMissingNft() {
        NftId nftId = new NftId(1L, EntityId.of("0.0.1", EntityType.TOKEN));
        nftRepository.burnOrWipeNft(nftId, 1L);
        assertThat(nftRepository.findById(nftId)).isNotPresent();
    }

    @Test
    void updateAccountId() {
        Nft savedNft = nftRepository.save(nft("0.0.3", 2, 2));
        EntityId accountId = EntityId.of("0.0.10", EntityType.ACCOUNT);
        nftRepository.transferNftOwnership(savedNft.getId(), accountId, 3L);
        savedNft.setAccountId(accountId);
        savedNft.setModifiedTimestamp(3L);
        assertThat(nftRepository.findById(savedNft.getId())).contains(savedNft);
    }

    @Test
    void updateAccountIdMissingNft() {
        NftId nftId = new NftId(1L, EntityId.of("0.0.1", EntityType.TOKEN));
        EntityId accountId = EntityId.of("0.0.10", EntityType.ACCOUNT);
        nftRepository.transferNftOwnership(nftId, accountId, 3L);
        assertThat(nftRepository.findById(nftId)).isNotPresent();
    }

    @Test
    void updateTreasury() {
        // given
        long consensusTimestamp = 6L;
        var newTreasury = EntityId.of("0.0.2", EntityType.ACCOUNT);
        var nft1 = nft("0.0.100", 1, 1);
        var nft2 = nft("0.0.100", 2, 2);
        var nft3 = nft("0.0.100", 3, 3);
        var nft4 = nft("0.0.101", 1, 4); // Not updated since wrong token
        var nft5 = nft("0.0.100", 4, 5); // Not updated since wrong account
        nft5.setAccountId(newTreasury);
        nftRepository.saveAll(List.of(nft1, nft2, nft3, nft4, nft5));
        long nftTokenId = nft1.getId().getTokenId().getId();
        long oldTreasuryId = nft1.getAccountId().getId();
        var tokenAccountOldTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(oldTreasuryId).balance(3).tokenId(nftTokenId))
                .persist();
        var tokenAccountNewTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(newTreasury.getId()).balance(1).tokenId(nftTokenId))
                .persist();

        // when
        nftRepository.updateTreasury(consensusTimestamp, newTreasury.getId(), oldTreasuryId, nftTokenId);

        // then
        nft1.setAccountId(newTreasury);
        nft1.setModifiedTimestamp(consensusTimestamp);
        nft2.setAccountId(newTreasury);
        nft2.setModifiedTimestamp(consensusTimestamp);
        nft3.setAccountId(newTreasury);
        nft3.setModifiedTimestamp(consensusTimestamp);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3, nft4, nft5);

        tokenAccountOldTreasury.setBalance(0);
        tokenAccountNewTreasury.setBalance(4);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccountOldTreasury, tokenAccountNewTreasury);
    }

    private Nft nft(String tokenId, long serialNumber, long consensusTimestamp) {
        Nft nft = new Nft();
        nft.setAccountId(EntityId.of("0.0.1", EntityType.ACCOUNT));
        nft.setCreatedTimestamp(consensusTimestamp);
        nft.setId(new NftId(serialNumber, EntityId.of(tokenId, EntityType.TOKEN)));
        nft.setMetadata(new byte[] {1});
        nft.setModifiedTimestamp(consensusTimestamp);
        return nft;
    }
}
