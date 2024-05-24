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
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.CollectionUtils;
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
        var lastSort = Iterables.getLast(sortOrders, null);
        var exclusiveParam = lastSort != null ? lastSort.getProperty() : null;
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        var paramsMap = request.getParameterMap();
        var paginationParamsMap = extractor.apply(lastItem);

        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            if (!paginationParamsMap.containsKey(key)) {
                builder.queryParam(key, (Object[]) entry.getValue());
            } else {
                addQueryParamToLink(entry, builder, order);
            }
        }

        for (var entry : paginationParamsMap.entrySet()) {
            var key = entry.getKey();
            var inclusive = !key.equals(exclusiveParam);
            RangeOperator operator;
            if (order.isAscending()) {
                operator = inclusive ? RangeOperator.GTE : RangeOperator.GT;
            } else {
                operator = inclusive ? RangeOperator.LTE : RangeOperator.LT;
            }
            builder.queryParam(key, operator + ":" + entry.getValue());
        }

        return builder.toUriString();
    }

    private void addQueryParamToLink(Entry<String, String[]> entry, UriComponentsBuilder builder, Direction order) {
        for (var value : entry.getValue()) {
            var rangeBound = RangeBound.valueOf(value);
            var operator = rangeBound.operator();
            if ((order.isAscending() && (operator == RangeOperator.GT || operator == RangeOperator.GTE))
                    || (order.isDescending() && (operator == RangeOperator.LT || operator == RangeOperator.LTE))) {
                // Skip this value since the new bound comes from the extracted value
                continue;
            }

            builder.queryParam(entry.getKey(), value);
        }
    }
}
