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
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.service.Bound;
import org.jooq.Condition;
import org.jooq.Field;

interface JooqRepository {

    default Condition getCondition(Field<Long> field, EntityIdRangeParameter param) {
        if (param == null || param == EntityIdRangeParameter.EMPTY) {
            return noCondition();
        }

        return getCondition(field, param.operator(), param.value().getId());
    }

    default Condition getCondition(Field<Long> field, RangeOperator operator, Long value) {
        return operator.getFunction().apply(field, value);
    }

    default Condition getBoundCondition(FieldBound fieldBound) {
        var primaryBound = fieldBound.primaryBound();
        var primaryLower = EntityIdRangeParameter.EMPTY;
        var primaryUpper = EntityIdRangeParameter.EMPTY;

        if (primaryBound != null) {
            primaryLower = primaryBound.getLower();
            primaryUpper = primaryBound.getUpper();

            // If the primary param has a range with a single value, rewrite it to EQ
            if (primaryBound.hasEqualBounds()) {
                primaryLower = new EntityIdRangeParameter(EQ, EntityId.of(primaryBound.adjustLowerBound()));
                primaryUpper = null;
            }
        }

        var secondaryBound = fieldBound.secondaryBound();
        var secondaryLower = EntityIdRangeParameter.EMPTY;
        var secondaryUpper = EntityIdRangeParameter.EMPTY;
        if (secondaryBound != null) {
            secondaryLower = secondaryBound.getLower();
            secondaryUpper = secondaryBound.getUpper();
            // If the secondary param operator is EQ, set the secondary upper bound to the same
            if (secondaryLower != null && secondaryLower.operator() == EQ) {
                secondaryUpper = secondaryLower;
            }
        }

        var primaryField = fieldBound.primaryField();
        var secondaryField = fieldBound.secondaryField();
        var lowerCondition = getOuterBoundCondition(primaryLower, secondaryLower, primaryField, secondaryField);
        var middleCondition = getMiddleCondition(primaryLower, secondaryLower, primaryField, secondaryField)
                .and(getMiddleCondition(primaryUpper, secondaryUpper, primaryField, secondaryField));
        var upperCondition = getOuterBoundCondition(primaryUpper, secondaryUpper, primaryField, secondaryField);

        return lowerCondition.or(middleCondition).or(upperCondition);
    }

    private Condition getOuterBoundCondition(
            EntityIdRangeParameter primaryParam,
            EntityIdRangeParameter secondaryParam,
            Field<Long> primaryField,
            Field<Long> secondaryField) {
        // No outer bound condition if there is no primary parameter, or the operator is EQ. For EQ, everything should
        // go into the middle condition
        if (primaryParam == null
                || primaryParam.equals(EntityIdRangeParameter.EMPTY)
                || primaryParam.operator() == EQ) {
            return noCondition();
        }

        // If the secondary param operator is EQ, there should only have the middle condition
        if (secondaryParam != null && secondaryParam.operator() == EQ) {
            return noCondition();
        }

        long value = primaryParam.value().getId();
        if (primaryParam.operator() == GT) {
            value += 1L;
        } else if (primaryParam.operator() == LT) {
            value -= 1L;
        }

        return getCondition(primaryField, EQ, value).and(getCondition(secondaryField, secondaryParam));
    }

    private Condition getMiddleCondition(
            EntityIdRangeParameter primaryParam,
            EntityIdRangeParameter secondaryParam,
            Field<Long> primaryField,
            Field<Long> secondaryField) {
        if (primaryParam == null || primaryParam.equals(EntityIdRangeParameter.EMPTY)) {
            return noCondition();
        }

        // When the primary param operator is EQ, or the secondary param operator is EQ, don't adjust the value for the
        // primary param.
        if (primaryParam.operator() == EQ || (secondaryParam != null && secondaryParam.operator() == EQ)) {
            return getCondition(primaryField, primaryParam).and(getCondition(secondaryField, secondaryParam));
        }

        long value = primaryParam.value().getId();
        value += primaryParam.hasLowerBound() ? 1L : -1L;
        return getCondition(primaryField, primaryParam.operator(), value);
    }

    record FieldBound(Field<Long> primaryField, Field<Long> secondaryField, Bound primaryBound, Bound secondaryBound) {}
}
