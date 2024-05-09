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

package com.hedera.mirror.restjava.controller;

import static com.hedera.mirror.restjava.common.ParameterNames.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.ParameterNames.LIMIT;
import static com.hedera.mirror.restjava.common.ParameterNames.ORDER;
import static com.hedera.mirror.restjava.common.ParameterNames.OWNER;
import static com.hedera.mirror.restjava.common.ParameterNames.TOKEN_ID;

import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.restjava.common.LinkFactory;
import com.hedera.mirror.restjava.common.RangeOperator;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
public class AllowancesLinkFactory implements LinkFactory {

    private final HttpServletRequest request;

    @Override
    public <T> Links create(T item, ParameterExtractor<T> extractor) {
        var links = new Links();
        if (item == null) {
            return links;
        }

        var paramsMap = request.getParameterMap();
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        boolean owner =
                paramsMap.containsKey(OWNER) ? Boolean.parseBoolean(paramsMap.get(OWNER)[0]) : true;
        var order = paramsMap.containsKey(ORDER)
                ? Sort.Direction.fromString(paramsMap.get(ORDER)[0])
                : Sort.Direction.ASC;

        boolean accountIdQueryAdded = false;
        boolean tokenQueryAdded = false;
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            switch (key) {
                case LIMIT -> builder.queryParam(LIMIT, paramsMap.get(LIMIT)[0]);
                case ORDER -> builder.queryParam(ORDER, order.name().toLowerCase());
                case OWNER -> builder.queryParam(OWNER, owner);
                case ACCOUNT_ID -> {
                    var accountExtractor = owner ? extractor.extract(ACCOUNT_ID) : extractor.extract(OWNER);
                    accountIdQueryAdded =
                            addQueryParam(accountExtractor, item, entry, builder, order, ACCOUNT_ID, true);
                }
                case TOKEN_ID -> tokenQueryAdded =
                        addQueryParam(extractor.extract(TOKEN_ID), item, entry, builder, order, TOKEN_ID, false);
                default -> {
                    // Ignore unknown parameters
                }
            }
        }

        if (order.isDescending()) {
            var accountIdValue = extractor.extract(ACCOUNT_ID).apply(item);
            var upperBound = RangeOperator.LTE + ":" + accountIdValue;
            builder.queryParam(ACCOUNT_ID, upperBound);

            var tokenValue = extractor.extract(TOKEN_ID).apply(item);
            var upperBoundToken = RangeOperator.LT + ":" + tokenValue;
            builder.queryParam(TOKEN_ID, upperBoundToken);
        } else {
            if (!accountIdQueryAdded) {
                var accountIdValue = extractor.extract(ACCOUNT_ID).apply(item);
                builder.queryParam(ACCOUNT_ID, RangeOperator.GTE + ":" + accountIdValue);
            }

            if (!tokenQueryAdded) {
                var tokenValue = extractor.extract(TOKEN_ID).apply(item);
                builder.queryParam(TOKEN_ID, RangeOperator.GT + ":" + tokenValue);
            }
        }

        return links.next(builder.toUriString());
    }
}
