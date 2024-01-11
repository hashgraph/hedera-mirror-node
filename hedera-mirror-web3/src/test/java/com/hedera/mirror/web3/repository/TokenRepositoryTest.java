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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.web3.Web3IntegrationTest;
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
    void findFungibleTotalSupplyByTokenIdAndTimestampBurn() {
        final var totalSupply = 9L;
        final var change = -1L;
        final var totalSupplyHistorical = totalSupply - change;
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true).totalSupply(totalSupply))
                .persist();

        final var tokenBurn = domainBuilder
                .tokenTransfer()
                .customize(tr -> tr.id(new TokenTransfer.Id(
                                tokenHistory.getTimestampLower() + 2,
                                EntityId.of(tokenHistory.getTokenId()),
                                tokenHistory.getTreasuryAccountId()))
                        .amount(change))
                .persist();

        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(),
                        tokenBurn.getId().getConsensusTimestamp() - 1))
                .isEqualTo(totalSupplyHistorical);
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestampMint() {
        final var totalSupply = 9L;
        final var change = 1L;
        final var totalSupplyHistorical = totalSupply - change;
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true).totalSupply(totalSupply))
                .persist();

        final var tokenMint = domainBuilder
                .tokenTransfer()
                .customize(tr -> tr.id(new TokenTransfer.Id(
                                tokenHistory.getTimestampLower() + 2,
                                EntityId.of(tokenHistory.getTokenId()),
                                tokenHistory.getTreasuryAccountId()))
                        .amount(change))
                .persist();

        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(),
                        tokenMint.getId().getConsensusTimestamp() - 1))
                .isEqualTo(totalSupplyHistorical);
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestampDoNotIncludeTransfer() {
        final var totalSupply = 9L;
        final var change = -1L;
        final var totalSupplyHistorical = totalSupply;
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true).totalSupply(totalSupply))
                .persist();

        final var tokenBurn = domainBuilder
                .tokenTransfer()
                .customize(tr -> tr.id(new TokenTransfer.Id(
                                tokenHistory.getTimestampLower(),
                                EntityId.of(tokenHistory.getTokenId()),
                                tokenHistory.getTreasuryAccountId()))
                        .amount(change))
                .persist();

        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(),
                        tokenHistory.getTimestampLower() + 1))
                .isEqualTo(totalSupplyHistorical);
    }

    @Test
    void findFungibleTotalSupplyByTokenIdAndTimestampDoNotMatchTokenHistory() {
        final var totalSupply = 9L;
        final var change = -1L;
        final var totalSupplyHistorical = 0L;
        final var tokenHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true).totalSupply(totalSupply))
                .persist();

        final var tokenBurn = domainBuilder
                .tokenTransfer()
                .customize(tr -> tr.id(new TokenTransfer.Id(
                                tokenHistory.getTimestampLower() - 2,
                                EntityId.of(tokenHistory.getTokenId()),
                                tokenHistory.getTreasuryAccountId()))
                        .amount(change))
                .persist();

        assertThat(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(
                        tokenHistory.getTokenId(),
                        tokenHistory.getTimestampLower() - 1))
                .isEqualTo(totalSupplyHistorical);
    }
}
