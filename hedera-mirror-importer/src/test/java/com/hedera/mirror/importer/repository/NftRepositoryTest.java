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
import com.hedera.mirror.common.domain.token.NftTransfer;
import jakarta.annotation.Resource;
import java.util.List;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.Test;

class NftRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private NftRepository nftRepository;

    @Resource
    private NftTransferRepository nftTransferRepository;

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
        long consensusTimestamp = 6L;
        EntityId newTreasury = EntityId.of("0.0.2", EntityType.ACCOUNT);
        Nft nft1 = nft("0.0.100", 1, 1);
        Nft nft2 = nft("0.0.100", 2, 2);
        Nft nft3 = nft("0.0.100", 3, 3);
        Nft nft4 = nft("0.0.101", 1, 4); // Not updated since wrong token
        Nft nft5 = nft("0.0.100", 4, 5); // Not updated since wrong account
        nft5.setAccountId(newTreasury);
        nftRepository.saveAll(List.of(nft1, nft2, nft3, nft4, nft5));
        long nftTokenId = nft1.getId().getTokenId().getId();
        var tokenAccountOldTreasury = domainBuilder
                .tokenAccount()
                .customize(ta ->
                        ta.accountId(nft1.getAccountId().getId()).balance(3).tokenId(nftTokenId))
                .persist();
        var tokenAccountNewTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(newTreasury.getId()).balance(1).tokenId(nftTokenId))
                .persist();

        EntityId tokenId = nft1.getId().getTokenId();
        EntityId previousTreasury = nft1.getAccountId();
        nftRepository.updateTreasury(
                tokenId.getId(),
                previousTreasury.getId(),
                newTreasury.getId(),
                consensusTimestamp,
                EntityId.of("0.0.200", EntityType.ACCOUNT).getId(),
                false);

        assertAccountUpdated(nft1, newTreasury);
        assertAccountUpdated(nft2, newTreasury);
        assertAccountUpdated(nft3, newTreasury);
        assertThat(nftRepository.findById(nft4.getId())).get().isEqualTo(nft4);
        assertThat(nftRepository.findById(nft5.getId())).get().isEqualTo(nft5);

        IterableAssert<NftTransfer> nftTransfers =
                assertThat(nftTransferRepository.findAll()).hasSize(3);
        nftTransfers.extracting(NftTransfer::getReceiverAccountId).containsOnly(newTreasury);
        nftTransfers.extracting(NftTransfer::getSenderAccountId).containsOnly(previousTreasury);
        nftTransfers.extracting(n -> n.getId().getTokenId()).containsOnly(tokenId);
        nftTransfers.extracting(n -> n.getId().getConsensusTimestamp()).containsOnly(consensusTimestamp);
        nftTransfers.extracting(n -> n.getId().getSerialNumber()).containsExactlyInAnyOrder(1L, 2L, 3L);
        nftTransfers.extracting(NftTransfer::getIsApproval).containsExactlyInAnyOrder(false, false, false);

        tokenAccountOldTreasury.setBalance(0);
        tokenAccountNewTreasury.setBalance(4);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccountOldTreasury, tokenAccountNewTreasury);
    }

    private void assertAccountUpdated(Nft nft, EntityId accountId) {
        AbstractObjectAssert<?, Nft> nftAssert =
                assertThat(nftRepository.findById(nft.getId())).get();
        nftAssert.extracting(Nft::getAccountId).isEqualTo(accountId);
        nftAssert.extracting(Nft::getModifiedTimestamp).isNotEqualTo(nft.getModifiedTimestamp());
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
