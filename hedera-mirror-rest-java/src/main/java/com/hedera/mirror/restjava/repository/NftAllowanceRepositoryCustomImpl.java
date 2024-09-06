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

import static com.hedera.mirror.restjava.common.RangeOperator.EQ;
import static com.hedera.mirror.restjava.common.RangeOperator.GT;
import static com.hedera.mirror.restjava.common.RangeOperator.LT;
import static com.hedera.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;
import static org.jooq.impl.DSL.noCondition;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import com.hedera.mirror.restjava.service.Bound;
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

    private static final Condition APPROVAL_CONDITION = NFT_ALLOWANCE.APPROVED_FOR_ALL.isTrue();
    private static final Map<OrderSpec, List<SortField<?>>> SORT_ORDERS = Map.of(
            new OrderSpec(true, Direction.ASC), List.of(NFT_ALLOWANCE.SPENDER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(true, Direction.DESC), List.of(NFT_ALLOWANCE.SPENDER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()),
            new OrderSpec(false, Direction.ASC), List.of(NFT_ALLOWANCE.OWNER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(false, Direction.DESC), List.of(NFT_ALLOWANCE.OWNER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()));

    private final DSLContext dslContext;

    @NotNull
    @Override
    public Collection<NftAllowance> findAll(NftAllowanceRequest request, EntityId accountId) {
        boolean byOwner = request.isOwner();
        var condition = getBaseCondition(accountId, byOwner)
                .and(getBoundCondition(byOwner, request.getOwnerOrSpenderIds(), request.getTokenIds()));
        return dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(condition)
                .orderBy(SORT_ORDERS.get(new OrderSpec(byOwner, request.getOrder())))
                .limit(request.getLimit())
                .fetchInto(NftAllowance.class);
    }

    private Condition getBaseCondition(EntityId accountId, boolean byOwner) {
        return getCondition(byOwner ? NFT_ALLOWANCE.OWNER : NFT_ALLOWANCE.SPENDER, EQ, accountId.getId())
                .and(APPROVAL_CONDITION);
    }

    private Condition getBoundCondition(boolean byOwner, Bound primaryBound, Bound tokenBound) {
        var primaryField = byOwner ? NFT_ALLOWANCE.SPENDER : NFT_ALLOWANCE.OWNER;
        var primaryLower = primaryBound.getLower();
        var primaryUpper = primaryBound.getUpper();
        var tokenLower = tokenBound.getLower();
        var tokenUpper = tokenBound.getUpper();

        // If the primary param has a range with a single value, rewrite it to EQ
        if (primaryBound.hasEqualBounds()) {
            primaryLower = new EntityIdRangeParameter(EQ, EntityId.of(primaryBound.adjustLowerBound()));
            primaryUpper = null;
        }

        // If the token param operator is EQ, set the token upper bound to the same
        if (tokenLower != null && tokenLower.operator() == EQ) {
            tokenUpper = tokenLower;
        }

        var lowerCondition = getOuterBoundCondition(primaryLower, tokenLower, primaryField);
        var middleCondition = getMiddleCondition(primaryLower, tokenLower, primaryField)
                .and(getMiddleCondition(primaryUpper, tokenUpper, primaryField));
        var upperCondition = getOuterBoundCondition(primaryUpper, tokenUpper, primaryField);

        return lowerCondition.or(middleCondition).or(upperCondition);
    }

    private Condition getOuterBoundCondition(
            EntityIdRangeParameter primaryParam, EntityIdRangeParameter tokenParam, Field<Long> primaryField) {
        // No outer bound condition if there is no primary parameter, or the operator is EQ. For EQ, everything should
        // go into the middle condition
        if (primaryParam == null || primaryParam.operator() == EQ) {
            return noCondition();
        }

        // If the token param operator is EQ, there should only have the middle condition
        if (tokenParam != null && tokenParam.operator() == EQ) {
            return noCondition();
        }

        long value = primaryParam.value().getId();
        if (primaryParam.operator() == GT) {
            value += 1L;
        } else if (primaryParam.operator() == LT) {
            value -= 1L;
        }

        return getCondition(primaryField, EQ, value).and(getCondition(NFT_ALLOWANCE.TOKEN_ID, tokenParam));
    }

    private Condition getMiddleCondition(
            EntityIdRangeParameter primaryParam, EntityIdRangeParameter tokenParam, Field<Long> primaryField) {
        if (primaryParam == null) {
            return noCondition();
        }

        // When the primary param operator is EQ, or the token param operator is EQ, don't adjust the value for the
        // primary param.
        if (primaryParam.operator() == EQ || (tokenParam != null && tokenParam.operator() == EQ)) {
            return getCondition(primaryField, primaryParam).and(getCondition(NFT_ALLOWANCE.TOKEN_ID, tokenParam));
        }

        long value = primaryParam.value().getId();
        value += primaryParam.hasLowerBound() ? 1L : -1L;
        return getCondition(primaryField, primaryParam.operator(), value);
    }

    private record OrderSpec(boolean byOwner, Direction direction) {}
}
