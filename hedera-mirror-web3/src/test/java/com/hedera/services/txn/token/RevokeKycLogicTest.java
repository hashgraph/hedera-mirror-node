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

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RevokeKycLogicTest {

    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID tokenID = IdUtils.asToken("1.2.3");
    private final TokenRelationshipKey tokenRelationshipKey =
            new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);

    @Mock
    private Store store;

    @Mock
    private TokenRelationship tokenRelationship;

    private RevokeKycLogic revokeKycLogic;
    private TransactionBody tokenRevokeKycBody;

    @BeforeEach
    void setUp() {
        revokeKycLogic = new RevokeKycLogic();
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        // and:
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(tokenRelationship);

        // when:
        revokeKycLogic.revokeKyc(idOfToken, idOfAccount, store);

        // then:
        verify(tokenRelationship).setKycGranted(false);
        verify(store).updateTokenRelationship(tokenRelationship);
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, revokeKycLogic.validate(tokenRevokeKycBody));
    }

    @Test
    void rejectMissingToken() {
        givenMissingTokenTxnCtx();

        // expect:
        assertEquals(INVALID_TOKEN_ID, revokeKycLogic.validate(tokenRevokeKycBody));
    }

    @Test
    void rejectMissingAccount() {
        givenMissingAccountTxnCtx();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, revokeKycLogic.validate(tokenRevokeKycBody));
    }

    private void givenValidTxnCtx() {
        buildTxnContext(accountID, tokenID);
    }

    private void givenMissingTokenTxnCtx() {
        tokenRevokeKycBody = TransactionBody.newBuilder()
                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder()
                        .setAccount(accountID))
                .build();
    }

    private void givenMissingAccountTxnCtx() {
        tokenRevokeKycBody = TransactionBody.newBuilder()
                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder()
                        .setToken(tokenID))
                .build();
    }

    private void buildTxnContext(AccountID accountID, TokenID tokenID) {
        tokenRevokeKycBody = TransactionBody.newBuilder()
                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder()
                        .setAccount(accountID)
                        .setToken(tokenID))
                .build();
    }

}
