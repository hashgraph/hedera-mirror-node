/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftRepositoryTest extends Web3IntegrationTest {
    private final EntityId accountId = new EntityId(0L, 0L, 56L, EntityType.ACCOUNT);
    private final NftRepository nftRepository;

    @Test
    void findById() {
        final var spender = new EntityId(0L, 0L, 56L, EntityType.ACCOUNT);
        final var nft = domainBuilder.nft().customize(n -> n.spender(spender)).persist();

        assertThat(nftRepository.findById(nft.getId())).hasValueSatisfying(actual -> assertThat(actual)
                .returns(nft.getSpender(), Nft::getSpender)
                .returns(nft.getAccountId(), Nft::getAccountId)
                .returns(nft.getMetadata(), Nft::getMetadata));
    }

    @Test
    void countByAccountIdNotDeleted() {
        final var firstNft =
                domainBuilder.nft().customize(n -> n.accountId(accountId)).persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(firstNft.getId().getTokenId().getId()))
                .persist();

        final var secondNft =
                domainBuilder.nft().customize(n -> n.accountId(accountId)).persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(secondNft.getId().getTokenId().getId()).deleted(null))
                .persist();

        assertThat(nftRepository.countByAccountIdNotDeleted(accountId.getId())).isEqualTo(2);
    }

    @Test
    void countByAccountIdNotDeletedShouldNotCountDeletedNft() {
        final var deletedNft = domainBuilder
                .nft()
                .customize(n -> n.accountId(accountId).deleted(true))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(deletedNft.getId().getTokenId().getId()))
                .persist();

        assertThat(nftRepository.countByAccountIdNotDeleted(accountId.getId())).isZero();
    }

    @Test
    void countByAccountIdNotDeletedShouldNotCountDeletedEntity() {
        final var deletedEntityNft =
                domainBuilder.nft().customize(n -> n.accountId(accountId)).persist();
        domainBuilder
                .entity()
                .customize(
                        e -> e.id(deletedEntityNft.getId().getTokenId().getId()).deleted(true))
                .persist();

        assertThat(nftRepository.countByAccountIdNotDeleted(accountId.getId())).isZero();
    }
}
