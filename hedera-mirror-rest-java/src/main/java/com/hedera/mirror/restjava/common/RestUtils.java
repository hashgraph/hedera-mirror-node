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

import static com.hedera.mirror.restjava.common.EntityIdUtils.parseIdFromString;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RestUtils {

    public static final Integer MAX_LIMIT = 100;
    public static final Integer DEFAULT_LIMIT = 25;

    public static Filter getFilter(String paramName, String queryParam) {

        if (paramName == null || queryParam == null) {
            return null;
        }

        String[] splitVal = queryParam.split(":");

        if (splitVal.length == 1) {
            // No operator specified. Just use "eq:"
            return new Filter(paramName, RangeOperator.EQ, splitVal[0]);
        }
        return new Filter(paramName, RangeOperator.valueOf(splitVal[0].toUpperCase()), splitVal[1]);
    }

    public static Long getAccountNum(Filter filter) {
        return filter != null ? parseIdFromString(filter.getValue())[2] : null;
    }

    public static Integer validateLimit(Integer limit) {
        return limit > MAX_LIMIT ? MAX_LIMIT : limit;
    }
}
