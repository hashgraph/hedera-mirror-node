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
import org.springframework.util.CollectionUtils;

public class Bound {

    @Getter
    private EntityIdRangeParameter lower;

    @Getter
    private EntityIdRangeParameter upper;

    private final EnumMap<RangeOperator, Integer> cardinality = new EnumMap<>(RangeOperator.class);

    public Bound(List<EntityIdRangeParameter> params, boolean primarySortField) {

        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        for (EntityIdRangeParameter param : params) {

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
            throw new IllegalArgumentException("Invalid range provided");
        }
    }

    private long adjustUpperBound() {
        if (this.upper == null) {
            return Long.MAX_VALUE;
        }
        long upperBound = this.upper.value().getId();
        if (this.upper.operator() == RangeOperator.LT) {
            upperBound--;
        }
        return upperBound;
    }

    private long adjustLowerBound() {
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
        return getLower().value().getId() == getUpper().value().getId();
    }

    public void verifyUnsupported(RangeOperator unsupportedOperator) {
        if (getCardinality(unsupportedOperator) > 0) {
            throw new IllegalArgumentException(String.format("Unsupported range operator %s", unsupportedOperator));
        }
    }

    public void verifySingleOccurrence() {
        if (this.getCardinality(RangeOperator.GT, RangeOperator.GTE) > 1
                || this.getCardinality(RangeOperator.LT, RangeOperator.LTE) > 1
                || this.getCardinality(RangeOperator.EQ) > 1) {
            throw new IllegalArgumentException("Single occurrence only supported.");
        }
    }

    public void verifyEqualOrRange() {
        if (this.getCardinality(RangeOperator.EQ) == 1
                && (this.getCardinality(RangeOperator.GT, RangeOperator.GTE) != 0
                        || this.getCardinality(RangeOperator.LT, RangeOperator.LTE) != 0)) {
            throw new IllegalArgumentException("Can't support both range and equal for this parameter.");
        }
    }
}
