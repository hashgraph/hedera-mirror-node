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

import static com.hedera.mirror.restjava.common.Constants.ENTITY_ID_PATTERN;
import static com.hedera.mirror.restjava.common.Constants.SPLITTER;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.restjava.exception.InvalidParametersException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;

@CustomLog
@UtilityClass
public class Utils {

    public static final Map<String, RangeOperator> OPERATOR_PATTERNS = Map.of(
            "asc", RangeOperator.GTE,
            "desc", RangeOperator.LTE);

    public static EntityId parseId(String id) {

        if (StringUtils.isBlank(id)) {
            throw new InvalidParametersException(" Id '%s' has an invalid format".formatted(id));
        }
        Matcher entityIdMatcher = ENTITY_ID_PATTERN.matcher(id);

        if (entityIdMatcher.matches()) {
            // Group 0 is for shard.realm.num entityId
            if (entityIdMatcher.group(1) != null || entityIdMatcher.group(3) != null) {
                return parseDelimitedString(id);
            } else {
                // change this with evm address and alias support
                throw new InvalidParametersException("Id format is not yet supported");
            }
        } else {
            throw new InvalidParametersException(" Id '%s' has an invalid format".formatted(id));
        }
    }

    private static EntityId parseDelimitedString(String id) {

        long shard = 0;
        long realm = 0;
        List<String> parts =
                SPLITTER.splitToStream(Objects.requireNonNullElse(id, "")).toList();
        var numOrEvmAddress = parts.getLast();

        try {
            switch (parts.size()) {
                case 1 -> {
                    shard = 0;
                }
                case 2 -> {
                    shard = 0;
                    realm = Long.parseLong(parts.getFirst());
                }
                case 3 -> {
                    shard = Long.parseLong(parts.getFirst());
                    realm = Long.parseLong(parts.get(1));
                }
            }

            return EntityId.of(shard, realm, Long.parseLong(numOrEvmAddress));
        } catch (InvalidEntityException e) {
            throw new InvalidParametersException(e.getMessage());
        }
    }

    public static String getPaginationLink(
            HttpServletRequest req,
            boolean isEnd,
            Map<String, String> lastValues,
            Map<String, Boolean> included,
            Sort.Direction order,
            int limit) {
        var url = req.getRequestURI();
        StringBuilder paginationLink = new StringBuilder();

        if (lastValues == null || lastValues.isEmpty()) {
            return null;
        }

        if (!isEnd) {
            // add limit and order
            var orderString = order == null
                    ? Sort.Direction.ASC.toString().toLowerCase()
                    : order.toString().toLowerCase();
            var next = getNextParamQueries(orderString, lastValues, included);
            if (next.isBlank()) {
                return null;
            }
            // remove the '/' at the end of req.path
            var path = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            paginationLink.append(path);
            paginationLink.append("?limit=").append(limit);
            paginationLink.append("&order=").append(orderString);
            paginationLink.append(next);
        }
        return paginationLink.isEmpty() ? null : paginationLink.toString();
    }

    private static String getNextParamQueries(
            String order, Map<String, String> lastValues, Map<String, Boolean> includedMap) {
        StringBuilder next = new StringBuilder();
        for (Map.Entry<String, String> lastValue : lastValues.entrySet()) {
            boolean inclusive = includedMap.get(lastValue.getKey());
            var pattern = OPERATOR_PATTERNS.get(order);
            var newPattern = order.equals("asc") ? RangeOperator.GT : RangeOperator.LT;
            var insertValue =
                    inclusive ? pattern + ":" + lastValue.getValue() : newPattern + ":" + lastValue.getValue();
            next.append("&").append(lastValue.getKey()).append("=").append(insertValue.toLowerCase());
        }
        return next.toString();
    }
}
