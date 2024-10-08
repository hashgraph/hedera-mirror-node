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

package com.hedera.mirror.restjava.service;

import static com.hedera.mirror.restjava.common.RangeOperator.EQ;
import static com.hedera.mirror.restjava.common.RangeOperator.GT;
import static com.hedera.mirror.restjava.common.RangeOperator.LT;

import com.hedera.mirror.restjava.common.NumberRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.common.RangeParameter;
import java.util.Arrays;
import java.util.EnumMap;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Field;

public class Bound {

    public static final Bound EMPTY = new Bound(null, false, StringUtils.EMPTY, null);

    @Getter
    private final Field<Long> field;

    @Getter
    private RangeParameter<Long> lower;

    @Getter
    private RangeParameter<Long> upper;

    private final String parameterName;

    private final EnumMap<RangeOperator, Integer> cardinality = new EnumMap<>(RangeOperator.class);

    public Bound(RangeParameter<Long>[] params, boolean primarySortField, String parameterName, Field<Long> field) {
        this.field = field;
        this.parameterName = parameterName;

        if (ArrayUtils.isEmpty(params)) {
            return;
        }

        for (var param : params) {
            if (param.hasLowerBound()) {
                lower = param;
            } else if (param.hasUpperBound()) {
                upper = param;
            }
            cardinality.merge(param.operator(), 1, Math::addExact);
        }

        long adjustedLower = getAdjustedLowerRangeValue();
        long adjustedUpper = adjustUpperBound();
        if (primarySortField && adjustedLower > adjustedUpper) {
            throw new IllegalArgumentException("Invalid range provided for %s".formatted(parameterName));
        }
    }

    public long adjustUpperBound() {
        if (this.upper == null) {
            return Long.MAX_VALUE;
        }

        long upperBound = this.upper.value();
        if (this.upper.operator() == RangeOperator.LT) {
            upperBound--;
        }

        return upperBound;
    }

    public RangeParameter<Long> adjustLowerRange() {
        if (this.hasEqualBounds()) {
            // If the primary param has a range with a single value, rewrite it to EQ
            lower = new NumberRangeParameter(EQ, this.getAdjustedLowerRangeValue());
            upper = null;
        }

        return lower;
    }

    public long getAdjustedLowerRangeValue() {
        if (this.lower == null) {
            return 0;
        }

        long lowerBound = this.lower.value();
        if (this.lower.operator() == RangeOperator.GT) {
            lowerBound++;
        }

        return lowerBound;
    }

    public void adjustUpperRange() {
        if (!this.isEmpty() && lower != null && lower.operator() == EQ) {
            // If the secondary param operator is EQ, set the secondary upper bound to the same
            upper = lower;
        }
    }

    // Gets a range value if the operator was converted to EQ
    public long getEqualityRangeValue(boolean upper) {
        var rangeParameter = upper ? this.getUpper() : this.getLower();
        var operator = rangeParameter.operator();
        long value = rangeParameter.value();
        if (!upper && !this.isEmpty() && this.hasEqualBounds()) {
            // If the primary param has a range with a single value, use the lower range value
            value = this.getAdjustedLowerRangeValue();
        } else if (operator == GT) {
            value += 1L;
        } else if (operator == LT) {
            value -= 1L;
        }

        return value;
    }

    public int getCardinality(RangeOperator... operators) {
        return Arrays.stream(operators)
                .mapToInt(x -> cardinality.getOrDefault(x, 0))
                .sum();
    }

    public boolean isEmpty() {
        return lower == null && upper == null;
    }

    public boolean hasLowerAndUpper() {
        return lower != null && upper != null;
    }

    public boolean hasEqualBounds() {
        return hasLowerAndUpper() && getAdjustedLowerRangeValue() == adjustUpperBound();
    }

    // Returns a new bound with only a lower rangeParameter
    public Bound toLower(Bound bound) {
        return createBound(bound.getLower());
    }

    // Returns a new bound with only an upper rangeParameter
    public Bound toUpper(Bound bound) {
        return createBound(bound.getUpper());
    }

    public void verifyUnsupported(RangeOperator unsupportedOperator) {
        if (getCardinality(unsupportedOperator) > 0) {
            throw new IllegalArgumentException(
                    String.format("Unsupported range operator %s for %s", unsupportedOperator, parameterName));
        }
    }

    public void verifySingleOccurrence() {
        verifySingleOccurrence(RangeOperator.EQ);
        verifySingleOccurrence(RangeOperator.GT, RangeOperator.GTE);
        verifySingleOccurrence(RangeOperator.LT, RangeOperator.LTE);
    }

    public void verifyEqualOrRange() {
        if (this.getCardinality(RangeOperator.EQ) == 1
                && (this.getCardinality(RangeOperator.GT, RangeOperator.GTE) != 0
                        || this.getCardinality(RangeOperator.LT, RangeOperator.LTE) != 0)) {
            throw new IllegalArgumentException("Can't support both range and equal for %s".formatted(parameterName));
        }
    }

    private Bound createBound(RangeParameter<Long> param) {
        if (param == null) {
            return Bound.EMPTY;
        }

        var params = new NumberRangeParameter[] {new NumberRangeParameter(param.operator(), param.value())};
        return new Bound(params, false, parameterName, field);
    }

    private void verifySingleOccurrence(RangeOperator... rangeOperators) {
        if (this.getCardinality(rangeOperators) > 1) {
            throw new IllegalArgumentException(
                    "Only one range operator from %s is allowed for the given parameter for %s"
                            .formatted(Arrays.toString(rangeOperators), parameterName));
        }
    }
}
