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

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@CustomLog
@UtilityClass
public class Utils {

    public static String getPaginationLink(boolean isEnd, Map<String, String> lastValues, Sort.Direction order) {

        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (servletRequestAttributes == null) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        StringBuilder paginationLink = new StringBuilder();

        if (lastValues == null || lastValues.isEmpty()) {
            return null;
        }

        if (!isEnd) {
            paginationLink.append(generateLinkFromRequestParams(lastValues, order, request));
        }
        return paginationLink.isEmpty() ? null : paginationLink.toString();
    }

    private static String generateLinkFromRequestParams(
            Map<String, String> lastValues, Sort.Direction order, HttpServletRequest request) {

        StringBuilder link = new StringBuilder();

        var paramsMap = request.getParameterMap();
        var url = request.getRequestURI();

        // remove the '/' at the end of req.path
        var path = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        link.append(path);

        if (!paramsMap.isEmpty() || !lastValues.isEmpty()) {
            link.append("?");
        }

        // add all params from the existing url
        int count = 0; // This is to check for first parameter
        int last = 0;
        boolean inclusive = true;
        List<String> paramsNeeded = new ArrayList<>();

        for (Map.Entry<String, String[]> param : paramsMap.entrySet()) {
            String key;
            String value;
            if (count != 0) {
                link.append("&");
            }
            key = param.getKey();
            if (lastValues.containsKey(param.getKey())) {
                if (++last == lastValues.size()) {
                    inclusive = false;
                }
                paramsNeeded.add(key);
                value = getNextValue(order, lastValues, key, inclusive);
            } else {
                value = param.getValue()[0];
            }
            count++;
            link.append("%s=".formatted(key)).append(value);
        }
        addLastValueElements(lastValues, order, paramsNeeded, count, link, last, inclusive);
        return link.toString();
    }

    private static void addLastValueElements(
            Map<String, String> lastValues,
            Sort.Direction order,
            List<String> paramsNeeded,
            int count,
            StringBuilder link,
            int last,
            boolean inclusive) {
        if (paramsNeeded.size() != lastValues.size()) {
            for (String key : lastValues.keySet()) {
                if (!paramsNeeded.contains(key)) {
                    if (count++ != 0) {
                        link.append("&");
                    }
                    if (++last == lastValues.size()) {
                        inclusive = false;
                    }
                    link.append("%s=".formatted(key)).append(getNextValue(order, lastValues, key, inclusive));
                }
            }
        }
    }

    private static String getNextValue(
            Sort.Direction order, Map<String, String> lastValues, String key, boolean inclusive) {
        var pattern = order == Sort.Direction.ASC ? RangeOperator.GTE : RangeOperator.LTE;
        var newPattern = order == Sort.Direction.ASC ? RangeOperator.GT : RangeOperator.LT;
        if (inclusive) {
            return pattern + ":" + lastValues.get(key);
        } else {
            return newPattern + ":" + lastValues.get(key);
        }
    }
}
