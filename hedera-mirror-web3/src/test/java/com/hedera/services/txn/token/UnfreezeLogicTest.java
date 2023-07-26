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

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnfreezeLogicTest {

    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);
    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID tokenID = IdUtils.asToken("1.2.3");
    final TokenRelationshipKey tokenRelationshipKey =
            new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));

    @Mock
    private Store store;

    private UnfreezeLogic subject;
    private TransactionBody tokenUnfreezeTxn;

    @BeforeEach
    void setup() {
        subject = new UnfreezeLogic();
    }

    @Test
    void followsHappyPath() {
        // given:
        TokenRelationship tokenRelationship = mock(TokenRelationship.class);
        TokenRelationship modifiedTokenRelationship = mock(TokenRelationship.class);
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(tokenRelationship);
        given(tokenRelationship.setFrozen(false)).willReturn(modifiedTokenRelationship);

        // when:
        subject.unfreeze(idOfToken, idOfAccount, store);

        // then:
        verify(tokenRelationship).setFrozen(false);
        verify(store).updateTokenRelationship(modifiedTokenRelationship);
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.validate(tokenUnfreezeTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.validate(tokenUnfreezeTxn));
    }

    @Test
    void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.validate(tokenUnfreezeTxn));
    }

    private void givenValidTxnCtx() {
        tokenUnfreezeTxn = TransactionBody.newBuilder()
                .setTokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder()
                        .setAccount(accountID)
                        .setToken(tokenID))
                .build();
    }

    private void givenMissingToken() {
        tokenUnfreezeTxn = TransactionBody.newBuilder()
                .setTokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder())
                .build();
    }

    private void givenMissingAccount() {
        tokenUnfreezeTxn = TransactionBody.newBuilder()
                .setTokenUnfreeze(
                        TokenUnfreezeAccountTransactionBody.newBuilder().setToken(tokenID))
                .build();
    }
}
