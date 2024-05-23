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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import com.hedera.mirror.restjava.jooq.domain.tables.records.NftAllowanceRecord;
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
import org.jooq.TableField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class NftAllowanceRepositoryCustomImpl implements NftAllowanceRepositoryCustom {

    private static final Map<OrderSpec, List<SortField<?>>> SORT_ORDERS = Map.of(
            new OrderSpec(true, Direction.ASC), List.of(NFT_ALLOWANCE.SPENDER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(true, Direction.DESC), List.of(NFT_ALLOWANCE.SPENDER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()),
            new OrderSpec(false, Direction.ASC), List.of(NFT_ALLOWANCE.OWNER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(false, Direction.DESC), List.of(NFT_ALLOWANCE.OWNER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()));
    private static final Condition APPROVAL_CONDITION = NFT_ALLOWANCE.APPROVED_FOR_ALL.isTrue();

    private final DSLContext dslContext;

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public Collection<NftAllowance> findAll(NftAllowanceRequest request, EntityId accountId) {
        boolean byOwner = request.isOwner();
        int limit = request.getLimit();
        var order = request.getOrder();

        var primaryField = byOwner ? NFT_ALLOWANCE.OWNER : NFT_ALLOWANCE.SPENDER;
        var primarySortField = byOwner ? NFT_ALLOWANCE.SPENDER : NFT_ALLOWANCE.OWNER;

        var primaryBounds = request.getOwnerOrSpenderIds();
        var lowerPrimary = primaryBounds.getLower();
        var upperPrimary = primaryBounds.getUpper();

        // This is for range shortcutting when the bounds are the same.
        if (primaryBounds.hasEqualBounds()) {
            lowerPrimary = new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(primaryBounds.adjustLowerBound()));
            upperPrimary = null;
        }

        var tokenBounds = request.getTokenIds();

        var commonCondition = getCondition(primaryField, RangeOperator.EQ, accountId.getId());
        var lowerCondition = getOuterBoundCondition(lowerPrimary, tokenBounds.getLower(), primarySortField);
        var middleCondition = getMiddleCondition(lowerPrimary, tokenBounds.getLower(), primarySortField)
                .and(getMiddleCondition(upperPrimary, tokenBounds.getUpper(), primarySortField));
        var upperCondition = getOuterBoundCondition(upperPrimary, tokenBounds.getUpper(), primarySortField);
        var condition = commonCondition
                .and(lowerCondition.or(middleCondition).or(upperCondition))
                .and(APPROVAL_CONDITION);

        return dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(condition)
                .orderBy(SORT_ORDERS.get(new OrderSpec(byOwner, order)))
                .limit(limit)
                .fetchInto(NftAllowance.class);
    }

    private Condition getOuterBoundCondition(
            EntityIdRangeParameter primarySortParam,
            EntityIdRangeParameter tokenParam,
            TableField<NftAllowanceRecord, Long> primarySortField) {

        if (primarySortParam == null) {
            return noCondition();
        }

        var primaryCondition = getCondition(
                primarySortField,
                primarySortParam.operator(),
                primarySortParam.value().getId());

        if (tokenParam == null) {
            return primaryCondition;
        }

        var tokenCondition = getCondition(
                NFT_ALLOWANCE.TOKEN_ID,
                tokenParam.operator(),
                tokenParam.value().getId());

        // There can only be one query param with operator eq for primary sort param
        if (primarySortParam.operator() == RangeOperator.EQ) {
            return primaryCondition.and(tokenCondition);
        }

        // Get the condition for primary field with EQ operator
        long value = primarySortParam.value().getId();
        if (primarySortParam.operator() == RangeOperator.GT) {
            value += 1L;
        } else if (primarySortParam.operator() == RangeOperator.LT) {
            value -= 1L;
        }

        var equalCondition = getCondition(primarySortField, RangeOperator.EQ, value);
        return equalCondition.and(tokenCondition);
    }

    private Condition getMiddleCondition(
            EntityIdRangeParameter primarySortParam,
            EntityIdRangeParameter tokenParam,
            TableField<NftAllowanceRecord, Long> primarySortField) {

        // No middle condition if there is no primary parameter bound or no token parameter, or the primary sort
        // parameter's operator is EQ. Note that it's guaranteed that there must be
        // a primary sort parameter when token parameter exists except in case of optimized Range

        if (primarySortParam == null || tokenParam == null || primarySortParam.operator() == RangeOperator.EQ) {
            return noCondition();
        }

        long value = primarySortParam.value().getId();
        var operator = primarySortParam.operator();

        if (operator == RangeOperator.GT || operator == RangeOperator.GTE) {
            value += 1L;
        } else if (operator == RangeOperator.LT || operator == RangeOperator.LTE) {
            value -= 1L;
        }
        return getCondition(primarySortField, operator, value);
    }

    private record OrderSpec(boolean byOwner, Direction direction) {}

    public Condition getCondition(Field<Long> field, RangeOperator operator, Long value) {
        return operator.getFunction().apply(field, value);
    }
}
