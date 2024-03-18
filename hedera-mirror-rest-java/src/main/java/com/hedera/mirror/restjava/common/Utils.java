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

import static com.hedera.mirror.restjava.common.Constants.SPLITTER;
import static com.hedera.mirror.restjava.common.Constants.encodedEntityIdRegex;
import static com.hedera.mirror.restjava.common.Constants.entityIdRegex;
import static com.hedera.mirror.restjava.common.Constants.evmAddressRegex;
import static com.hedera.mirror.restjava.common.Constants.evmAddressShardRealmRegex;
import static com.hedera.mirror.restjava.common.Constants.maxEncodedId;
import static com.hedera.mirror.restjava.common.Constants.maxNum;
import static com.hedera.mirror.restjava.common.Constants.maxRealm;
import static com.hedera.mirror.restjava.common.Constants.maxShard;
import static com.hedera.mirror.restjava.common.Constants.numBits;
import static com.hedera.mirror.restjava.common.Constants.realmBits;

import com.hedera.mirror.restjava.exception.InvalidParametersException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;

public class Utils {
    public static final Map<String, RangeOperator> OPERATOR_PATTERNS = Map.of(
            "asc", RangeOperator.gte,
            "desc", RangeOperator.lte);

    public static boolean isValidEntityIdPattern(String id) {
        if (StringUtils.isBlank(id)) {
            return false;
        }
        if (isAMatch(entityIdRegex, id) || isAMatch(encodedEntityIdRegex, id)) {
            return true;
        }
        return isValidEvmAddress(id, Constants.EvmAddressType.ANY);
    }

    private static boolean isAMatch(String regex, String id) {
        Pattern partitionedPattern = Pattern.compile(regex);
        var matcher = partitionedPattern.matcher(id);
        return matcher.matches();
    }

    private static boolean isValidEvmAddress(String address, Constants.EvmAddressType evmAddressType) {

        if (evmAddressType == Constants.EvmAddressType.ANY) {
            return isAMatch(evmAddressRegex, address) || isAMatch(evmAddressShardRealmRegex, address);
        }
        if (evmAddressType == Constants.EvmAddressType.NO_SHARD_REALM) {
            return isAMatch(evmAddressRegex, address);
        }
        return isAMatch(evmAddressShardRealmRegex, address);
    }

    public static long[] parseFromEvmAddress(String evmAddress) {
        // extract shard from index 0->8, realm from 8->23, num from 24->40 and parse from hex to decimal
        var hexDigits = evmAddress.replace("0x", "");
        return new long[] {
            Long.parseLong(hexDigits.substring(0, 8), 16), // shard
            Long.parseLong(hexDigits.substring(8, 24), 16), // realm
            Long.parseLong(hexDigits.substring(24, 40), 16)
        }; // num
    }

    public static long[] parseId(String id) {
        if (isValidEntityIdPattern(id)) {
            var idParts = id.contains(".") || isValidEvmAddressLength(id.length())
                    ? parseDelimitedString(id)
                    : parseFromEncodedId(id);
            if (idParts[2] > maxNum || idParts[1] > maxRealm || idParts[0] > maxShard) {
                throw new InvalidParametersException(id + "- Id has an invalid format");
            }
            return idParts;
        } else {
            throw new InvalidParametersException(id + "- Id has an invalid format");
        }
    }

    private static long[] parseDelimitedString(String id) {
        List<String> parts =
                SPLITTER.splitToStream(Objects.requireNonNullElse(id, "")).toList();
        var numOrEvmAddress = parts.getLast();

        if (isValidEvmAddressLength(numOrEvmAddress.length())) {
            var evmAddress = numOrEvmAddress.replace("0x", "");
            var shardRealmNum = parseFromEvmAddress(numOrEvmAddress);
            var shard = shardRealmNum[0];
            var realm = shardRealmNum[1];
            var num = shardRealmNum[2];
            if (shard > maxShard || realm > maxRealm || num > maxNum) {
                // non-parsable evm address. get id num from evm address here
            } else {
                if (parts.size() == 3
                        && ((Long.parseLong(parts.getFirst()) != shard) || Long.parseLong(parts.get(1)) != realm)) {
                    throw new InvalidParametersException(id + "- Id has an invalid format");
                }
                return new long[] {shard, realm, num};
            }
        }

        // it's either shard.realm.num or realm.num
        if (parts.size() < 3) {
            return new long[] {0, 0, Long.parseLong(parts.getLast())};
        }
        return ArrayUtils.toPrimitive(parts.stream().map(Long::valueOf).toArray(Long[]::new));
    }

    private static long[] parseFromEncodedId(String id) {
        var encodedId = Long.parseLong(id);
        if (encodedId > maxEncodedId) {
            throw new InvalidParametersException(id + "- Id has an invalid format");
        }
        var num = encodedId & maxNum;
        var shardRealm = encodedId >> numBits;
        var realm = shardRealm & maxRealm;
        var shard = shardRealm >> realmBits;
        return new long[] {shard, realm, num};
    }

    public static boolean isValidEvmAddressLength(int len) {
        return len == 40 || len == 42;
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
            var newPattern = order.equals("asc") ? RangeOperator.gt : RangeOperator.lt;
            var insertValue =
                    inclusive ? pattern + ":" + lastValue.getValue() : newPattern + ":" + lastValue.getValue();
            next.append("&").append(lastValue.getKey()).append("=").append(insertValue);
        }
        return next.toString();
    }
}
