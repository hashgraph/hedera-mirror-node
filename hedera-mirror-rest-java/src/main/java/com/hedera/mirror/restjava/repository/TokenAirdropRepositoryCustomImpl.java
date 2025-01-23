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

import static com.hedera.mirror.restjava.common.RangeOperator.EQ;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static com.hedera.mirror.restjava.jooq.domain.Tables.TOKEN_AIRDROP;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import com.hedera.mirror.restjava.jooq.domain.enums.AirdropState;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class TokenAirdropRepositoryCustomImpl implements TokenAirdropRepositoryCustom {

    private final DSLContext dslContext;
    private static final Map<AirdropRequestType, Map<Direction, List<SortField<?>>>> SORT_ORDERS = Map.of(
            OUTSTANDING,
                    Map.of(
                            Direction.ASC,
                                    List.of(
                                            OUTSTANDING.getPrimaryField().asc(),
                                            TOKEN_AIRDROP.TOKEN_ID.asc(),
                                            TOKEN_AIRDROP.SERIAL_NUMBER.asc()),
                            Direction.DESC,
                                    List.of(
                                            OUTSTANDING.getPrimaryField().desc(),
                                            TOKEN_AIRDROP.TOKEN_ID.desc(),
                                            TOKEN_AIRDROP.SERIAL_NUMBER.desc())),
            PENDING,
                    Map.of(
                            Direction.ASC,
                                    List.of(
                                            PENDING.getPrimaryField().asc(),
                                            TOKEN_AIRDROP.TOKEN_ID.asc(),
                                            TOKEN_AIRDROP.SERIAL_NUMBER.asc()),
                            Direction.DESC,
                                    List.of(
                                            PENDING.getPrimaryField().desc(),
                                            TOKEN_AIRDROP.TOKEN_ID.desc(),
                                            TOKEN_AIRDROP.SERIAL_NUMBER.desc())));

    @Override
    public Collection<TokenAirdrop> findAll(TokenAirdropRequest request, EntityId accountId) {
        var type = request.getType();
        var bounds = request.getBounds();
        var condition = getBaseCondition(accountId, type.getBaseField())
                .and(getBoundConditions(bounds))
                .and(TOKEN_AIRDROP.STATE.eq(AirdropState.PENDING));

        var order = SORT_ORDERS.get(type).get(request.getOrder());
        return dslContext
                .selectFrom(TOKEN_AIRDROP)
                .where(condition)
                .orderBy(order)
                .limit(request.getLimit())
                .fetchInto(TokenAirdrop.class);
    }

    private Condition getBaseCondition(EntityId accountId, Field<Long> baseField) {
        return getCondition(baseField, EQ, accountId.getId());
    }
}
