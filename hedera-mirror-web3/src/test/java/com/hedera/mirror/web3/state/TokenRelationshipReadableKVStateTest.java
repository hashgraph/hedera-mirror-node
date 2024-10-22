/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRelationshipReadableKVStateTest {

    @InjectMocks
    private TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Spy
    private ContractCallContext contractCallContext;

    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().shardNum(1L).realmNum(2L).accountNum(3L).build();
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().shardNum(4L).realmNum(5L).tokenNum(6L).build();

    private static final Optional<Long> timestamp = Optional.of(1234L);

    private static final long ACCOUNT_BALANCE = 3L;

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    private DomainBuilder domainBuilder;

    private TokenAccount tokenAccount;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void sizeIsAlwaysZero() {
        assertThat(tokenRelationshipReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(tokenRelationshipReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    @Test
    void getWithTokenIDNullReturnsNull() {
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId((TokenID) null)
                .accountId(ACCOUNT_ID)
                .build();
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isNull();
    }

    @Test
    void getWithAccountIDNullReturnsNull() {
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId((AccountID) null)
                .build();
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isNull();
    }

    @Test
    void getWithTokenIDDefaultReturnsNull() {
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TokenID.DEFAULT)
                .accountId(ACCOUNT_ID)
                .build();
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isNull();
    }

    @Test
    void getWithAccountIDDefaultReturnsNull() {
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(AccountID.DEFAULT)
                .build();
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isNull();
    }

    @Test
    void getWithTokenAccountNullReturnsNull() {
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .build();
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isNull();
    }

    @Test
    void getWithFungibleTokenAccountBalance() {
        setUpTokenAccount();
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .build();
        final var expected = TokenRelation.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .balance(ACCOUNT_BALANCE)
                .frozen(true)
                .kycGranted(true)
                .automaticAssociation(true)
                .build();
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isEqualTo(expected);
    }

    @Test
    void getWithFungibleTokenAccountBalanceHistorical() {
        setUpTokenAccount();
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .build();
        final var expected = TokenRelation.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .balance(ACCOUNT_BALANCE)
                .frozen(true)
                .kycGranted(true)
                .automaticAssociation(true)
                .build();
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenRepository.findTypeByTokenId(anyLong())).thenReturn(Optional.of(TokenTypeEnum.FUNGIBLE_COMMON));
        when(tokenAccountRepository.findByIdAndTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(tokenAccount));
        when(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(ACCOUNT_BALANCE));
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getWithNftAccountBalance(final boolean areFlagsEnabled) {
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(toEntityId(TOKEN_ID).getId())
                        .accountId(toEntityId(ACCOUNT_ID).getId())
                        .balance(ACCOUNT_BALANCE)
                        .kycStatus(areFlagsEnabled ? TokenKycStatusEnum.GRANTED : TokenKycStatusEnum.REVOKED)
                        .freezeStatus(areFlagsEnabled ? TokenFreezeStatusEnum.FROZEN : TokenFreezeStatusEnum.UNFROZEN)
                        .automaticAssociation(areFlagsEnabled)
                        .createdTimestamp(timestamp.get()))
                .get();
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .build();
        final var expected = TokenRelation.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .balance(ACCOUNT_BALANCE)
                .frozen(areFlagsEnabled)
                .kycGranted(areFlagsEnabled)
                .automaticAssociation(areFlagsEnabled)
                .build();
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isEqualTo(expected);
    }

    @Test
    void getWithNftAccountBalanceHistorical() {
        setUpTokenAccount();
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .build();
        final var expected = TokenRelation.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .balance(ACCOUNT_BALANCE)
                .frozen(true)
                .kycGranted(true)
                .automaticAssociation(true)
                .build();
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenRepository.findTypeByTokenId(anyLong())).thenReturn(Optional.of(TokenTypeEnum.NON_FUNGIBLE_UNIQUE));
        when(tokenAccountRepository.findByIdAndTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(tokenAccount));
        when(nftRepository.nftBalanceByAccountIdTokenIdAndTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(ACCOUNT_BALANCE));
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isEqualTo(expected);
    }

    @Test
    void getWithTokenAccountBalanceHistoricalAccountNotFoundReturnsZeroBalance() {
        setUpTokenAccount();
        final var entityIDPair = EntityIDPair.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .build();
        final var expected = TokenRelation.newBuilder()
                .tokenId(TOKEN_ID)
                .accountId(ACCOUNT_ID)
                .balance(0L)
                .frozen(true)
                .kycGranted(true)
                .automaticAssociation(true)
                .build();
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenAccountRepository.findByIdAndTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(tokenAccount));
        assertThat(tokenRelationshipReadableKVState.get(entityIDPair)).isEqualTo(expected);
    }

    private void setUpTokenAccount() {
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(toEntityId(TOKEN_ID).getId())
                        .accountId(toEntityId(ACCOUNT_ID).getId())
                        .balance(ACCOUNT_BALANCE)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .automaticAssociation(true)
                        .createdTimestamp(timestamp.get()))
                .get();
    }
}
