package com.hedera.mirror.web3.viewmodel;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.apache.commons.lang3.StringUtils;

public record BlockType(String name, long number) {

    public static final BlockType EARLIEST = new BlockType("earliest", 0L);
    public static final BlockType LATEST = new BlockType("latest", Long.MAX_VALUE);
    public static final BlockType PENDING = new BlockType("pending", Long.MAX_VALUE);
    private static final String HEX_PREFIX = "0x";

    public static BlockType of(final String value) {

        if (StringUtils.isEmpty(value) || BlockType.LATEST.name().equalsIgnoreCase(value)) {
            return BlockType.LATEST;
        } else if (BlockType.EARLIEST.name().equalsIgnoreCase(value)) {
            return BlockType.EARLIEST;
        } else if (BlockType.PENDING.name().equalsIgnoreCase(value)) {
            return BlockType.PENDING;
        }

        int radix = 10;
        var cleanedValue = value;

        if (value.startsWith(HEX_PREFIX)) {
            radix = 16;
            cleanedValue = StringUtils.removeStart(value, HEX_PREFIX);
        }

        try {
            long blockNumber = Long.parseLong(cleanedValue, radix);
            return new BlockType(value, blockNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid block value: " + value, e);
        }
    }
}
