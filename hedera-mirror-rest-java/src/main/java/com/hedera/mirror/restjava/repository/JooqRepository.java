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
import static org.jooq.impl.DSL.noCondition;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.common.NumberRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.common.RangeParameter;
import com.hedera.mirror.restjava.service.Bound;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;

interface JooqRepository {

    default Condition getCondition(RangeParameter<Long> param, Field<Long> field) {
        if (param == null || param.isEmpty()) {
            return noCondition();
        }

        return getCondition(field, param.operator(), param.value());
    }

    default Condition getCondition(Field<Long> field, RangeOperator operator, Long value) {
        return operator.getFunction().apply(field, value);
    }

    default Condition getConditions(List<Bound> bounds, boolean upper) {
        var condition = noCondition();
        for (var bound : bounds) {
            var param = upper ? bound.getUpper() : bound.getLower();
            condition = condition.and(getCondition(param, bound.getField()));
        }

        return condition;
    }

    default Condition getBoundCondition(List<Bound> bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return noCondition();
        }

        var primary = bounds.getFirst();
        var secondaryBounds = bounds.subList(1, bounds.size());
        if (primary.isEmpty() && secondaryBounds.isEmpty()) {
            return noCondition();
        }

        if (!primary.isEmpty() && primary.hasEqualBounds()) {
            // If the primary param has a range with a single value, rewrite it to EQ
            var primaryLower = new NumberRangeParameter(
                    EQ, EntityId.of(primary.adjustLowerBound()).getId());
            primary.setLower(primaryLower);
            primary.setUpper(null);
        }

        for (var bound : secondaryBounds) {
            if (!bound.isEmpty()) {
                var secondaryLower = bound.getLower();
                if (secondaryLower != null && secondaryLower.operator() == EQ) {
                    // If the secondary param operator is EQ, set the secondary upper bound to the same
                    bound.setUpper(secondaryLower);
                }
            }
        }

        var lowerCondition = getOuterBoundCondition(primary, secondaryBounds, false);
        var middleCondition = getMiddleCondition(primary, secondaryBounds, false)
                .and(getMiddleCondition(primary, secondaryBounds, true));
        var upperCondition = getOuterBoundCondition(primary, secondaryBounds, true);

        return lowerCondition.or(middleCondition).or(upperCondition);
    }

    private Condition getOuterBoundCondition(Bound primary, List<Bound> secondaryBounds, boolean upper) {
        var primaryParam = upper ? primary.getUpper() : primary.getLower();
        // No outer bound condition if there is no primary parameter, or the operator is EQ. For EQ, everything should
        // go into the middle condition
        if (primaryParam == null || primaryParam.isEmpty() || primaryParam.operator() == EQ) {
            return noCondition();
        }

        for (var secondaryBound : secondaryBounds) {
            var secondaryParam = upper ? secondaryBound.getUpper() : secondaryBound.getLower();
            // If the secondary param operator is EQ, there should only have the middle condition
            if (secondaryParam != null && secondaryParam.operator() == EQ) {
                return noCondition();
            }
        }

        long value = primaryParam.value();
        if (primaryParam.operator() == GT) {
            value += 1L;
        } else if (primaryParam.operator() == LT) {
            value -= 1L;
        }

        return getCondition(primary.getField(), EQ, value).and(getConditions(secondaryBounds, upper));
    }

    private Condition getMiddleCondition(Bound primary, List<Bound> secondaryBounds, boolean upper) {
        var primaryParam = upper ? primary.getUpper() : primary.getLower();
        if (primaryParam == null) {
            return getConditions(secondaryBounds, upper);
        }

        var lastBound = secondaryBounds.isEmpty() ? null : secondaryBounds.getLast();
        RangeParameter<Long> lastParam = null;
        if (lastBound != null) {
            lastParam = upper ? lastBound.getUpper() : lastBound.getLower();
        }

        var primaryOperator = primaryParam.operator();
        var primaryField = primary.getField();
        // When the primary param operator is EQ, or the secondary param operator is EQ, don't adjust the value for the
        // primary param.
        if (primaryOperator == EQ || (lastParam != null && lastParam.operator() == EQ)) {
            return getCondition(primaryParam, primaryField).and(getConditions(secondaryBounds, upper));
        }

        long value = primaryParam.value();
        if (primaryOperator.isInclusive()) {
            value += primaryParam.hasLowerBound() ? 1L : -1L;
        }

        return getCondition(primaryField, primaryOperator, value);
    }
}
