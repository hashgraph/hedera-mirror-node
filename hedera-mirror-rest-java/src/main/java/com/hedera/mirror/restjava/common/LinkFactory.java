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

import com.hedera.mirror.rest.model.Links;
import java.util.Map.Entry;
import java.util.function.Function;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.web.util.UriComponentsBuilder;

public interface LinkFactory {
    <T> Links create(T item, ParameterExtractor<T> extractor);

    interface ParameterExtractor<T> {
        Function<T, String> extract(String name);
    }

    /**
     * Add a query parameter to the UriComponentsBuilder
     *
     * @return true if the query parameter was added, false otherwise
     */
    default <T> boolean addQueryParam(
            Function<T, String> extract,
            T item,
            Entry<String, String[]> entry,
            UriComponentsBuilder builder,
            Direction order,
            String key,
            boolean inclusive) {
        if (extract == null) {
            return false;
        }

        var lastParameterValue = extract.apply(item);
        if (lastParameterValue == null) {
            return false;
        }

        for (var value : entry.getValue()) {
            if (order.isDescending()) {
                builder.queryParam(key, value);
            } else {
                var splitVal = value.split(":");
                var operator = splitVal.length == 1
                        ? (inclusive ? RangeOperator.GTE : RangeOperator.GT)
                        : RangeOperator.of(splitVal[0]);

                builder.queryParam(key, operator.toString() + ":" + lastParameterValue);
            }
        }

        return true;
    }
}
