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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Nft;

class NftRepositoryTest extends AbstractRepositoryTest {

    @Resource
    protected NftRepository repository;

    @Test
    void save() {
        Nft savedNft = repository.save(nft("0.0.2", 1, 1));
        assertThat(repository.findById(savedNft.getId()).get()).isEqualTo(savedNft);
    }

    @Test
    void updateDeleted() {
        Nft savedNft = repository.save(nft("0.0.3", 2, 2));
        repository.burnOrWipeNft(savedNft.getId(), 3L);
        savedNft.setDeleted(true);
        savedNft.setModifiedTimestamp(3L);
        assertThat(repository.findById(savedNft.getId()).get()).isEqualTo(savedNft);
    }

    @Test
    void updateDeletedMissingNft() {
        Nft.Id nftId = new Nft.Id(1L, EntityId.of("0.0.1", EntityTypeEnum.TOKEN));
        repository.burnOrWipeNft(nftId, 1L);
        assertThat(repository.findById(nftId)).isNotPresent();
    }

    @Test
    void updateAccountId() {
        Nft savedNft = repository.save(nft("0.0.3", 2, 2));
        EntityId accountId = EntityId.of("0.0.10", EntityTypeEnum.ACCOUNT);
        repository.transferNftOwnership(savedNft.getId(), accountId, 3L);
        savedNft.setAccountId(accountId);
        savedNft.setModifiedTimestamp(3L);
        assertThat(repository.findById(savedNft.getId()).get()).isEqualTo(savedNft);
    }

    @Test
    void updateAccountIdMissingNft() {
        Nft.Id nftId = new Nft.Id(1L, EntityId.of("0.0.1", EntityTypeEnum.TOKEN));
        EntityId accountId = EntityId.of("0.0.10", EntityTypeEnum.ACCOUNT);
        repository.transferNftOwnership(nftId, accountId, 3L);
        assertThat(repository.findById(nftId)).isNotPresent();
    }

    private Nft nft(String tokenId, long serialNumber, long consensusTimestamp) {
        Nft nft = new Nft();
        nft.setCreatedTimestamp(consensusTimestamp);
        nft.setMetadata(new byte[] {1});
        nft.setId(new Nft.Id(serialNumber, EntityId.of(tokenId, EntityTypeEnum.TOKEN)));
        nft.setAccountId(EntityId.of("0.0.1", EntityTypeEnum.ACCOUNT));
        return nft;
    }
}
