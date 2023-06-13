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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssociateLogicTest {

    private final Address accountAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final Address tokenAddress = Address.fromHexString("0x0000000000000000000000000000000000000182");
    private final List<Address> tokenAddresses = List.of(tokenAddress);

    @Mock
    Account.AccountBuilder accountBuilder;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private StoreImpl store;

    @Mock
    private Account account;

    @Mock
    private Id accountId;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private Token token;

    private AssociateLogic associateLogic;

    @BeforeEach
    public void setUp() {
        associateLogic = new AssociateLogic(store, mirrorNodeEvmProperties);
    }

    @Test
    void throwErrorWhenAccountNotFound() {
        when(store.getAccount(accountAddress, true)).thenThrow(getException(Account.class.getName(), accountAddress));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "detail",
                        String.format(
                                "Entity of type %s with id %s is missing", Account.class.getName(), accountAddress));
    }

    @Test
    void throwErrorWhenTokenNotFound() {
        when(store.getAccount(accountAddress, true)).thenReturn(account);
        when(store.getToken(tokenAddress, true)).thenThrow(getException(Token.class.getName(), tokenAddress));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "detail",
                        String.format("Entity of type %s with id %s is missing", Token.class.getName(), tokenAddress));
    }

    @Test
    void failsOnCrossingAssociationLimit() {
        when(store.getAccount(accountAddress, true)).thenReturn(account);
        when(account.getNumAssociations()).thenReturn(5);
        when(mirrorNodeEvmProperties.isTokenAssociationsLimited()).thenReturn(true);
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(3);

        // expect:
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED.name());
    }

    @Test
    void failsOnAssociatingWithAlreadyRelatedToken() {
        setupAccount();
        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, false)).thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);
        when(accountId.num()).thenReturn(1L);

        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT.name());
    }

    @Test
    void canAssociateWithNewToken() {
        final var modifiedAccount = setupAccount();
        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, false)).thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);
        associateLogic.associate(accountAddress, tokenAddresses);

        verify(store).updateTokenRelationship(new TokenRelationship(token, modifiedAccount));
    }

    @Test
    void updatesAccount() {
        final var modifiedAccount = setupAccount();
        when(account.getNumAssociations()).thenReturn(5);

        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, false)).thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);

        associateLogic.associate(accountAddress, tokenAddresses);

        verify(store).updateAccount(modifiedAccount);
        verify(accountBuilder).numAssociations(6);
    }

    @Test
    void commitsOnSuccess() {
        setupAccount();
        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, false)).thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);

        associateLogic.associate(accountAddress, tokenAddresses);

        verify(store).commit();
    }

    private Account setupAccount() {
        when(store.getAccount(accountAddress, true)).thenReturn(account);
        when(account.getAccountAddress()).thenReturn(accountAddress);
        when(account.getId()).thenReturn(accountId);
        when(account.toBuilder()).thenReturn(accountBuilder);
        when(accountBuilder.numAssociations(anyInt())).thenReturn(accountBuilder);
        Account modifiedAccount = mock(Account.class);
        when(accountBuilder.build()).thenReturn(modifiedAccount);
        return modifiedAccount;
    }

    private void setupToken() {
        when(store.getToken(tokenAddress, true)).thenReturn(token);
        Id tokenId = mock(Id.class);
        when(token.getId()).thenReturn(tokenId);
        when(tokenId.asEvmAddress()).thenReturn(tokenAddress);
    }

    private InvalidTransactionException getException(final String type, Object id) {
        return new InvalidTransactionException(
                FAIL_INVALID, String.format("Entity of type %s with id %s is missing", type, id), "");
    }
}
