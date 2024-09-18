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

import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Field;
import org.springframework.util.CollectionUtils;

public class Bound {

    public static final Bound EMPTY = new Bound(null, false, StringUtils.EMPTY, null);

    @Getter
    private final Field<Long> field;

    @Getter
    @Setter
    private EntityIdRangeParameter lower;

    @Getter
    @Setter
    private EntityIdRangeParameter upper;

    private final String parameterName;

    private final EnumMap<RangeOperator, Integer> cardinality = new EnumMap<>(RangeOperator.class);

    public Bound(
            List<EntityIdRangeParameter> params, boolean primarySortField, String parameterName, Field<Long> field) {
        this.field = field;
        this.parameterName = parameterName;

        if (CollectionUtils.isEmpty(params)) {
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

        long adjustedLower = adjustLowerBound();
        long adjustedUpper = adjustUpperBound();
        if (primarySortField && adjustedLower > adjustedUpper) {
            throw new IllegalArgumentException("Invalid range provided for %s".formatted(parameterName));
        }
    }

    public long adjustUpperBound() {
        if (this.upper == null) {
            return Long.MAX_VALUE;
        }

        long upperBound = this.upper.value().getId();
        if (this.upper.operator() == RangeOperator.LT) {
            upperBound--;
        }

        return upperBound;
    }

    public long adjustLowerBound() {
        if (this.lower == null) {
            return 0;
        }

        long lowerBound = this.lower.value().getId();
        if (this.lower.operator() == RangeOperator.GT) {
            lowerBound++;
        }

        return lowerBound;
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
        return hasLowerAndUpper() && adjustLowerBound() == adjustUpperBound();
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

    private void verifySingleOccurrence(RangeOperator... rangeOperators) {
        if (this.getCardinality(rangeOperators) > 1) {
            throw new IllegalArgumentException(
                    "Only one range operator from %s is allowed for the given parameter for %s"
                            .formatted(Arrays.toString(rangeOperators), parameterName));
        }
    }

    public void verifyEqualOrRange() {
        if (this.getCardinality(RangeOperator.EQ) == 1
                && (this.getCardinality(RangeOperator.GT, RangeOperator.GTE) != 0
                        || this.getCardinality(RangeOperator.LT, RangeOperator.LTE) != 0)) {
            throw new IllegalArgumentException("Can't support both range and equal for %s".formatted(parameterName));
        }
    }
}
