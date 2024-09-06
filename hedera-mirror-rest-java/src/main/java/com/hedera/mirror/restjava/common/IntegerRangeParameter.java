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

package com.hedera.mirror.restjava.common;

import org.apache.commons.lang3.StringUtils;

public record IntegerRangeParameter(RangeOperator operator, Integer value) implements RangeParameter<Integer> {

    public static final IntegerRangeParameter EMPTY = new IntegerRangeParameter(null, null);

    public static IntegerRangeParameter valueOf(String valueRangeParam) {
        if (StringUtils.isBlank(valueRangeParam)) {
            return EMPTY;
        }

        var splitVal = valueRangeParam.split(":");
        return switch (splitVal.length) {
            case 1 -> new IntegerRangeParameter(RangeOperator.EQ, Integer.valueOf(splitVal[0]));
            case 2 -> new IntegerRangeParameter(RangeOperator.of(splitVal[0]), Integer.valueOf(splitVal[1]));
            default -> throw new IllegalArgumentException(
                    "Invalid range operator %s. Should have format rangeOperator:Integer".formatted(valueRangeParam));
        };
    }
}
