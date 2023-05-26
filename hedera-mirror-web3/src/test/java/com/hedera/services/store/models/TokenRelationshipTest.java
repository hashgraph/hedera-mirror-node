/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.models;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRelationshipTest {
    private final Id tokenId = new Id(0, 0, 1234);
    private final Id accountId = new Id(1, 0, 4321);
    private final long balance = 1_234L;
    private final JKey kycKey = asFcKeyUnchecked(
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
    private final JKey freezeKey = asFcKeyUnchecked(
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
    private final long defaultLongValue = 0;
    private final int defaultIntValue = 0;
    private Token token;
    private Account account;

    private TokenRelationship subject;

    @BeforeEach
    void setUp() {
        token = new Token(
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.FINITE,
                defaultIntValue,
                21_000_000,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                false,
                defaultIntValue,
                true,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                defaultLongValue,
                defaultLongValue,
                Collections.emptyList());

        account = new Account(
                accountId,
                defaultLongValue,
                defaultLongValue,
                false,
                defaultLongValue,
                defaultLongValue,
                Id.DEFAULT,
                defaultIntValue,
                null,
                null,
                null,
                3,
                defaultIntValue,
                defaultIntValue,
                0L);

        subject = new TokenRelationship(token, account, balance, false, false, false, true, true, 0);
    }

    @Test
    void ofRecordInterestIfFungibleBalanceChanges() {
        token.setType(TokenType.FUNGIBLE_COMMON);

        subject = subject.setBalance(balance - 1);
        assertTrue(subject.hasChangesForRecord());
    }

    @Test
    void notOrdinarilyOfRecordInterestIfNonFungibleBalanceChanges() {
        token.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        subject = subject.setBalance(balance - 1);
        assertTrue(subject.hasChangesForRecord());
    }

    @Test
    void ordinarilyOfRecordInterestIfNonFungibleBalanceChangesForDeletedToken() {
        token.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        // token.setIsDeleted(true);

        subject = subject.setBalance(balance - 1);
        assertTrue(subject.hasChangesForRecord());
    }

    @Test
    void toStringAsExpected() {
        // given:
        final var desired = "TokenRelationship{notYetPersisted=true, account=Account{id=1.0.4321,"
                + " expiry=0, balance=0, deleted=false, ownedNfts=0, alreadyUsedAutoAssociations=0, maxAutoAssociations=0,"
                + " alias=, cryptoAllowances=null, fungibleTokenAllowances=null, approveForAllNfts=null, numAssociations=3,"
                + " numPositiveBalances=0}, token=Token{id=0.0.1234, type=FUNGIBLE_COMMON, deleted=false, autoRemoved=false, treasury=null,"
                + " autoRenewAccount=null, kycKey=null, freezeKey=null, frozenByDefault=false, supplyKey=null, currentSerialNumber=0,"
                + " pauseKey=null, paused=false}, balance=1234, balanceChange=0, frozen=false, kycGranted=false, isAutomaticAssociation=true}";

        // expect:
        assertEquals(desired, subject.toString());
    }

    @Test
    void equalsWorks() {
        assertEquals(subject, subject);
        assertNotEquals(subject, freezeKey);
    }

    @Test
    void automaticAssociationSetterWorks() {
        subject = new TokenRelationship(token, account, balance, false, false, false, true, false, 0);
        assertFalse(subject.isAutomaticAssociation());

        subject = new TokenRelationship(token, account, balance, false, false, false, true, true, 0);
        assertTrue(subject.isAutomaticAssociation());
    }

    @Test
    void cannotChangeBalanceIfFrozenForToken() {
        // given:
        token = token.setFreezeKey(freezeKey);
        subject = new TokenRelationship(token, account, balance, true, false, false, true, true, 0);

        assertFailsWith(() -> subject.setBalance(balance + 1), ACCOUNT_FROZEN_FOR_TOKEN);
    }

    @Test
    void canChangeBalanceIfFrozenForDeletedToken() {
        token = token.setFreezeKey(freezeKey);
        token = token.setIsDeleted(true);
        subject = new TokenRelationship(token, account, balance, true, false, false, true, true, 0);

        subject = subject.setBalance(0);
        assertEquals(-balance, subject.getBalanceChange());
    }

    @Test
    void canChangeBalanceIfUnfrozenForToken() {
        // given:
        token = token.setFreezeKey(freezeKey);

        // when:
        subject = subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void canChangeBalanceIfNoFreezeKey() {
        // given:
        subject = new TokenRelationship(token, account, balance, true, false, false, true, true, 0);

        // when:
        subject = subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void cannotChangeBalanceIfKycNotGranted() {
        // given:
        token = token.setKycKey(kycKey);
        subject = new TokenRelationship(token, account, balance, false, false, false, true, true, 0);
        // verify
        assertFailsWith(() -> subject.setBalance(balance + 1), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
    }

    @Test
    void canChangeBalanceIfKycGranted() {
        // given:
        token = token.setKycKey(kycKey);
        subject = new TokenRelationship(token, account, balance, false, true, false, true, true, 0);

        // when:
        subject = subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void canChangeBalanceIfNoKycKey() {

        // when:
        subject = subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void updateFreezeWorksIfFeezeKeyIsPresent() {
        // given:
        token = token.setFreezeKey(freezeKey);

        // when:
        subject = new TokenRelationship(token, account, balance, true, false, false, true, true, 0);

        // then:
        assertTrue(subject.isFrozen());
    }

    @Test
    void givesCorrectRepresentation() {
        Token newToken = subject.getToken().setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject = subject.setToken(newToken);
        assertTrue(subject.hasUniqueRepresentation());

        newToken = subject.getToken().setType(TokenType.FUNGIBLE_COMMON);
        subject = subject.setToken(newToken);
        assertTrue(subject.hasCommonRepresentation());
    }

    @Test
    void testHashCode() {
        var rel = new TokenRelationship(token, account, balance, false, false, false, true, true, 0);
        assertEquals(rel.hashCode(), subject.hashCode());
    }

    @Test
    void updateKycWorksIfKycKeyIsPresent() {
        // given:
        token.setKycKey(kycKey);

        // when:
        subject = new TokenRelationship(token, account, balance, false, true, false, true, true, 0);

        // then:
        assertTrue(subject.isKycGranted());
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
