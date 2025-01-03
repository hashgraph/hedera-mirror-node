/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.repository;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.service.Bound;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
class JooqRepositoryTest extends RestJavaIntegrationTest {

    private final TokenAirdropRepository repository;

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyBounds(List<Bound> bounds) {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getReceiverAccountId());
        var request = mock(TokenAirdropRequest.class);
        when(request.getAccountId()).thenReturn(new EntityIdNumParameter(entityId));
        when(request.getBounds()).thenReturn(bounds);
        when(request.getLimit()).thenReturn(1);
        when(request.getOrder()).thenReturn(Direction.ASC);
        when(request.getType()).thenReturn(PENDING);

        assertThat(repository.findAll(request, entityId)).contains(tokenAirdrop);
    }
}
