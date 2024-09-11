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

package com.hedera.mirror.restjava.repository;

import static com.hedera.mirror.restjava.jooq.domain.Tables.TOKEN_AIRDROP;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.jooq.domain.enums.AirdropState;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class TokenAirdropRepositoryCustomImpl implements TokenAirdropRepositoryCustom {

    private final DSLContext dslContext;
    private static final Map<Direction, List<SortField<?>>> OUTSTANDING_SORT_ORDERS = Map.of(
            Direction.ASC, List.of(TOKEN_AIRDROP.RECEIVER_ID.asc(), TOKEN_AIRDROP.TOKEN_ID.asc()),
            Direction.DESC, List.of(TOKEN_AIRDROP.RECEIVER_ID.desc(), TOKEN_AIRDROP.TOKEN_ID.desc()));

    @Override
    public Collection<TokenAirdrop> findAllOutstanding(TokenAirdropRequest request, EntityId accountId) {
        var condition = TOKEN_AIRDROP
                .SENDER_ID
                .eq(accountId.getId())
                .and(TOKEN_AIRDROP.STATE.eq(AirdropState.PENDING))
                .and(getCondition(TOKEN_AIRDROP.RECEIVER_ID, request.getEntityId()))
                .and(getCondition(TOKEN_AIRDROP.TOKEN_ID, request.getTokenId()));

        var order = OUTSTANDING_SORT_ORDERS.get(request.getOrder());
        return dslContext
                .selectFrom(TOKEN_AIRDROP)
                .where(condition)
                .orderBy(order)
                .limit(request.getLimit())
                .fetchInto(TokenAirdrop.class);
    }
}
