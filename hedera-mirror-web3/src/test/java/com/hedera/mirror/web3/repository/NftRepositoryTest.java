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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftRepositoryTest extends Web3IntegrationTest {

    private static final EntityId accountId = EntityId.of(0L, 0L, 56L);
    private final NftRepository nftRepository;

    @Test
    void findById() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findById(nft.getId())).hasValueSatisfying(actual -> assertThat(actual)
                .returns(nft.getSpender(), Nft::getSpender)
                .returns(nft.getAccountId(), Nft::getAccountId)
                .returns(nft.getMetadata(), Nft::getMetadata));
    }

    @Test
    void findByIdReturnsDeleted() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(nft.getTokenId()).deleted(true))
                .persist();

        assertThat(nftRepository.findById(nft.getId())).hasValueSatisfying(actual -> assertThat(actual)
                .returns(nft.getSpender(), Nft::getSpender)
                .returns(nft.getAccountId(), Nft::getAccountId)
                .returns(nft.getMetadata(), Nft::getMetadata));
    }

    @Test
    void findActiveById() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findActiveById(nft.getTokenId(), nft.getId().getSerialNumber()))
                .hasValueSatisfying(actual -> assertThat(actual)
                        .returns(nft.getSpender(), Nft::getSpender)
                        .returns(nft.getAccountId(), Nft::getAccountId)
                        .returns(nft.getMetadata(), Nft::getMetadata));
    }

    @Test
    void findActiveByIdReturnEmptyIfDeleted() {
        final var nft = domainBuilder.nft().customize(n -> n.deleted(true)).persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findActiveById(nft.getTokenId(), nft.getId().getSerialNumber()))
                .isEmpty();
    }

    @Test
    void findActiveByIdReturnEmptyIfEntityDeleted() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(nft.getTokenId()).deleted(true))
                .persist();

        assertThat(nftRepository.findActiveById(nft.getTokenId(), nft.getId().getSerialNumber()))
                .isEmpty();
    }

    @Test
    void findActiveByIdReturnObjectIfEntityMissing() {
        final var nft = domainBuilder.nft().persist();

        assertThat(nftRepository.findActiveById(nft.getTokenId(), nft.getId().getSerialNumber()))
                .isNotEmpty();
    }

    @Test
    void countByAccountIdNotDeleted() {
        final var firstNft =
                domainBuilder.nft().customize(n -> n.accountId(accountId)).persist();
        domainBuilder.entity().customize(e -> e.id(firstNft.getTokenId())).persist();

        final var secondNft =
                domainBuilder.nft().customize(n -> n.accountId(accountId)).persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(secondNft.getTokenId()).deleted(null))
                .persist();

        assertThat(nftRepository.countByAccountIdNotDeleted(accountId.getId())).isEqualTo(2);
    }

    @Test
    void countByAccountIdNotDeletedShouldNotCountDeletedNft() {
        final var deletedNft = domainBuilder
                .nft()
                .customize(n -> n.accountId(accountId).deleted(true))
                .persist();
        domainBuilder.entity().customize(e -> e.id(deletedNft.getTokenId())).persist();

        assertThat(nftRepository.countByAccountIdNotDeleted(accountId.getId())).isZero();
    }

    @Test
    void countByAccountIdNotDeletedShouldNotCountDeletedEntity() {
        final var deletedEntityNft =
                domainBuilder.nft().customize(n -> n.accountId(accountId)).persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(deletedEntityNft.getTokenId()).deleted(true))
                .persist();

        assertThat(nftRepository.countByAccountIdNotDeleted(accountId.getId())).isZero();
    }

    @Test
    void findActiveByIdAndTimestampLessThanBlock() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower() + 1))
                .get()
                .isEqualTo(nft);
    }

    @Test
    void findActiveByIdAndTimestampEqualToBlock() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower()))
                .get()
                .isEqualTo(nft);
    }

    @Test
    void findActiveByIdAndTimestampGreaterThanBlock() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampNftDeleted() {
        final var nft = domainBuilder.nft().customize(n -> n.deleted(true)).persist();
        domainBuilder.entity().customize(e -> e.id(nft.getTokenId())).persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampEntityDeletedAndNftStillValid() {
        final var nft = domainBuilder.nft().persist();
        long blockTimestamp = nft.getTimestampLower();
        domainBuilder
                .entity()
                .customize(e -> e.id(nft.getTokenId())
                        .timestampRange(Range.closedOpen(blockTimestamp + 10, blockTimestamp + 20))
                        .deleted(true))
                .persist();

        // Tests that NFT records remain valid if their linked entity is marked as deleted after the blockTimestamp.
        // Verifies NFTs are considered active at blockTimestamp, even if the associated entity is currently deleted.

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower()))
                .get()
                .isEqualTo(nft);
    }

    @Test
    void findActiveByIdAndTimestampEntityDeleteAndNftIsDeleted() {
        final var nft = domainBuilder.nft().customize(n -> n.deleted(true)).persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(nft.getTokenId()).deleted(true))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampEntityDeleteAndNftHistoryIsDeleted() {
        final var nftHistory =
                domainBuilder.nftHistory().customize(n -> n.deleted(true)).persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(nftHistory.getTokenId()).deleted(true))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(), nftHistory.getId().getSerialNumber(), nftHistory.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampEntityDeletedIsNull() {
        final var nft = domainBuilder.nft().persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(nft.getTokenId()).deleted(null))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nft.getTokenId(), nft.getId().getSerialNumber(), nft.getTimestampLower()))
                .get()
                .isEqualTo(nft);
    }

    @Test
    void findActiveByIdAndTimestampHistoricalLessThanBlock() {
        final var nftHistory = domainBuilder.nftHistory().persist();
        domainBuilder.entity().customize(e -> e.id(nftHistory.getTokenId())).persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(),
                        nftHistory.getId().getSerialNumber(),
                        nftHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(nftHistory);
    }

    @Test
    void findActiveByIdAndTimestampHistoricalEqualToBlock() {
        final var nftHistory = domainBuilder.nftHistory().persist();
        domainBuilder.entity().customize(e -> e.id(nftHistory.getTokenId())).persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(), nftHistory.getId().getSerialNumber(), nftHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(nftHistory);
    }

    @Test
    void findActiveByIdAndTimestampHistoricalGreaterThanBlock() {
        final var nftHistory = domainBuilder.nftHistory().persist();
        domainBuilder
                .entityHistory()
                .customize(e -> e.id(nftHistory.getTokenId()))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(),
                        nftHistory.getId().getSerialNumber(),
                        nftHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampHistoricalNftDeleted() {
        final var nftHistory =
                domainBuilder.nftHistory().customize(n -> n.deleted(true)).persist();
        domainBuilder
                .entityHistory()
                .customize(e -> e.id(nftHistory.getTokenId()))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(), nftHistory.getId().getSerialNumber(), nftHistory.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampHistoricalEntityDeleted() {
        final var nftHistory = domainBuilder.nftHistory().persist();
        domainBuilder
                .entityHistory()
                .customize(e -> e.id(nftHistory.getTokenId()).deleted(true))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(), nftHistory.getId().getSerialNumber(), nftHistory.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findActiveByIdAndTimestampHistoricalEntityDeletedIsNull() {
        final var nftHistory = domainBuilder.nftHistory().persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(nftHistory.getTokenId()).deleted(null))
                .persist();

        assertThat(nftRepository.findActiveByIdAndTimestamp(
                        nftHistory.getTokenId(), nftHistory.getId().getSerialNumber(), nftHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(nftHistory);
    }

    @Test
    void findActiveByIdAndTimestampReturnsLatestEntry() {
        long tokenId = 1L;
        long serialNumber = 1L;
        final var nftHistory1 = domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId).serialNumber(serialNumber))
                .persist();
        domainBuilder.entity().customize(e -> e.id(tokenId)).persist();

        final var nftHistory2 = domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId).serialNumber(serialNumber))
                .persist();

        final var latestTimestamp = Math.max(nftHistory1.getTimestampLower(), nftHistory2.getTimestampLower());

        assertThat(nftRepository.findActiveByIdAndTimestamp(tokenId, serialNumber, latestTimestamp + 1))
                .hasValueSatisfying(actual -> assertThat(actual).returns(latestTimestamp, Nft::getTimestampLower));
    }
}
