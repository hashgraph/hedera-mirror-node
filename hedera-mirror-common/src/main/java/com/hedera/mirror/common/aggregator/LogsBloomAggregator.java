package com.hedera.mirror.common.aggregator;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.util.Arrays;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class LogsBloomAggregator {

    public static final int BYTE_SIZE = 256;
    private final byte[] logsBloom = new byte[BYTE_SIZE];

    public LogsBloomAggregator insertBytes(final byte[] bloom) {
        if (bloom != null) {
            for (int i = 0; i < bloom.length; i++) {
                logsBloom[i] |= bloom[i];
            }
        }
        return this;
    }

    public byte[] getBloom() {
        return Arrays.copyOf(logsBloom, logsBloom.length);
    }
}

// 0111 1111 = 127

// 1000 0001 = -127

// 0111 1111 = 127
