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

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.mirror.web3.evm.store.CachingStateFrame.Accessor;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssociateLogicTest {

    @Mock
    private CachingStateFrame<Object> cachingStateFrame;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private Accessor<Object, Account> accountAccessor;

    @Mock
    private Accessor<Object, Token> tokenAccessor;

    @Mock
    private Accessor<Object, TokenRelationship> tokenRelationshipAccessor;

    @Mock
    private StackedStateFrames<Object> stackedStateFrames;

    @Mock
    private Account account;

    @Mock
    Account.AccountBuilder accountBuilder;

    @Mock
    private Token token;

    private AssociateLogic associateLogic;

    @BeforeEach
    public void setUp() {
        when(stackedStateFrames.top()).thenReturn(cachingStateFrame);
        when(cachingStateFrame.getAccessor(Account.class)).thenReturn(accountAccessor);
        when(cachingStateFrame.getAccessor(Token.class)).thenReturn(tokenAccessor);
        when(cachingStateFrame.getAccessor(TokenRelationship.class)).thenReturn(tokenRelationshipAccessor);
        associateLogic = new AssociateLogic(stackedStateFrames, mirrorNodeEvmProperties);
    }

    Address accountAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");

    Address tokenAddress = Address.fromHexString("0x0000000000000000000000000000000000000182");

    @Test
    void throwErrorWhenAccountNotFound() {
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, List.of(tokenAddress)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "detail", String.format("Association with account %s failed", accountAddress));
    }

    @Test
    void throwErrorWhenTokenNotFound() {
        when(accountAccessor.get(any())).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, List.of(tokenAddress)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("detail", String.format("Association with token %s failed", tokenAddress));
    }

    @Test
    void failsOnCrossingAssociationLimit() {
        when(accountAccessor.get(accountAddress)).thenReturn(Optional.of(account));
        when(tokenAccessor.get(tokenAddress)).thenReturn(Optional.of(token));
        when(account.getNumAssociations()).thenReturn(5);
        when(mirrorNodeEvmProperties.isTokenAssociationsLimited()).thenReturn(true);
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(3);

        // expect:
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, List.of(tokenAddress)))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED.name());
    }

    @Test
    void failsOnAssociatingWithAlreadyRelatedToken() {
        setupAccount();
        setupToken();
        when(tokenRelationshipAccessor.get(any())).thenReturn(Optional.of(mock(TokenRelationship.class)));

        assertThatThrownBy(() -> associateLogic.associate(accountAddress, List.of(tokenAddress)))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT.name());
    }

    @Test
    void canAssociateWithNewToken() {
        final var modifiedAccount = setupAccount();
        when(modifiedAccount.getAccountAddress()).thenReturn(accountAddress);

        setupToken();

        associateLogic.associate(accountAddress, List.of(tokenAddress));

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        final var tokenRelationship = new TokenRelationship(token, modifiedAccount);
        verify(tokenRelationshipAccessor).set(tokenRelationShipKey, tokenRelationship);
    }

    @Test
    void updatesAccount() {
        final var modifiedAccount = setupAccount();
        when(modifiedAccount.getAccountAddress()).thenReturn(accountAddress);
        when(account.getNumAssociations()).thenReturn(5);

        setupToken();

        associateLogic.associate(accountAddress, List.of(tokenAddress));

        verify(accountAccessor).set(accountAddress, modifiedAccount);
        verify(accountBuilder).numAssociations(6);
    }

    @Test
    void commitsOnSuccess() {
        setupAccount();
        setupToken();

        associateLogic.associate(accountAddress, List.of(tokenAddress));

        verify(stackedStateFrames.top()).commit();
    }

    private Account setupAccount() {
        when(accountAccessor.get(accountAddress)).thenReturn(Optional.of(account));
        when(account.modificationBuilder()).thenReturn(accountBuilder);
        when(accountBuilder.numAssociations(anyInt())).thenReturn(accountBuilder);
        Account modifiedAccount = mock(Account.class);
        when(accountBuilder.build()).thenReturn(modifiedAccount);
        return modifiedAccount;
    }

    private void setupToken() {
        when(tokenAccessor.get(tokenAddress)).thenReturn(Optional.of(token));
        Id tokenId = mock(Id.class);
        when(token.getId()).thenReturn(tokenId);
        when(tokenId.asEvmAddress()).thenReturn(tokenAddress);
    }
}
