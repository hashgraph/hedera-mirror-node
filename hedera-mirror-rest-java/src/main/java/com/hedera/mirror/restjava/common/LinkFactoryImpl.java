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
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
class LinkFactoryImpl implements LinkFactory {

    private static final Links DEFAULT_LINKS = new Links();

    @Override
    public <T> Links create(
            List<T> items, @Nonnull Pageable pageable, @Nonnull Function<T, Map<String, String>> extractor) {
        if (CollectionUtils.isEmpty(items) || pageable.getPageSize() > items.size()) {
            return DEFAULT_LINKS;
        }

        var servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes == null) {
            return DEFAULT_LINKS;
        }

        var request = servletRequestAttributes.getRequest();
        var lastItem = CollectionUtils.lastElement(items);
        var nextLink = createNextLink(lastItem, pageable, extractor, request);
        return new Links().next(nextLink);
    }

    private <T> String createNextLink(
            T lastItem, Pageable pageable, Function<T, Map<String, String>> extractor, HttpServletRequest request) {
        var sortOrders = pageable.getSort();
        var primarySort = Iterables.getFirst(sortOrders, null);
        var order = primarySort == null ? Direction.ASC : primarySort.getDirection();
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        var paramsMap = request.getParameterMap();
        var paginationParamsMap = extractor.apply(lastItem);
        var linkedMultiValueMap = new LinkedMultiValueMap<String, String>();

        addParamMapToLink(paramsMap, paginationParamsMap, order, linkedMultiValueMap);
        addExtractedParamsToLink(sortOrders, paginationParamsMap, order, linkedMultiValueMap);
        builder.queryParams(linkedMultiValueMap);
        return builder.toUriString();
    }

    private void addParamMapToLink(
            Map<String, String[]> paramsMap,
            Map<String, String> paginationParamsMap,
            Direction order,
            LinkedMultiValueMap<String, String> linkedMultiValueMap) {
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            if (!paginationParamsMap.containsKey(key)) {
                for (var value : entry.getValue()) {
                    linkedMultiValueMap.add(entry.getKey(), value);
                }
            } else {
                addQueryParamToLink(entry, order, linkedMultiValueMap);
            }
        }
    }

    private void addQueryParamToLink(
            Entry<String, String[]> entry, Direction order, LinkedMultiValueMap<String, String> linkedMultiValueMap) {
        for (var value : entry.getValue()) {
            // Skip if it's in the same direction as the order, the new bound should come from the extracted value
            if (isSameDirection(order, value)) {
                continue;
            }

            linkedMultiValueMap.add(entry.getKey(), value);
            if (hasEq(value)) {
                // Only 1 eq is allowed, return without adding additional values
                return;
            }
        }
    }

    private void addExtractedParamsToLink(
            Sort sort,
            Map<String, String> paginationParamsMap,
            Direction order,
            LinkedMultiValueMap<String, String> linkedMultiValueMap) {
        var sortKeys = sort.map(Sort.Order::getProperty).toList();
        if (sortKeys.isEmpty()) {
            for (var entry : paginationParamsMap.entrySet()) {
                linkedMultiValueMap.add(entry.getKey(), entry.getValue());
            }
            return;
        }

        for (int i = 0; i < sortKeys.size(); i++) {
            var key = sortKeys.get(i);
            if (linkedMultiValueMap.containsKey(key) && containsEq(linkedMultiValueMap.get(key))) {
                // This query parameter has already been added with an eq
                continue;
            }

            var entry = paginationParamsMap.get(key);
            var exclusive = true;
            if (sortKeys.size() > i + 1) {
                var succeedingKey = sortKeys.get(i + 1);
                var succeedingEntry = linkedMultiValueMap.get(succeedingKey);
                exclusive = containsEq(succeedingEntry);
            }

            linkedMultiValueMap.add(key, getOperator(order, exclusive) + ":" + entry);
        }
    }

    private static RangeOperator getOperator(Direction order, boolean exclusive) {
        return switch (order) {
            case ASC -> exclusive ? RangeOperator.GT : RangeOperator.GTE;
            case DESC -> exclusive ? RangeOperator.LT : RangeOperator.LTE;
        };
    }

    private static boolean isSameDirection(Direction order, String value) {
        var normalized = value.toLowerCase();
        return switch (order) {
            case ASC -> normalized.startsWith("gt:") || normalized.startsWith("gte:");
            case DESC -> normalized.startsWith("lt:") || normalized.startsWith("lte:");
        };
    }

    private static boolean containsEq(List<String> values) {
        if (values != null) {
            for (var value : values) {
                if (hasEq(value)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasEq(String value) {
        var normalized = value.toLowerCase();
        return normalized.startsWith("eq:") || (!normalized.startsWith("gt") && !normalized.startsWith("lt"));
    }
}
