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

package com.hedera.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.EntityIdUtils;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRelationshipDatabaseAccessorTest {
    @InjectMocks
    TokenRelationshipDatabaseAccessor tokenRelationshipDatabaseAccessor;

    @Mock
    private TokenDatabaseAccessor tokenDatabaseAccessor;

    @Mock
    private AccountDatabaseAccessor accountDatabaseAccessor;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    private final DomainBuilder domainBuilder = new DomainBuilder();

    private static final long TIMESTAMP = 1234L;
    private Account account;
    private Token token;
    private static final Address TOKEN_ADDRESS = Address.ALTBN128_ADD;
    private static final Address ACCOUNT_ADDRESS = Address.ALTBN128_MUL;

    @BeforeEach
    void setup() {
        account = mock(Account.class);
        when(account.getId()).thenReturn(new Id(1, 2, 3));
        token = mock(Token.class);
        when(token.getId()).thenReturn(new Id(4, 5, 6));
    }

    @Test
    void get() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .automaticAssociation(true))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(token));
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), DatabaseAccessor.UNSET_TIMESTAMP))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(true, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getWhenKycNotApplicable() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                        .automaticAssociation(true))
                .get();

        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(token));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), DatabaseAccessor.UNSET_TIMESTAMP))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(true, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getWhenKycNotApplicableHistorical() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .tokenId(EntityIdUtils.entityIdFromId(token.getId()).getId())
                        .accountId(EntityIdUtils.entityIdFromId(account.getId()).getId())
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                        .automaticAssociation(true))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, TIMESTAMP)).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, TIMESTAMP)).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findByIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), TIMESTAMP))
                .thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), TIMESTAMP))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(true, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getWhenKycRevoked() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .automaticAssociation(true))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(token));
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), DatabaseAccessor.UNSET_TIMESTAMP))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(false, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getBooleansFalse() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .automaticAssociation(false))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, DatabaseAccessor.UNSET_TIMESTAMP))
                .thenReturn(Optional.of(token));
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), DatabaseAccessor.UNSET_TIMESTAMP))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(false, TokenRelationship::isFrozen)
                        .returns(false, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isAutomaticAssociation));
    }
}
