package com.hedera.mirror.common.aggregator;

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

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

@NoArgsConstructor
public class LogsBloomAggregator {

    public static final int BYTE_SIZE = 256;
    private byte[] aggregatedBlooms = ArrayUtils.EMPTY_BYTE_ARRAY;

    public LogsBloomAggregator aggregate(byte[] bloom) {
        if (bloom == null) {
            return this;
        }
        if (aggregatedBlooms.length == 0) {
            aggregatedBlooms = new byte[BYTE_SIZE];
        }
        for (int i = 0; i < bloom.length; i++) {
            aggregatedBlooms[i] |= bloom[i];
        }
        return this;
    }

    public byte[] getBloom() {
        return aggregatedBlooms;
    }

    public boolean couldContain(byte[] bloom) {
        if (bloom == null) {
            // other implementations accept null values as positive matches.
            return true;
        }
        if (aggregatedBlooms.length == 0) {
            return false;
        }
        if (bloom.length != BYTE_SIZE) {
            return false;
        }

        for (int i = 0; i < bloom.length; i++) {
            if ((bloom[i] & aggregatedBlooms[i]) != bloom[i]) {
                return false;
            }
        }
        return true;
    }
}
