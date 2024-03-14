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

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.Filter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.exception.InvalidFilterException;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
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
class NftAllowanceRepositoryCustomImpl implements NftAllowanceRepositoryCustom {

    private static final Map<OrderSpec, List<SortField<?>>> SORT_ORDERS = Map.of(
            new OrderSpec(true, Direction.ASC), List.of(NFT_ALLOWANCE.SPENDER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(true, Direction.DESC), List.of(NFT_ALLOWANCE.SPENDER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()),
            new OrderSpec(false, Direction.ASC), List.of(NFT_ALLOWANCE.OWNER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(false, Direction.DESC), List.of(NFT_ALLOWANCE.OWNER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()));

    private final DSLContext dslContext;

    @NotNull
    @Override
    public Collection<NftAllowance> findAll(
            boolean byOwner, @NotNull List<Filter<?>> filters, int limit, @NotNull Direction order) {
        Filter<?> approvedForAllFilter = null;
        Condition commonCondition = null;
        var primaryField = byOwner ? NFT_ALLOWANCE.OWNER : NFT_ALLOWANCE.SPENDER;
        var primarySortField = byOwner ? NFT_ALLOWANCE.SPENDER : NFT_ALLOWANCE.OWNER;
        Filter<?> primarySortFilter = null;
        Filter<?> tokenFilter = null;

        for (var filter : filters) {
            var field = filter.field();
            if (field == NFT_ALLOWANCE.APPROVED_FOR_ALL) {
                approvedForAllFilter = filter;
            } else if (field == primaryField) {
                commonCondition = makeCondition(filter);
            } else if (field == primarySortField) {
                primarySortFilter = filter;
            } else if (field == NFT_ALLOWANCE.TOKEN_ID) {
                tokenFilter = filter;
            }
        }

        if (commonCondition == null) {
            throw new InvalidFilterException("Primary filter not found");
        }

        if (tokenFilter != null && primarySortFilter == null) {
            throw new InvalidFilterException(
                    "Token filter exists without primary sort column (owner or spender) filter");
        }

        if (approvedForAllFilter != null) {
            commonCondition = commonCondition.and(makeCondition(approvedForAllFilter));
        }

        var baseCondition = getBaseCondition(commonCondition, primarySortFilter, tokenFilter);
        var secondaryCondition = getSecondaryCondition(commonCondition, primarySortFilter, tokenFilter);
        var condition = secondaryCondition != null ? baseCondition.or(secondaryCondition) : baseCondition;

        return dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(condition)
                .orderBy(SORT_ORDERS.get(new OrderSpec(byOwner, order)))
                .limit(limit)
                .fetchInto(NftAllowance.class);
    }

    private Condition getBaseCondition(Condition commonCondition, Filter<?> primarySortFilter, Filter<?> tokenFilter) {
        if (primarySortFilter == null) {
            return commonCondition;
        }

        if (tokenFilter == null) {
            return commonCondition.and(makeCondition(primarySortFilter));
        }

        if (primarySortFilter.operator() == RangeOperator.EQ) {
            return commonCondition.and(makeCondition(primarySortFilter)).and(makeCondition(tokenFilter));
        }

        // Create a filter for the primary sort field with EQ operator
        var value = (Long) primarySortFilter.value();
        if (primarySortFilter.operator() == RangeOperator.GT) {
            value += 1L;
        } else if (primarySortFilter.operator() == RangeOperator.LT) {
            value -= 1L;
        }

        var filter = new Filter<>(primarySortFilter.field(), RangeOperator.EQ, value, Long.class);
        return commonCondition.and(makeCondition(filter)).and(makeCondition(tokenFilter));
    }

    private Condition getSecondaryCondition(
            Condition commonCondition, Filter<?> primarySortFilter, Filter<?> tokenFilter) {
        // No secondary condition if there is no token filter, or the primary sort filter's operator is EQ. Note that
        // it's guaranteed that there must be a primary sort filter when token filter exists
        if (tokenFilter == null || primarySortFilter.operator() == RangeOperator.EQ) {
            return null;
        }

        var value = (Long) primarySortFilter.value();
        var operator = primarySortFilter.operator();
        if (operator == RangeOperator.GT || operator == RangeOperator.GTE) {
            value += 1L;
        } else if (operator == RangeOperator.LT || operator == RangeOperator.LTE) {
            value -= 1L;
        }

        var filter = new Filter<>(primarySortFilter.field(), operator, value, Long.class);
        return commonCondition.and(makeCondition(filter));
    }

    @SuppressWarnings({"java:S1905", "rawtypes", "unchecked"})
    private static Condition makeCondition(Filter<?> filter) {
        var field = (Field) filter.field();
        var value = filter.value();
        return switch (filter.operator()) {
            case EQ -> field.eq(value);
            case GT -> field.gt(value);
            case GTE -> field.ge(value);
            case LT -> field.lt(value);
            case LTE -> field.le(value);
            case NE -> field.ne(value);
        };
    }

    private record OrderSpec(boolean byOwner, Direction direction) {}
}
