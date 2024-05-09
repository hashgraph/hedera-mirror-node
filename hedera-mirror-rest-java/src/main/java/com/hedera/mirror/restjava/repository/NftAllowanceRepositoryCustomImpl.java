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
import com.hedera.mirror.restjava.jooq.domain.tables.records.NftAllowanceRecord;
import com.hedera.mirror.restjava.service.NftAllowanceRequest;
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

        var primarySortParam = request.getOwnerOrSpenderId();
        var tokenParam = request.getTokenId();

        Bound primaryBounds = getBounds(primarySortParam);
        Bound tokenBounds = getBounds(tokenParam);

        var commonCondition = getCondition(primaryField, RangeOperator.EQ, accountId.getId());
        var lowerCondition = getOuterBoundCondition(
                primaryBounds.primaryLowerBoundParam(), tokenBounds.primaryLowerBoundParam, primarySortField);
        var middleCondition = getMiddleCondition(
                        primaryBounds.primaryLowerBoundParam, tokenBounds.primaryLowerBoundParam, primarySortField)
                .and(getMiddleCondition(
                        primaryBounds.primaryUpperBoundParam, tokenBounds.primaryUpperBoundParam, primarySortField));
        var upperCondition = getOuterBoundCondition(
                primaryBounds.primaryUpperBoundParam(), tokenBounds.primaryUpperBoundParam, primarySortField);
        var condition = commonCondition.and(lowerCondition.or(middleCondition).or(upperCondition));

        return dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(condition)
                .orderBy(SORT_ORDERS.get(new OrderSpec(byOwner, order)))
                .limit(limit)
                .fetchInto(NftAllowance.class);
    }

    private Bound getBounds(List<EntityIdRangeParameter> primarySortParam) {
        EntityIdRangeParameter lowerBoundParam = null;
        EntityIdRangeParameter upperBoundParam = null;

        if (primarySortParam != null) {
            for (EntityIdRangeParameter param : primarySortParam) {
                // Considering EQ in the same category as GT,GTE as an assumption
                if (param.operator() == RangeOperator.GT
                        || param.operator() == RangeOperator.GTE
                        || param.operator() == RangeOperator.EQ) {
                    upperBoundParam = param;
                } else if (param.operator() == RangeOperator.LT || param.operator() == RangeOperator.LTE) {
                    lowerBoundParam = param;
                }
            }
        }
        return new Bound(lowerBoundParam, upperBoundParam);
    }

    private record Bound(
            EntityIdRangeParameter primaryLowerBoundParam, EntityIdRangeParameter primaryUpperBoundParam) {}

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
            EntityIdRangeParameter primary,
            EntityIdRangeParameter token,
            TableField<NftAllowanceRecord, Long> primarySortField) {

        // No secondary condition if there is no primary parameter bound or no token parameter, or the primary sort
        // parameter's operator is EQ. Note
        // that
        // it's guaranteed that there must be a primary sort parameter when token parameter exists

        if (primary == null || token == null || primary.operator() == RangeOperator.EQ) {
            return noCondition();
        }

        long value = primary.value().getId();
        var operator = primary.operator();

        if (operator == RangeOperator.GT || operator == RangeOperator.GTE) {
            value += 1L;
        }
        return getCondition(primarySortField, operator, value);
    }

    private record OrderSpec(boolean byOwner, Direction direction) {}

    public Condition getCondition(Field<Long> field, RangeOperator operator, Long value) {
        return operator.getFunction().apply(field, value);
    }
}
