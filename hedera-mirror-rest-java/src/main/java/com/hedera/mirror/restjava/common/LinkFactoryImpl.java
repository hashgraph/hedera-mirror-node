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

import static com.hedera.mirror.restjava.common.ParameterNames.LIMIT;
import static com.hedera.mirror.restjava.common.ParameterNames.ORDER;

import com.hedera.mirror.rest.model.Links;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
public class LinkFactoryImpl implements LinkFactory {

    private final HttpServletRequest request;

    public <T> Links create(Iterable<T> items, Pageable pageable, ParameterExtractor<T> extractor) {
        var links = new Links();
        if (items == null) {
            return links;
        }

        var itemsList = StreamSupport.stream(items.spliterator(), false).toList();
        var lastItem = CollectionUtils.lastElement(itemsList);
        var limit = pageable.getPageSize();
        var paramsMap = request.getParameterMap();
        if (lastItem == null || limit > itemsList.size() || paramsMap.isEmpty()) {
            return links;
        }

        var sorts = pageable.getSort().iterator();
        var primarySort = sorts.hasNext() ? sorts.next() : null;
        var order = primarySort == null ? Direction.ASC : primarySort.getDirection();
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            switch (key) {
                case LIMIT -> builder.queryParam(LIMIT, limit);
                case ORDER -> builder.queryParam(ORDER, order.name().toLowerCase());
                default -> {
                    var extract = extractor.extract(key);
                    if (extract != null) {
                        var value = extract.apply(lastItem);
                        boolean inclusive = extractor.isInclusive(key);
                        var queryValues = addQueryParam(value, entry, order, inclusive);
                        builder.queryParam(key, queryValues);
                    }
                }
            }
        }

        var secondarySort = sorts.hasNext() ? sorts.next() : null;
        addRangeBoundaries(primarySort, secondarySort, extractor, lastItem, builder);
        return links.next(builder.toUriString());
    }

    private <T> void addRangeBoundaries(
            Order primarySort,
            Order secondarySort,
            ParameterExtractor<T> extractor,
            T lastItem,
            UriComponentsBuilder builder) {
        if (primarySort != null) {
            var primarySortValue = extractor.extract(primarySort.getProperty()).apply(lastItem);
            if (primarySort.isDescending()) {
                var upperBound = RangeOperator.LTE + ":" + primarySortValue;
                builder.queryParam(primarySort.getProperty(), upperBound);

                if (secondarySort != null) {
                    var secondarySortValue =
                            extractor.extract(secondarySort.getProperty()).apply(lastItem);
                    builder.queryParam(secondarySort.getProperty(), RangeOperator.LT + ":" + secondarySortValue);
                }
            } else {
                var queryParameters = builder.toUriString();
                if (!queryParameters.contains(primarySort.getProperty())) {
                    builder.queryParam(primarySort.getProperty(), RangeOperator.GTE + ":" + primarySortValue);
                }
                if (secondarySort != null && !queryParameters.contains(secondarySort.getProperty())) {
                    var secondarySortValue =
                            extractor.extract(secondarySort.getProperty()).apply(lastItem);
                    builder.queryParam(secondarySort.getProperty(), RangeOperator.GT + ":" + secondarySortValue);
                }
            }
        }
    }

    private List<String> addQueryParam(
            String lastParameterValue, Entry<String, String[]> entry, Direction order, boolean inclusive) {
        var queryParams = new ArrayList<String>();
        if (lastParameterValue == null) {
            return queryParams;
        }

        for (var value : entry.getValue()) {
            if (order.isDescending() || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("true")) {
                queryParams.add(value);
            } else {
                var operator = getOperator(value, inclusive);
                queryParams.add(operator.toString() + ":" + lastParameterValue);
            }
        }

        return queryParams;
    }

    private RangeOperator getOperator(String value, boolean inclusive) {
        var splitVal = value.split(":");
        if (splitVal.length != 1) {
            return RangeOperator.of(splitVal[0]);
        } else {
            return inclusive ? RangeOperator.GTE : RangeOperator.GT;
        }
    }
}
