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

    private Account account;
    private Token token;

    @BeforeEach
    void setup() {
        account = mock(Account.class);
        when(account.getId()).thenReturn(new Id(1, 2, 3));
        token = mock(Token.class);
        when(token.getId()).thenReturn(new Id(4, 5, 6));

        when(accountDatabaseAccessor.get(any())).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(any())).thenReturn(Optional.of(token));
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

        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(Address.ALTBN128_MUL, Address.ALTBN128_ADD)))
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
    void getBooleansFalse() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .automaticAssociation(false))
                .get();

        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(Address.ALTBN128_MUL, Address.ALTBN128_ADD)))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(false, TokenRelationship::isFrozen)
                        .returns(false, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isAutomaticAssociation));
    }

    @Test
    void getEmptyIfTokenNotAssociated() {
        final var tokenAccount =
                domainBuilder.tokenAccount().customize(t -> t.associated(false)).get();

        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(Address.ALTBN128_MUL, Address.ALTBN128_ADD)))
                .isEmpty();
    }
}
