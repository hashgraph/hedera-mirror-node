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
import static org.jooq.impl.DSL.noCondition;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.restjava.dto.OutstandingTokenAirdropRequest;
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
    private static final Map<OrderSpec, List<SortField<?>>> SORT_ORDERS = Map.of(
            new OrderSpec(true, Direction.ASC),
                    List.of(
                            TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID.asc(),
                            TOKEN_AIRDROP.TOKEN_ID.asc(),
                            TOKEN_AIRDROP.SERIAL_NUMBER.asc()),
            new OrderSpec(true, Direction.DESC),
                    List.of(
                            TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID.desc(),
                            TOKEN_AIRDROP.TOKEN_ID.desc(),
                            TOKEN_AIRDROP.SERIAL_NUMBER.desc()),
            new OrderSpec(false, Direction.ASC),
                    List.of(TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID.asc(), TOKEN_AIRDROP.TOKEN_ID.asc()),
            new OrderSpec(false, Direction.DESC),
                    List.of(TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID.desc(), TOKEN_AIRDROP.TOKEN_ID.desc()));

    @Override
    public Collection<TokenAirdrop> findAllOutstanding(OutstandingTokenAirdropRequest request, EntityId accountId) {
        var serialNumberCondition = getCondition(TOKEN_AIRDROP.SERIAL_NUMBER, request.getSerialNumber());
        var includeSerialNumber = !serialNumberCondition.equals(noCondition());
        if (includeSerialNumber) {
            // If the query includes a serial number, explicitly remove fungible tokens from the result as they have a
            // serial number of 0
            serialNumberCondition = serialNumberCondition.and(TOKEN_AIRDROP.SERIAL_NUMBER.ne(0L));
        }

        var condition = TOKEN_AIRDROP
                .SENDER_ACCOUNT_ID
                .eq(accountId.getId())
                .and(TOKEN_AIRDROP.STATE.eq(AirdropState.PENDING))
                .and(getCondition(TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID, request.getReceiverId()))
                .and(getCondition(TOKEN_AIRDROP.TOKEN_ID, request.getTokenId()))
                .and(serialNumberCondition);

        var order = SORT_ORDERS.get(new OrderSpec(includeSerialNumber, request.getOrder()));
        return dslContext
                .selectFrom(TOKEN_AIRDROP)
                .where(condition)
                .orderBy(order)
                .limit(request.getLimit())
                .fetchInto(TokenAirdrop.class);
    }

    private record OrderSpec(boolean includeSerialNumber, Direction direction) {}
}
