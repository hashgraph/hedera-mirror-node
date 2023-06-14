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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
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
        associateLogic = new AssociateLogic(mirrorNodeEvmProperties);
    }

    @Test
    void throwErrorWhenAccountNotFound() {
        when(store.getAccount(accountAddress, OnMissing.THROW))
                .thenThrow(getException(Account.class.getName(), accountAddress));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "detail",
                        String.format(
                                "Entity of type %s with id %s is missing", Account.class.getName(), accountAddress));
    }

    @Test
    void throwErrorWhenTokenNotFound() {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(store.getFungibleToken(tokenAddress, OnMissing.THROW))
                .thenThrow(getException(Token.class.getName(), tokenAddress));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "detail",
                        String.format("Entity of type %s with id %s is missing", Token.class.getName(), tokenAddress));
    }

    @Test
    void failsOnCrossingAssociationLimit() {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(account.getNumAssociations()).thenReturn(5);
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(3);

        // expect:
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED.name());
    }

    @Test
    void failsOnAssociatingWithAlreadyRelatedToken() {
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(1000);
        setupAccount(1);
        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, OnMissing.DONT_THROW))
                .thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);
        when(accountId.num()).thenReturn(1L);

        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT.name());
    }

    @Test
    void canAssociateWithNewToken() {
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(1000);
        final var modifiedAccount = setupAccount(1);
        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, OnMissing.DONT_THROW))
                .thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);
        associateLogic.associate(accountAddress, tokenAddresses, store);

        verify(store).updateTokenRelationship(new TokenRelationship(token, modifiedAccount));
    }

    @Test
    void updatesAccount() {
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(1000);
        final var modifiedAccount = setupAccount(6);
        when(account.getNumAssociations()).thenReturn(5);

        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.getTokenRelationship(tokenRelationShipKey, OnMissing.DONT_THROW))
                .thenReturn(tokenRelationship);
        when(tokenRelationship.getAccount()).thenReturn(account);

        associateLogic.associate(accountAddress, tokenAddresses, store);

        verify(store).updateAccount(modifiedAccount);
        verify(account).setNumAssociations(6);
    }

    private Account setupAccount(int numOfAssociations) {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(account.getAccountAddress()).thenReturn(accountAddress);
        when(account.getId()).thenReturn(accountId);
        Account modifiedAccount = mock(Account.class);
        when(account.setNumAssociations(numOfAssociations)).thenReturn(modifiedAccount);
        return modifiedAccount;
    }

    private void setupToken() {
        when(store.getFungibleToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        Id tokenId = mock(Id.class);
        when(token.getId()).thenReturn(tokenId);
        when(tokenId.asEvmAddress()).thenReturn(tokenAddress);
    }

    private InvalidTransactionException getException(final String type, Object id) {
        return new InvalidTransactionException(
                FAIL_INVALID, String.format("Entity of type %s with id %s is missing", type, id), "");
    }
}
