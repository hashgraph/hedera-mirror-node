/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.web3.Web3IntegrationTest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenRepositoryTest extends Web3IntegrationTest {

    private final TokenRepository tokenRepository;

    @Test
    void findById() {
        final var token = domainBuilder
                .token()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository.findById(token.getTokenId()).get())
                .returns(token.getName(), Token::getName)
                .returns(token.getSymbol(), Token::getSymbol)
                .returns(token.getTotalSupply(), Token::getTotalSupply)
                .returns(token.getDecimals(), Token::getDecimals)
                .returns(token.getType(), Token::getType)
                .returns(token.getFreezeDefault(), Token::getFreezeDefault)
                .returns(token.getKycKey(), Token::getKycKey);
    }

    @Test
    void findByIdAndTimestampRangeLessThanBlockTimestamp() {
        final var token = domainBuilder
                .token()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository
                        .findByTokenIdAndTimestamp(token.getTokenId(), token.getTimestampLower() + 1)
                        .get())
                .returns(token.getName(), Token::getName)
                .returns(token.getSymbol(), Token::getSymbol)
                .returns(token.getTotalSupply(), Token::getTotalSupply)
                .returns(token.getDecimals(), Token::getDecimals)
                .returns(token.getType(), Token::getType)
                .returns(token.getFreezeDefault(), Token::getFreezeDefault)
                .returns(token.getKycKey(), Token::getKycKey);
    }

    @Test
    void findByIdAndTimestampRangeEqualToBlockTimestamp() {
        final var token = domainBuilder
                .token()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository
                        .findByTokenIdAndTimestamp(token.getTokenId(), token.getTimestampLower())
                        .get())
                .returns(token.getName(), Token::getName)
                .returns(token.getSymbol(), Token::getSymbol)
                .returns(token.getTotalSupply(), Token::getTotalSupply)
                .returns(token.getDecimals(), Token::getDecimals)
                .returns(token.getType(), Token::getType)
                .returns(token.getFreezeDefault(), Token::getFreezeDefault)
                .returns(token.getKycKey(), Token::getKycKey);
    }

    @Test
    void findByIdAndTimestampRangeGreaterThanBlockTimestamp() {
        final var token = domainBuilder
                .token()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository.findByTokenIdAndTimestamp(token.getTokenId(), token.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalByIdAndTimestampRangeLessThanBlockTimestamp() {
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository.findByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(), tokenHistory.getTimestampLower() + 1))
                .isPresent();
    }

    @Test
    void findHistoricalByIdAndTimestampRangeEqualToBlockTimestamp() {
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository.findByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(), tokenHistory.getTimestampLower()))
                .isPresent();
    }

    @Test
    void findHistoricalByIdAndTimestampRangeGreaterThanBlockTimestamp() {
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true))
                .persist();

        assertThat(tokenRepository.findByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(), tokenHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestamp() {
        // given
        var tokenId = domainBuilder.entityId();
        long blockTimestamp = domainBuilder.timestamp();
        long snapshotTimestamp = blockTimestamp - Duration.ofMinutes(12).toNanos();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(snapshotTimestamp, EntityId.of(2))))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(snapshotTimestamp, domainBuilder.entityId(), tokenId)))
                .persist();
        var tokenBalance2 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(snapshotTimestamp - 1, domainBuilder.entityId(), tokenId)))
                .persist();
        // a token balance after the block timestamp
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(blockTimestamp + 1, domainBuilder.entityId(), tokenId)))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 1, tokenId, domainBuilder.entityId()))
                        .amount(1L))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 1, tokenId, domainBuilder.entityId()))
                        .amount(-1L))
                .persist();
        var tokenMintTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 2, tokenId, domainBuilder.entityId())))
                .persist();
        var tokenBurnTransfer = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 3, tokenId, domainBuilder.entityId()))
                        .amount(-2L))
                .persist();
        // A mint transfer at the block timestamp
        var tokenMintTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(blockTimestamp, tokenId, domainBuilder.entityId())))
                .persist();

        // when, then
        long expectedTotalSupply = tokenBalance1.getBalance()
                + tokenBalance2.getBalance()
                + tokenMintTransfer1.getAmount()
                + tokenBurnTransfer.getAmount()
                + tokenMintTransfer2.getAmount();
        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(tokenId.getId(), blockTimestamp))
                .isEqualTo(expectedTotalSupply);
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestampEmpty() {
        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(
                        domainBuilder.id(), domainBuilder.timestamp()))
                .isZero();
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestampNoSnapshot() {
        // given
        var tokenId = domainBuilder.entityId();
        long blockTimestamp = domainBuilder.timestamp();
        long snapshotTimestamp = blockTimestamp - Duration.ofMinutes(12).toNanos();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(snapshotTimestamp, EntityId.of(2))))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 1, tokenId, domainBuilder.entityId()))
                        .amount(1L))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 1, tokenId, domainBuilder.entityId()))
                        .amount(-1L))
                .persist();
        var tokenMintTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 2, tokenId, domainBuilder.entityId())))
                .persist();
        var tokenBurnTransfer = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 3, tokenId, domainBuilder.entityId()))
                        .amount(-2L))
                .persist();
        // A mint transfer at the block timestamp
        var tokenMintTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(blockTimestamp, tokenId, domainBuilder.entityId())))
                .persist();

        // when, then
        long expectedTotalSupply =
                tokenMintTransfer1.getAmount() + tokenBurnTransfer.getAmount() + tokenMintTransfer2.getAmount();
        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(tokenId.getId(), blockTimestamp))
                .isEqualTo(expectedTotalSupply);
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestampNoTransfer() {
        // given
        var tokenId = domainBuilder.entityId();
        long blockTimestamp = domainBuilder.timestamp();
        long snapshotTimestamp = blockTimestamp - Duration.ofMinutes(12).toNanos();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(snapshotTimestamp, EntityId.of(2))))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(snapshotTimestamp, domainBuilder.entityId(), tokenId)))
                .persist();
        var tokenBalance2 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(snapshotTimestamp - 1, domainBuilder.entityId(), tokenId)))
                .persist();
        // a token balance after the block timestamp
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(blockTimestamp + 1, domainBuilder.entityId(), tokenId)))
                .persist();

        // when, then
        long expectedTotalSupply = tokenBalance1.getBalance() + tokenBalance2.getBalance();
        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(tokenId.getId(), blockTimestamp))
                .isEqualTo(expectedTotalSupply);
    }
}
