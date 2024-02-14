/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class TokenAccountRepositoryTest extends Web3IntegrationTest {
    private final int accountId = 123;
    private final TokenAccountRepository repository;

    @CsvSource(
            textBlock =
                    """
            false, true, true, FROZEN, GRANTED, FROZEN, GRANTED
            false, true, true, , , UNFROZEN, REVOKED
            true, true, false, , , FROZEN, NOT_APPLICABLE
            false, false, false, , , NOT_APPLICABLE, NOT_APPLICABLE
            """)
    @ParameterizedTest
    void findById(
            boolean freezeDefault,
            boolean hasFreezeKey,
            boolean hasKycKey,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus) {
        final var token = domainBuilder
                .token()
                .customize(t -> {
                    t.freezeDefault(freezeDefault);
                    if (!hasFreezeKey) {
                        t.freezeKey(null);
                    }

                    if (!hasKycKey) {
                        t.kycKey(null);
                    }
                })
                .persist();
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(
                        a -> a.freezeStatus(freezeStatus).kycStatus(kycStatus).tokenId(token.getTokenId()))
                .persist();

        assertThat(repository.findById(tokenAccount.getId())).hasValueSatisfying(account -> assertThat(account)
                .returns(expectedFreezeStatus, TokenAccount::getFreezeStatus)
                .returns(expectedKycStatus, TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance));
    }

    @CsvSource(textBlock = """
            ,
            FROZEN, GRANTED
            """)
    @ParameterizedTest
    void findByIdMissingToken(TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(a -> a.freezeStatus(freezeStatus).kycStatus(kycStatus))
                .persist();

        assertThat(repository.findById(tokenAccount.getId())).hasValueSatisfying(account -> assertThat(account)
                .returns(freezeStatus, TokenAccount::getFreezeStatus)
                .returns(kycStatus, TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance));
    }

    @Test
    void countByAccountIdAndAssociatedGroupedByBalanceIsPositive() {
        long accountId = 22L;
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).balance(23).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).balance(24).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).balance(0).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(false).accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId))
                .hasSize(2)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2), tuple(false, 1));
    }

    @Test
    void findByIdAndTimestampLessThanBlock() {
        domainBuilder.tokenAccount().persist();
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccount.getId().getAccountId(),
                        tokenAccount.getId().getTokenId(),
                        tokenAccount.getTimestampLower() + 1))
                .get()
                .isEqualTo(tokenAccount);
    }

    @Test
    void findByIdAndTimestampEqualToBlock() {
        domainBuilder.tokenAccount().persist();
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccount.getId().getAccountId(),
                        tokenAccount.getId().getTokenId(),
                        tokenAccount.getTimestampLower()))
                .get()
                .isEqualTo(tokenAccount);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlock() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccount.getId().getAccountId(),
                        tokenAccount.getId().getTokenId(),
                        tokenAccount.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalLessThanBlock() {
        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory.getId().getAccountId(),
                        tokenAccountHistory.getId().getTokenId(),
                        tokenAccountHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(tokenAccountHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlock() {
        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory.getId().getAccountId(),
                        tokenAccountHistory.getId().getTokenId(),
                        tokenAccountHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(tokenAccountHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlock() {
        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory.getId().getAccountId(),
                        tokenAccountHistory.getId().getTokenId(),
                        tokenAccountHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @CsvSource(
            textBlock =
                    """
            false, true, true, FROZEN, GRANTED, FROZEN, GRANTED
            false, true, true, , , UNFROZEN, REVOKED
            true, true, false, , , FROZEN, NOT_APPLICABLE
            false, false, false, , , NOT_APPLICABLE, NOT_APPLICABLE
            """)
    @ParameterizedTest
    void findByIdAndTimestampHistoricalReturnsLatestEntry(
            boolean freezeDefault,
            boolean hasFreezeKey,
            boolean hasKycKey,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus) {
        long accountId = 2L;
        final var token = domainBuilder
                .token()
                .customize(t -> {
                    t.freezeDefault(freezeDefault);
                    if (!hasFreezeKey) {
                        t.freezeKey(null);
                    }

                    if (!hasKycKey) {
                        t.kycKey(null);
                    }
                })
                .persist();
        final var tokenAccountHistory1 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(accountId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var tokenAccountHistory2 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(accountId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var latestTimestamp =
                Math.max(tokenAccountHistory1.getTimestampLower(), tokenAccountHistory2.getTimestampLower());

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory1.getId().getAccountId(),
                        tokenAccountHistory1.getId().getTokenId(),
                        latestTimestamp + 1))
                .get()
                .returns(latestTimestamp, TokenAccount::getTimestampLower)
                .returns(expectedFreezeStatus, TokenAccount::getFreezeStatus)
                .returns(expectedKycStatus, TokenAccount::getKycStatus);
    }

    @CsvSource(textBlock = """
            ,
            FROZEN, GRANTED
            """)
    @ParameterizedTest
    void findByIdAndTimestampHistoricalMissingTokenReturnsLatestEntry(
            TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        long accountId = 2L;
        long tokenId = 102L;
        final var tokenAccountHistory1 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(tokenId)
                        .accountId(accountId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var tokenAccountHistory2 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(tokenId)
                        .accountId(accountId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var latestTimestamp =
                Math.max(tokenAccountHistory1.getTimestampLower(), tokenAccountHistory2.getTimestampLower());

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory1.getId().getAccountId(),
                        tokenAccountHistory1.getId().getTokenId(),
                        latestTimestamp + 1))
                .get()
                .returns(latestTimestamp, TokenAccount::getTimestampLower)
                .returns(freezeStatus, TokenAccount::getFreezeStatus)
                .returns(kycStatus, TokenAccount::getKycStatus);
    }

    @Test
    void countByAccountIdAndTimestampLessThanBlock() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getAccountId(), tokenAccount.getTimestampLower() + 1))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 1));
    }

    @Test
    void countByAccountIdAndTimestampLessThanBlockSize() {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId).balance(0))
                .persist();
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId).balance(0))
                .persist();
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getId().getAccountId(), tokenAccount.getTimestampLower() + 1))
                .hasSize(2);
    }

    @Test
    void countByAccountIdAndTimestampEqualToBlock() {
        final var tokenAccount =
                domainBuilder.tokenAccount().customize(ta -> ta.balance(0)).persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getAccountId(), tokenAccount.getTimestampLower()))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(false, 1));
    }

    @Test
    void countByAccountIdAndTimestampGreaterThanBlock() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getId().getAccountId(), tokenAccount.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void countByAccountIdAndTimestampHistoricalLessThanBlock() {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, tokenAccountHistory.getTimestampLower() + 1))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2));
    }

    @Test
    void countByAccountIdAndTimestampHistoricalEqualToBlock() {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, tokenAccountHistory.getTimestampLower()))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2));
    }

    @Test
    void countByAccountIdAndTimestampHistoricalGreaterThanBlock() {
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccountHistory.getId().getAccountId(), tokenAccountHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void countByAccountIdAndTimestampHistoricalReturnsLatestEntry() {
        long accountId = 2L;
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId).balance(0))
                .persist();
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, tokenAccountHistory.getTimestampLower() + 1))
                .hasSize(2)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2), tuple(false, 1));
    }
}
