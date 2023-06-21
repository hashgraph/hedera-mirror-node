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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftRepositoryTest extends AbstractRepositoryTest {

    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;

    @Test
    void save() {
        var savedNft = nftRepository.save(domainBuilder.nft().get());
        assertThat(nftRepository.findById(savedNft.getId())).contains(savedNft);
    }

    @Test
    void updateTreasury() {
        // given
        var newTreasury = domainBuilder.entityId(EntityType.ACCOUNT);
        var oldTreasury = domainBuilder.entityId(EntityType.ACCOUNT);
        long tokenId = domainBuilder.id();

        var tokenAccountNewTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(newTreasury.getId()).balance(1).tokenId(tokenId))
                .persist();
        var tokenAccountOldTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(oldTreasury.getId()).balance(3).tokenId(tokenId))
                .persist();
        var nft1 = domainBuilder
                .nft()
                .customize(n -> n.accountId(oldTreasury).tokenId(tokenId))
                .persist();
        var nft2 = domainBuilder
                .nft()
                .customize(n -> n.accountId(oldTreasury).tokenId(tokenId))
                .persist();
        // The history row should preserve the delegating spender and spender
        var nft3 = domainBuilder
                .nft()
                .customize(n -> n.accountId(oldTreasury)
                        .delegatingSpender(domainBuilder.entityId(EntityType.ACCOUNT))
                        .spender(domainBuilder.entityId(EntityType.ACCOUNT))
                        .tokenId(tokenId))
                .persist();
        // Already owned by new treasury before the update
        var nft4 = domainBuilder
                .nft()
                .customize(n -> n.accountId(newTreasury).tokenId(tokenId))
                .persist();
        // Different token
        var nft5 = domainBuilder.nft().customize(n -> n.accountId(oldTreasury)).persist();
        // Owned by a third account
        var nft6 = domainBuilder.nft().customize(n -> n.tokenId(tokenId)).persist();

        // when
        var updateTimestamp = domainBuilder.timestamp();
        nftRepository.updateTreasury(updateTimestamp, newTreasury.getId(), oldTreasury.getId(), tokenId);

        // then
        tokenAccountOldTreasury.setBalance(0);
        tokenAccountNewTreasury.setBalance(4);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccountOldTreasury, tokenAccountNewTreasury);

        var expectedNftList = Stream.concat(
                        Stream.of(nft1, nft2, nft3).map(Nft::toBuilder).map(n -> n.accountId(newTreasury)
                                .delegatingSpender(null)
                                .spender(null)
                                .timestampRange(Range.atLeast(updateTimestamp))
                                .build()),
                        Stream.of(nft4, nft5, nft6))
                .toList();
        // The only change to the history rows is closing the timestamp range
        var expectedNftHistoryList = Stream.of(nft1, nft2, nft3)
                .peek(n -> n.setTimestampUpper(updateTimestamp))
                .toList();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNftList);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(expectedNftHistoryList);
    }
}
