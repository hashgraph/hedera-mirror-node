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
import static com.hedera.mirror.restjava.common.Constants.EVM_ADDRESS_MIN_LENGTH;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@CustomLog
@UtilityClass
public class Utils {

    public static final Map<String, RangeOperator> OPERATOR_PATTERNS = Map.of(
            "ASC", RangeOperator.GTE,
            "DESC", RangeOperator.LTE);

    public static EntityId parseId(String id) {

        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException(" Id '%s' has an invalid format".formatted(id));
        }

        if (id.length() < EVM_ADDRESS_MIN_LENGTH) {
            return parseEntityId(id);
        } else {
            throw new IllegalArgumentException("Unsupported ID: " + id);
        }
    }

    private static EntityId parseEntityId(String id) {

        var matcher = ENTITY_ID_PATTERN.matcher(id);
        long shard = 0;
        long realm = 0;
        String numOrEvmAddress;
        if (matcher.matches()) {

            if (matcher.group(4) != null) {
                realm = Long.parseLong(matcher.group(5));
                if (matcher.group(2) == null) {
                    // get the system shard value from properties
                    shard = 0;
                } else {
                    shard = Long.parseLong(matcher.group(3));
                }
            } else if (matcher.group(2) != null) {
                realm = Long.parseLong(matcher.group(3));
            }

            numOrEvmAddress = matcher.group(6);

        } else {
            throw new IllegalArgumentException("Id %s format is invalid".formatted(id));
        }

        return EntityId.of(shard, realm, Long.parseLong(numOrEvmAddress));
    }

    public static String getPaginationLink(
            boolean isEnd, Map<String, String> lastValues, Map<String, Boolean> included, Sort.Direction order) {

        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (servletRequestAttributes == null) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        var paramsMap = request.getParameterMap();
        var url = request.getRequestURI();
        StringBuilder paginationLink = new StringBuilder();

        if (lastValues == null || lastValues.isEmpty()) {
            return null;
        }

        if (!isEnd) {
            // remove the '/' at the end of req.path
            generateLinkFromRequestParams(lastValues, included, order, url, paginationLink, paramsMap);
        }
        return paginationLink.isEmpty() ? null : paginationLink.toString();
    }

    private static void generateLinkFromRequestParams(
            Map<String, String> lastValues,
            Map<String, Boolean> included,
            Sort.Direction order,
            String url,
            StringBuilder paginationLink,
            Map<String, String[]> paramsMap) {
        var path = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        paginationLink.append(path);
        if (!paramsMap.isEmpty() || !lastValues.isEmpty()) {
            paginationLink.append("?");
        }
        // add all params from the existing url
        int count = 0; // This is to check for first parameter
        for (Map.Entry<String, String[]> param : paramsMap.entrySet()) {
            if (!lastValues.containsKey(param.getKey())) {
                if (count != 0) {
                    paginationLink.append("&");
                }
                paginationLink.append("%s=".formatted(param.getKey())).append(param.getValue()[0]);
                count++;
            }
        }
        var next = getNextParamQueries(order, lastValues, included);
        if (paramsMap.isEmpty()) {
            paginationLink.append(next.substring(1));
        } else {
            paginationLink.append(next);
        }
    }

    private static String getNextParamQueries(
            Sort.Direction order, Map<String, String> lastValues, Map<String, Boolean> includedMap) {
        StringBuilder next = new StringBuilder();
        for (Map.Entry<String, String> lastValue : lastValues.entrySet()) {
            boolean inclusive = includedMap.get(lastValue.getKey());
            var pattern = OPERATOR_PATTERNS.get(order.name());
            var newPattern = order == Sort.Direction.ASC ? RangeOperator.GT : RangeOperator.LT;
            var insertValue =
                    inclusive ? pattern + ":" + lastValue.getValue() : newPattern + ":" + lastValue.getValue();
            next.append("&").append(lastValue.getKey()).append("=").append(insertValue.toLowerCase());
        }
        return next.toString();
    }
}
