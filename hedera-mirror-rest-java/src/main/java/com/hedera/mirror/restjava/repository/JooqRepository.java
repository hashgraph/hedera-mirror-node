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

import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.common.RangeParameter;
import com.hedera.mirror.restjava.service.Bound;
import java.util.Arrays;
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

    default Condition getCondition(Bound bound) {
        var field = bound.getField();
        return getCondition(bound.getLower(), field).and(getCondition(bound.getUpper(), field));
    }

    default Condition getBoundConditions(List<Bound> bounds) {
        return getBoundConditions(bounds, false, false);
    }

    default Condition getBoundConditions(List<Bound> bounds, boolean lowerProcessed, boolean upperProcessed) {
        if (bounds.isEmpty()) {
            return noCondition();
        }

        var primary = bounds.getFirst();
        if (bounds.size() == 1) {
            return getCondition(primary);
        }

        var secondaryBounds = bounds.subList(1, bounds.size());
        if (primary.isEmpty() && secondaryBounds.isEmpty()) {
            return noCondition();
        }

        if (!lowerProcessed) {
            for (var bound : secondaryBounds) {
                // Only secondary bounds should be adjusted
                bound.adjustUpperRange();
            }
        }

        // Lower conditions need to be discovered before upper conditions because the methods involved update the
        // primary bound
        var lowerCondition = getOuterCondition(primary, secondaryBounds, false, lowerProcessed);
        var middleCondition = getMiddleCondition(primary, secondaryBounds);
        var upperCondition = getOuterCondition(primary, secondaryBounds, true, upperProcessed);

        return lowerCondition.or(middleCondition).or(upperCondition);
    }

    private Condition getOuterCondition(
            Bound primary, List<Bound> secondaryBounds, boolean isUpper, boolean processed) {
        var outerCondition = getPrimaryCondition(primary, isUpper);
        if (outerCondition != noCondition()) {
            var outerBounds = processed ? secondaryBounds : removeRanges(secondaryBounds, isUpper);
            outerCondition = outerCondition.and(getBoundConditions(outerBounds, true, isUpper));
        }

        return outerCondition;
    }

    // Returns a list of new bounds that have had their lower or upper ranges removed
    private List<Bound> removeRanges(List<Bound> bounds, boolean isUpper) {
        return bounds.stream().map(b -> new Bound(b, isUpper)).toList();
    }

    private Condition getPrimaryCondition(Bound primary, boolean isUpper) {
        var rangeParameter = isUpper ? primary.getUpper() : primary.adjustLowerRange();
        if (rangeParameter == null || rangeParameter.isEmpty() || rangeParameter.operator() == EQ) {
            return noCondition();
        } else {
            long value = primary.getEqualityRangeValue(isUpper);
            return getCondition(primary.getField(), EQ, value);
        }
    }

    private Condition getMiddleCondition(Bound primaryBound, List<Bound> secondaryBounds) {
        var primaryLower = primaryBound.getLower();
        var primaryUpper = primaryBound.getUpper();
        var field = primaryBound.getField();
        var primaryLowerCondition = getPrimaryMiddleCondition(primaryLower, field, GT);
        var primaryUpperCondition = getPrimaryMiddleCondition(primaryUpper, field, LT);

        var secondaryCondition = noCondition();
        for (var secondaryBound : secondaryBounds) {
            if (containsEqOperator(primaryLower, primaryUpper, secondaryBound.getUpper(), secondaryBound.getLower())) {
                secondaryCondition = secondaryCondition.and(getCondition(secondaryBound));
            }
        }

        return primaryLowerCondition.and(primaryUpperCondition).and(secondaryCondition);
    }

    private Condition getPrimaryMiddleCondition(
            RangeParameter<Long> rangeParameter, Field<Long> field, RangeOperator inclusiveOperator) {
        var condition = noCondition();
        if (rangeParameter != null && !rangeParameter.isEmpty()) {
            // When the primary param operator is EQ don't adjust the value for the primary param.
            if (rangeParameter.operator() == EQ) {
                condition = getCondition(rangeParameter, field);
            } else if (rangeParameter.operator().isInclusive()) {
                condition = getCondition(field, inclusiveOperator, rangeParameter.value());
            } else {
                condition = getCondition(rangeParameter, field);
            }
        }

        return condition;
    }

    private boolean containsEqOperator(RangeParameter<?>... rangeParameters) {
        return Arrays.stream(rangeParameters).anyMatch(this::hasEqOperator);
    }

    private boolean hasEqOperator(RangeParameter<?> rangeParameter) {
        return rangeParameter != null && rangeParameter.operator() == EQ;
    }
}
