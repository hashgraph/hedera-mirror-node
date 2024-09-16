/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.service;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAirdropServiceTest extends RestJavaIntegrationTest {

    private final TokenAirdropService service;
    private static final EntityId RECEIVER = EntityId.of(1000L);
    private static final EntityId SENDER = EntityId.of(1001L);
    private static final EntityId TOKEN_ID = EntityId.of(5000L);

    @Test
    void getOutstanding() {
        var fungibleAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.amount(100L)
                        .receiverAccountId(RECEIVER.getId())
                        .senderAccountId(SENDER.getId())
                        .tokenId(TOKEN_ID.getId()))
                .persist();

        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(SENDER))
                .build();
        var response = service.getOutstandingAirdrops(request);
        assertThat(response).containsExactly(fungibleAirdrop);
    }

    @Test
    void getOutstandingByAlias() {
        var entity = domainBuilder.entity().persist();
        var tokenAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(entity.getId()))
                .persist();
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdAliasParameter(entity.getShard(), entity.getRealm(), entity.getAlias()))
                .build();
        var response = service.getOutstandingAirdrops(request);
        assertThat(response).containsExactly(tokenAirdrop);
    }

    @Test
    void getOutstandingByEvmAddress() {
        var entity = domainBuilder.entity().persist();
        var tokenAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(entity.getId()))
                .persist();
        var request = TokenAirdropRequest.builder()
                .accountId(
                        new EntityIdEvmAddressParameter(entity.getShard(), entity.getRealm(), entity.getEvmAddress()))
                .build();
        var response = service.getOutstandingAirdrops(request);
        assertThat(response).containsExactly(tokenAirdrop);
    }

    @Test
    void getOutstandingNotFound() {
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(SENDER))
                .build();
        var response = service.getOutstandingAirdrops(request);
        assertThat(response).isEmpty();
    }
}
