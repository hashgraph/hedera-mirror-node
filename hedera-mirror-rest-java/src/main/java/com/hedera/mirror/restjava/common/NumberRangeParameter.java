/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

public record NumberRangeParameter(RangeOperator operator, Long value) implements RangeParameter<Long> {

    public static final NumberRangeParameter EMPTY = new NumberRangeParameter(null, null);

    public static NumberRangeParameter valueOf(String valueRangeParam) {
        if (StringUtils.isBlank(valueRangeParam)) {
            return EMPTY;
        }

        var splitVal = valueRangeParam.split(":");
        return switch (splitVal.length) {
            case 1 -> new NumberRangeParameter(RangeOperator.EQ, getNumberValue(splitVal[0]));
            case 2 -> new NumberRangeParameter(RangeOperator.of(splitVal[0]), getNumberValue(splitVal[1]));
            default -> throw new IllegalArgumentException(
                    "Invalid range operator %s. Should have format rangeOperator:Number".formatted(valueRangeParam));
        };
    }

    private static long getNumberValue(String number) {
        var value = Long.parseLong(number);
        if (value < 0) {
            throw new IllegalArgumentException("Invalid range value: " + number);
        }

        return value;
    }
}
