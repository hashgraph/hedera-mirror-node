package com.hedera.mirror.importer.repository;

/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.mirror.importer.domain.Token;

public class NftRepositoryTest extends AbstractRepositoryTest {

    @Resource
    protected NftRepository repository;

    @Test
    void save() {
        Nft savedNft = repository.save(nft("0.0.2", 1, 1));
        assertThat(savedNft)
                .isEqualTo(repository.findById(savedNft.getCreatedTimestamp())
                        .get());
    }

    @Test
    void findByTokenIdAndSerialNumber() {
        Nft nftOne = repository.save(nft("0.0.2", 1, 1));
        Nft nftTwo = repository.save(nft("0.0.2", 2, 2));
        Nft nftThree = repository.save(nft("0.0.3", 1, 3));
        //TODO Clean up, just have this for coverage
        assertThat(nftOne)
                .isEqualTo(repository
                        .findByTokenIdAndSerialNumber(new Token.Id(EntityId.of("0.0.2", EntityTypeEnum.TOKEN)), 1)
                        .get());
        assertThat(nftTwo)
                .isEqualTo(repository
                        .findByTokenIdAndSerialNumber(new Token.Id(EntityId.of("0.0.2", EntityTypeEnum.TOKEN)), 2)
                        .get());
        assertThat(nftThree)
                .isEqualTo(repository
                        .findByTokenIdAndSerialNumber(new Token.Id(EntityId.of("0.0.3", EntityTypeEnum.TOKEN)), 1)
                        .get());
    }

    private Nft nft(String tokenId, long serialNumber, long consensusTimestamp) {
        Nft nft = new Nft();
        nft.setCreatedTimestamp(consensusTimestamp);
        nft.setMetadata(new byte[] {1});
        nft.setSerialNumber(serialNumber);
        nft.setTokenId(EntityId.of(tokenId, EntityTypeEnum.TOKEN));
        nft.setAccountId(EntityId.of("0.0.1", EntityTypeEnum.ACCOUNT));
        return nft;
    }
}
