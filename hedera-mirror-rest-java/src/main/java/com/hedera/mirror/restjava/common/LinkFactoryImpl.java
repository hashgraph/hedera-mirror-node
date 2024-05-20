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

import static com.hedera.mirror.restjava.common.EntityIdNumParameter.ENTITY_ID_PATTERN;
import static com.hedera.mirror.restjava.common.ParameterNames.LIMIT;
import static com.hedera.mirror.restjava.common.ParameterNames.ORDER;

import com.google.common.collect.Iterables;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.Links;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
public class LinkFactoryImpl implements LinkFactory {

    public <T> Links create(List<T> items, Pageable pageable, ParameterExtractor<T> extractor) {
        var links = new Links();
        if (items == null || items.isEmpty() || pageable.isUnpaged()) {
            return links;
        }

        var limit = pageable.getPageSize();
        if (limit > items.size()) {
            return links;
        }

        var lastItem = CollectionUtils.lastElement(items);
        var nextLink = createNextLink(lastItem, limit, pageable, extractor);
        return links.next(nextLink);
    }

    private <T> String createNextLink(T lastItem, int limit, Pageable pageable, ParameterExtractor<T> extractor) {
        var sortOrders = pageable.getSort();
        var primarySort = Iterables.getFirst(sortOrders, null);
        var lastSort = Iterables.getLast(sortOrders, null);
        var order = primarySort == null ? Direction.ASC : primarySort.getDirection();
        var request = extractor.getRequest();
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        var paramsMap = request.getParameterMap();

        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            switch (key) {
                case LIMIT -> builder.queryParam(LIMIT, limit);
                case ORDER -> builder.queryParam(ORDER, order.name().toLowerCase());
                default -> {
                    var lastValue = extractor.extract(lastItem, key);
                    if (lastValue == null) {
                        builder.queryParam(key, (Object[]) entry.getValue());
                    } else {
                        boolean inclusive = false;
                        if (lastSort != null) {
                            inclusive = !lastSort.getProperty().equals(key);
                        }

                        addQueryParamToLink(entry, inclusive, builder, lastValue, order, key);
                    }
                }
            }
        }

        // For any sort orders that are not already in the query parameters, add the last value to the link
        addSortOrdersToLink(sortOrders, paramsMap.keySet(), lastSort, builder, lastItem, extractor, order);
        return builder.toUriString();
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
                    .filter(rangeBound -> rangeBound.operator() == RangeOperator.GT)
                    .reduce((rangeBound1, rangeBound2) ->
                            rangeBound1.value().compareTo(rangeBound2.value()) < 0 ? rangeBound1 : rangeBound2)
                    .orElse(RangeBound.EMPTY);
            addBoundToLink(lowerBound, key, builder);
        } else {
            lastValueOperator = inclusive ? RangeOperator.GTE : RangeOperator.GT;

            // If an upper bound is found, always add it to the link
            var upperBound = rangeBounds.stream()
                    .filter(rangeBound -> rangeBound.operator() == RangeOperator.LT)
                    .reduce((rangeBound1, rangeBound2) ->
                            rangeBound1.value().compareTo(rangeBound2.value()) > 0 ? rangeBound1 : rangeBound2)
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

    private <T> void addSortOrdersToLink(
            Sort sortOrders,
            Set<String> keySet,
            Order lastSort,
            UriComponentsBuilder builder,
            T lastItem,
            ParameterExtractor<T> extractor,
            Direction order) {
        sortOrders.stream().filter(s -> !keySet.contains(s.getProperty())).forEach(sort -> {
            var lastValue = extractor.extract(lastItem, sort.getProperty());
            if (lastValue != null) {
                var inclusive = lastSort != sort;
                RangeOperator operator;
                if (order.isDescending()) {
                    operator = inclusive ? RangeOperator.LTE : RangeOperator.LT;
                } else {
                    operator = inclusive ? RangeOperator.GTE : RangeOperator.GT;
                }
                builder.queryParam(sort.getProperty(), operator + ":" + lastValue);
            }
        });
    }

    private record RangeBound(RangeOperator operator, String value) implements RangeParameter<String> {
        public static final RangeBound EMPTY = new RangeBound(null, null);

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
            boolean decrementValue = operator == RangeOperator.GTE;
            boolean incrementValue = operator == RangeOperator.LTE;
            if (decrementValue || incrementValue) {
                operator = decrementValue ? RangeOperator.GT : RangeOperator.LT;

                if (ENTITY_ID_PATTERN.matcher(rangeValue).matches()) {
                    var entityId = EntityId.of(rangeValue);
                    var updatedIdNum = (decrementValue ? entityId.getNum() - 1 : entityId.getNum() + 1);
                    rangeValue = entityId.getShard() + "." + entityId.getRealm() + "." + updatedIdNum;
                } else if (StringUtils.isNumeric(rangeValue)) {
                    var numericValue = Integer.parseInt(rangeValue) + (decrementValue ? -1 : 1);
                    rangeValue = String.valueOf(numericValue);
                }
            }

            return new RangeBound(operator, rangeValue);
        }
    }
}
