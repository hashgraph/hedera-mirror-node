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

import static com.hedera.mirror.restjava.common.Constants.BASE32;
import static com.hedera.mirror.restjava.common.Constants.ENTITY_ID_PATTERN;
import static com.hedera.mirror.restjava.common.Constants.EVM_ADDRESS_MIN_LENGTH;
import static com.hedera.mirror.restjava.common.Constants.EVM_ADDRESS_PATTERN;
import static com.hedera.mirror.restjava.common.Constants.HEX_PREFIX;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@CustomLog
@UtilityClass
public class Utils {

    public static EntityId parseId(String id) {

        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException(" Id '%s' has an invalid format".formatted(id));
        }

        if (id.length() < EVM_ADDRESS_MIN_LENGTH) {
            return parseEntityId(id);
        } else {
            return parseEvmAddressOrAlias(id);
        }
    }

    private static EntityId parseEvmAddressOrAlias(String id) {

        var matcher = EVM_ADDRESS_PATTERN.matcher(id);
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

    private static EntityId parseEntityId(String id) {

        var matcher = ENTITY_ID_PATTERN.matcher(id);
        long shard = 0;
        long realm = 0;

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Id %s format is invalid".formatted(id));
        }
        // This matched the format realm.
        if (matcher.group(3) != null) {
            // This gets the realm value
            realm = Long.parseLong(matcher.group(4));
            if (matcher.group(1) != null) {
                // This will get the matched shard value
                shard = Long.parseLong(matcher.group(2));
            }
        } else if (matcher.group(1) != null) {
            realm = Long.parseLong(matcher.group(2));
            // get this value from system property
            shard = 0;
        }

        var num = matcher.group(5);

        return EntityId.of(shard, realm, Long.parseLong(num));
    }

    public static byte[] decodeBase32(String base32) {
        return BASE32.decode(base32);
    }

    public static byte[] decodeEvmAddress(String evmAddress) {
        if (evmAddress == null) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        try {
            evmAddress = StringUtils.removeStart(evmAddress, HEX_PREFIX);
            return Hex.decodeHex(evmAddress);
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Unable to decode evmAddress: " + evmAddress);
        }
    }

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
