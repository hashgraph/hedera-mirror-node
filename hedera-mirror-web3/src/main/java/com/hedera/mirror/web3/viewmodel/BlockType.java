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

import com.hedera.mirror.web3.exception.UnsupportedBlockTypeException;
import org.apache.commons.lang3.StringUtils;

public record BlockType(String name, long number) {

    private static final String HEX_PREFIX = "0x";

    public static final BlockType EARLIEST = new BlockType("earliest", 0L);
    public static final BlockType LATEST = new BlockType("latest", Long.MAX_VALUE);
    public static final BlockType UNSUPPORTED = new BlockType("", 0L);

    public static BlockType of(final String value) throws UnsupportedBlockTypeException {
        if (StringUtils.isEmpty(value)) {
            return LATEST;
        }

        final String blockTypeName = value.toLowerCase();
        switch (blockTypeName) {
            case "earliest" -> {
                return EARLIEST;
            }
            case "latest" -> {
                return LATEST;
            }
            case "safe", "pending", "finalized" -> {
                return UNSUPPORTED;
            }
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

    public static void validateBlockTypeIsSupported(BlockType value) {
        if (value == UNSUPPORTED) {
            throw new UnsupportedBlockTypeException("Unsupported block type passed.");
        }
    }
}
