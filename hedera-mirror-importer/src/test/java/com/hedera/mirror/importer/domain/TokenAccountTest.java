package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

class TokenAccountTest {

    private final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityType.TOKEN);
    private final EntityId ACCOUNT_ID = EntityId.of("0.0.102", EntityType.ACCOUNT);

    @Test
    void createValidTokenAccount() {
        TokenAccount tokenAccount = tokenAccount(FOO_COIN_ID, ACCOUNT_ID);
        assertAll(
                () -> assertNotEquals(0, tokenAccount.getCreatedTimestamp()),
                () -> assertEquals(TokenFreezeStatusEnum.NOT_APPLICABLE, tokenAccount.getFreezeStatus()),
                () -> assertEquals(TokenKycStatusEnum.NOT_APPLICABLE, tokenAccount.getKycStatus()),
                () -> assertNotEquals(0, tokenAccount.getId().getModifiedTimestamp())
        );
    }

    private TokenAccount tokenAccount(EntityId tokenId, EntityId accountId) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, 2L);
        tokenAccount.setAssociated(false);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setCreatedTimestamp(1L);
        return tokenAccount;
    }
}
