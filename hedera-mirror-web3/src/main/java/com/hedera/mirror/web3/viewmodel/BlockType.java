/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.viewmodel;

import org.apache.commons.lang3.StringUtils;

public record BlockType(String name, long number) {

    private static final String HEX_PREFIX = "0x";

    public static final BlockType EARLIEST = new BlockType("earliest", 0L);
    public static final BlockType LATEST = new BlockType("latest", Long.MAX_VALUE);

    public static BlockType of(final String value) {
        if (StringUtils.isEmpty(value)) {
            return LATEST;
        }

        final String blockTypeName = value.toLowerCase();
        switch (blockTypeName) {
            case "earliest" -> {
                return EARLIEST;
            }
            case "latest", "safe", "pending", "finalized" -> {
                return LATEST;
            }
            default -> {
                return extractNumericBlock(value);
            }
        }
    }

    private static BlockType extractNumericBlock(String value) {
        int radix = 10;
        var cleanedValue = value;

        if (value.startsWith(HEX_PREFIX)) {
            radix = 16;
            cleanedValue = StringUtils.removeStart(value, HEX_PREFIX);
        }

        try {
            long blockNumber = Long.parseLong(cleanedValue, radix);
            if (blockNumber < 0) {
                throw new IllegalArgumentException("Invalid block value: " + value);
            }
            return new BlockType(value, blockNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid block value: " + value, e);
        }
    }
}
