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

import com.hedera.mirror.web3.exception.InvalidBlockTypeException;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public record BlockType(String name, long number) {

    private enum BlockTypeName {
        EARLIEST("earliest"),
        LATEST("latest"),
        PENDING("pending"),
        SAFE("safe"),
        FINALIZED("finalized");

        private final String name;

        BlockTypeName(final String name) {
            this.name = name;
        }
    }

    private static final String HEX_PREFIX = "0x";
    public static final BlockType LATEST = new BlockType(BlockTypeName.LATEST.name, Long.MAX_VALUE);

    private static final BlockTypeName[] UNSUPPORTED_TYPES = {
            BlockTypeName.PENDING,
            BlockTypeName.SAFE,
            BlockTypeName.FINALIZED
    };

    public static BlockType of(final String value) {
        if (StringUtils.isEmpty(value)) {
            return LATEST;
        }

        try {
            final BlockTypeName blockTypeName = BlockTypeName.valueOf(value.toUpperCase());
            switch (blockTypeName) {
                case EARLIEST -> {
                    return new BlockType(BlockTypeName.EARLIEST.name, 0L);
                }
                case LATEST, PENDING, SAFE, FINALIZED -> {
                    return new BlockType(blockTypeName.name, Long.MAX_VALUE);
                }
            }
        } catch (IllegalArgumentException e) {
            // The value is not in the enum -> check for passed block number.
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

    public static boolean isSupported(String blockType) {
        return Arrays.stream(UNSUPPORTED_TYPES)
                .noneMatch(unsupportedType -> unsupportedType.name().equalsIgnoreCase(blockType));
    }
}
