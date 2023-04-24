/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.token;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import org.junit.jupiter.api.Test;

class TokenAccountTest {

    private final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityType.TOKEN);
    private final EntityId ACCOUNT_ID = EntityId.of("0.0.102", EntityType.ACCOUNT);

    @Test
    void createValidTokenAccount() {
        var tokenAccount = TokenAccount.builder()
                .accountId(ACCOUNT_ID.getId())
                .associated(false)
                .createdTimestamp(1L)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .tokenId(FOO_COIN_ID.getId())
                .build();

        assertAll(
                () -> assertNotEquals(0, tokenAccount.getCreatedTimestamp()),
                () -> assertEquals(TokenFreezeStatusEnum.NOT_APPLICABLE, tokenAccount.getFreezeStatus()),
                () -> assertEquals(TokenKycStatusEnum.NOT_APPLICABLE, tokenAccount.getKycStatus()));
    }
}
