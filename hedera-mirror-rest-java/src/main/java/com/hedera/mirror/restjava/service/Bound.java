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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import java.util.EnumMap;
import java.util.List;
import lombok.Getter;

@Getter
public class Bound {

    private EntityIdRangeParameter lower;
    private EntityIdRangeParameter upper;
    private final EnumMap<RangeOperator, Integer> cardinality = new EnumMap<>(RangeOperator.class);

    public Bound(List<EntityIdRangeParameter> params) {

        if (params != null) {
            for (EntityIdRangeParameter param : params) {
                // Considering EQ in the same category as GT,GTE as an assumption
                if (param.hasLowerBound()) {
                    lower = param;
                } else if (param.hasUpperBound()) {
                    upper = param;
                }
                populateCardinality(param);
            }
            long adjustedLower = adjustLowerBound();
            long adjustedUpper = adjustUpperBound();

            if (adjustedLower == adjustedUpper) {
                this.lower = new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(adjustedLower));
            }
        }
    }

    private long adjustUpperBound() {
        long upperBound = Long.MAX_VALUE;

        if (this.upper != null) {
            upperBound = this.upper.value().getId();
            if (this.upper.operator() == RangeOperator.LT) {
                upperBound--;
            }
        }
        return upperBound;
    }

    private long adjustLowerBound() {
        long lowerBound = 0;
        if (this.lower != null) {
            lowerBound = this.lower.value().getId();
            if (this.lower.operator() == RangeOperator.GT) {
                lowerBound++;
            }
        }
        return lowerBound;
    }

    private void populateCardinality(EntityIdRangeParameter param) {
        switch (param.operator()) {
            case RangeOperator.GT -> cardinality.put(
                    RangeOperator.GT, cardinality.getOrDefault(RangeOperator.GT, 0) + 1);
            case RangeOperator.GTE -> cardinality.put(
                    RangeOperator.GTE, cardinality.getOrDefault(RangeOperator.GTE, 0) + 1);
            case RangeOperator.LT -> cardinality.put(
                    RangeOperator.LT, cardinality.getOrDefault(RangeOperator.LT, 0) + 1);
            case RangeOperator.LTE -> cardinality.put(
                    RangeOperator.LTE, cardinality.getOrDefault(RangeOperator.LTE, 0) + 1);
            case RangeOperator.EQ -> cardinality.put(
                    RangeOperator.EQ, cardinality.getOrDefault(RangeOperator.EQ, 0) + 1);
            case RangeOperator.NE -> cardinality.put(
                    RangeOperator.NE, cardinality.getOrDefault(RangeOperator.NE, 0) + 1);
        }
    }

    public int getCardinality(RangeOperator operator) {
        return cardinality.getOrDefault(operator, 0);
    }
}
