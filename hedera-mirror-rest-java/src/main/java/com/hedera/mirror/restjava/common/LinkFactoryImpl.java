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

import com.google.common.collect.Iterables;
import com.hedera.mirror.rest.model.Links;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
public class LinkFactoryImpl implements LinkFactory {

    private static final Links DEFAULT_LINKS = new Links();

    public <T> Links create(List<T> items, Pageable pageable, ParameterExtractor<T> extractor) {
        if (items == null || items.isEmpty() || pageable == null || extractor == null) {
            return DEFAULT_LINKS;
        }

        var limit = pageable.getPageSize();
        if (limit > items.size()) {
            return DEFAULT_LINKS;
        }

        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes == null) {
            return DEFAULT_LINKS;
        }

        var request = servletRequestAttributes.getRequest();
        var lastItem = CollectionUtils.lastElement(items);
        var nextLink = createNextLink(lastItem, pageable, extractor, request);
        return new Links().next(nextLink);
    }

    private <T> String createNextLink(
            T lastItem, Pageable pageable, ParameterExtractor<T> extractor, HttpServletRequest request) {
        var sortOrders = pageable.getSort();
        var primarySort = Iterables.getFirst(sortOrders, null);
        var order = primarySort == null ? Direction.ASC : primarySort.getDirection();
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        var paramsMap = request.getParameterMap();

        var paginationParamsMap = extractor.extract(lastItem);
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            if (!paginationParamsMap.containsKey(key)) {
                builder.queryParam(key, (Object[]) entry.getValue());
            } else {
                boolean inclusive = extractor.isInclusive(key);
                var lastValue = paginationParamsMap.get(key);
                addQueryParamToLink(entry, inclusive, builder, lastValue, order, key);
            }
        }

        for (var entry : paginationParamsMap.entrySet()) {
            var key = entry.getKey();
            if (!paramsMap.containsKey(key)) {
                boolean inclusive = extractor.isInclusive(key);
                addPaginationValuesToLink(key, entry.getValue(), inclusive, builder, order);
            }
        }

        return builder.toUriString();
    }

    private void addPaginationValuesToLink(
            String key, String lastValue, boolean inclusive, UriComponentsBuilder builder, Direction order) {
        RangeOperator operator;
        if (order.isDescending()) {
            operator = inclusive ? RangeOperator.LTE : RangeOperator.LT;
        } else {
            operator = inclusive ? RangeOperator.GTE : RangeOperator.GT;
        }
        builder.queryParam(key, operator + ":" + lastValue);
    }

    private void addQueryParamToLink(
            Entry<String, String[]> entry,
            boolean inclusive,
            UriComponentsBuilder builder,
            String lastValue,
            Direction order,
            String key) {
        var rangeBounds = Arrays.stream(entry.getValue())
                .distinct()
                .map(RangeBound::valueOf)
                .filter(rangeBound -> {
                    // Add to the link parameters that have an eq operator
                    if (rangeBound.operator().equals(RangeOperator.EQ)) {
                        builder.queryParam(key, rangeBound.value());
                        return false;
                    }

                    return true;
                })
                .toList();

        // The lastValueOperator is set based on the order
        RangeOperator lastValueOperator;
        if (order.isDescending()) {
            lastValueOperator = inclusive ? RangeOperator.LTE : RangeOperator.LT;

            // If a lower bound is found, always add it to the link
            var lowerBound = rangeBounds.stream()
                    .filter(rangeBound ->
                            rangeBound.operator() == RangeOperator.GT || rangeBound.operator() == RangeOperator.GTE)
                    .reduce((rangeBound1, rangeBound2) -> {
                        var compare = rangeBound1.value().compareTo(rangeBound2.value());
                        if (compare == 0) {
                            return rangeBound1.operator() == RangeOperator.GT ? rangeBound1 : rangeBound2;
                        }
                        return compare < 0 ? rangeBound1 : rangeBound2;
                    })
                    .orElse(RangeBound.EMPTY);
            addBoundToLink(lowerBound, key, builder);
        } else {
            lastValueOperator = inclusive ? RangeOperator.GTE : RangeOperator.GT;

            // If an upper bound is found, always add it to the link
            var upperBound = rangeBounds.stream()
                    .filter(rangeBound ->
                            rangeBound.operator() == RangeOperator.LT || rangeBound.operator() == RangeOperator.LTE)
                    .reduce((rangeBound1, rangeBound2) -> {
                        var compare = rangeBound1.value().compareTo(rangeBound2.value());
                        if (compare == 0) {
                            return rangeBound1.operator() == RangeOperator.LT ? rangeBound1 : rangeBound2;
                        }
                        return compare > 0 ? rangeBound1 : rangeBound2;
                    })
                    .orElse(RangeBound.EMPTY);
            addBoundToLink(upperBound, key, builder);
        }

        // Always add the last value to the link
        builder.queryParam(key, lastValueOperator + ":" + lastValue);
    }

    private void addBoundToLink(RangeBound bound, String key, UriComponentsBuilder builder) {
        if (bound != RangeBound.EMPTY) {
            builder.queryParam(key, bound.operator() + ":" + bound.value());
        }
    }

    private record RangeBound(RangeOperator operator, String value) implements RangeParameter<String> {
        private static final RangeBound EMPTY = new RangeBound(null, null);

        public static RangeBound valueOf(String value) {
            if (StringUtils.isBlank(value)) {
                return EMPTY;
            }

            value = value.toLowerCase();
            var splitValues = value.split(":");
            if (splitValues.length != 2) {
                return new RangeBound(RangeOperator.EQ, value);
            }

            var operator = RangeOperator.of(splitValues[0]);
            var rangeValue = splitValues[1];
            return new RangeBound(operator, rangeValue);
        }
    }
}
