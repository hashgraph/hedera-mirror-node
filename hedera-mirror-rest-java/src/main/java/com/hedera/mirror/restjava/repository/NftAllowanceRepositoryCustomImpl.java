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

import static com.hedera.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;
import static org.jooq.impl.DSL.noCondition;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.Filter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.service.NftAllowanceRequest;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class NftAllowanceRepositoryCustomImpl implements NftAllowanceRepositoryCustom {

    private static final Map<OrderSpec, List<SortField<?>>> SORT_ORDERS = Map.of(
            new OrderSpec(true, Direction.ASC), List.of(NFT_ALLOWANCE.SPENDER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(true, Direction.DESC), List.of(NFT_ALLOWANCE.SPENDER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()),
            new OrderSpec(false, Direction.ASC), List.of(NFT_ALLOWANCE.OWNER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(false, Direction.DESC), List.of(NFT_ALLOWANCE.OWNER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()));

    private final DSLContext dslContext;

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public Collection<NftAllowance> findAll(NftAllowanceRequest request) {
        boolean byOwner = request.isOwner();
        int limit = request.getLimit();
        Direction order = request.getOrder();

        var primaryField = byOwner ? NFT_ALLOWANCE.OWNER : NFT_ALLOWANCE.SPENDER;
        var primarySortField = byOwner ? NFT_ALLOWANCE.SPENDER : NFT_ALLOWANCE.OWNER;

        var accountId = request.getAccountId();
        var primarySortParam = request.getOwnerOrSpenderId();
        var tokenParam = request.getTokenId();

        var primaryFilter =
                new Filter<>(primaryField, RangeOperator.EQ, accountId.value().getId());
        var primarySortFilter = new Filter<>(
                primarySortField,
                primarySortParam.operator(),
                primarySortParam.value().getId());
        var tokenFilter = new Filter<>(
                NFT_ALLOWANCE.TOKEN_ID,
                tokenParam.operator(),
                tokenParam.value().getId());

        Condition commonCondition = primaryFilter.getCondition();
        var baseCondition = getBaseCondition(primarySortFilter, tokenFilter);
        var secondaryCondition = getSecondaryCondition(primarySortFilter, tokenFilter);
        var condition = commonCondition.and(baseCondition.or(secondaryCondition));

        return dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(condition)
                .orderBy(SORT_ORDERS.get(new OrderSpec(byOwner, order)))
                .limit(limit)
                .fetchInto(NftAllowance.class);
    }

    private Condition getBaseCondition(Filter<Long> primarySortFilter, Filter<Long> tokenFilter) {
        if (primarySortFilter == null) {
            return noCondition();
        }

        if (tokenFilter == null) {
            return primarySortFilter.getCondition();
        }

        if (primarySortFilter.operator() == RangeOperator.EQ) {
            return primarySortFilter.getCondition().and(tokenFilter.getCondition());
        }

        // Create a filter for the primary sort field with EQ operator
        long value = primarySortFilter.value();
        if (primarySortFilter.operator() == RangeOperator.GT) {
            value += 1L;
        } else if (primarySortFilter.operator() == RangeOperator.LT) {
            value -= 1L;
        }

        var filter = new Filter<>(primarySortFilter.field(), RangeOperator.EQ, value);
        return filter.getCondition().and(tokenFilter.getCondition());
    }

    private Condition getSecondaryCondition(Filter<Long> primarySortFilter, Filter<Long> tokenFilter) {
        // No secondary condition if there is no token filter, or the primary sort filter's operator is EQ. Note that
        // it's guaranteed that there must be a primary sort filter when token filter exists
        if (tokenFilter == null || primarySortFilter.operator() == RangeOperator.EQ) {
            return noCondition();
        }

        long value = primarySortFilter.value();
        var operator = primarySortFilter.operator();
        if (operator == RangeOperator.GT || operator == RangeOperator.GTE) {
            value += 1L;
        } else if (operator == RangeOperator.LT || operator == RangeOperator.LTE) {
            value -= 1L;
        }

        var filter = new Filter<>(primarySortFilter.field(), operator, value);
        return filter.getCondition();
    }

    private record OrderSpec(boolean byOwner, Direction direction) {}
}
