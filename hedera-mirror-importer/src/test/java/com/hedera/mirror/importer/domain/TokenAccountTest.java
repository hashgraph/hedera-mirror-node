package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TokenAccountTest {

    private final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityTypeEnum.TOKEN);
    private final EntityId ACCOUNT_ID = EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT);

    @Test
    void createValidTokenAccount() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        assertAll(
                () -> assertNotEquals(0, tokenAccount.getCreatedTimestamp()),
                () -> assertEquals(TokenFreezeStatusEnum.NOTAPPLICABLE, tokenAccount.getFreezeStatus()),
                () -> assertEquals(TokenKycStatusEnum.NOTAPPLICABLE, tokenAccount.getKycStatus()),
                () -> assertNotEquals(0, tokenAccount.getModifiedTimestamp())
        );
    }

    @Test
    void toggleAssociatedStatus() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        assertEquals(false, tokenAccount.isAssociated());
        tokenAccount.toggleAssociatedStatus();
        assertEquals(true, tokenAccount.isAssociated());
    }

    @Test
    void toggleFreezeStatusWhenNotApplicable() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        tokenAccount.toggleFreezeStatus();
        assertEquals(TokenFreezeStatusEnum.NOTAPPLICABLE, tokenAccount.getFreezeStatus());
    }

    @Test
    void toggleFreezeStatusWhenApplicable() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        tokenAccount.toggleFreezeStatus();
        assertEquals(TokenFreezeStatusEnum.UNFROZEN, tokenAccount.getFreezeStatus());
        tokenAccount.toggleFreezeStatus();
        assertEquals(TokenFreezeStatusEnum.FROZEN, tokenAccount.getFreezeStatus());
    }

    @Test
    void toggleKycStatusWhenNotApplicable() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        tokenAccount.toggleKycStatus();
        assertEquals(TokenKycStatusEnum.NOTAPPLICABLE, tokenAccount.getKycStatus());
    }

    @Test
    void toggleKycStatusWhenApplicable() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        tokenAccount.toggleKycStatus();
        assertEquals(TokenKycStatusEnum.GRANTED, tokenAccount.getKycStatus());
        tokenAccount.toggleKycStatus();
        assertEquals(TokenKycStatusEnum.REVOKED, tokenAccount.getKycStatus());
    }

    private TokenAccount tokenAccount(EntityId tokenId, EntityId accountId) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId);
        tokenAccount.setAssociated(false);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOTAPPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOTAPPLICABLE);
        tokenAccount.setCreatedTimestamp(1L);
        tokenAccount.setModifiedTimestamp(2L);
        return tokenAccount;
    }
}
