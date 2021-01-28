package com.hedera.datagenerator.sampling;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.security.SecureRandom;
import java.util.Random;
import lombok.RequiredArgsConstructor;

/**
 * Returns integers sampled randomly from the domain [min, max).
 */
@RequiredArgsConstructor
public class RandomDistributionFromRange implements Distribution<Long> {
    private long min;
    private long range;
    private Random random;

    public RandomDistributionFromRange(long min, long max) {
        if (max < min) {
            throw new IllegalArgumentException("max(" + max + ") should be greater than min(" + min + ")");
        }
        this.min = min;
        range = max - min;
        random = new SecureRandom();
    }

    @Override
    public Long sample() {
        return min + random.nextInt((int) range);
    }
}
