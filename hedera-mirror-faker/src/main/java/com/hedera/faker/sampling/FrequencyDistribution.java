package com.hedera.faker.sampling;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Generates frequency distribution of values. Frequencies are expressed in either parts-per-thousand,
 * parts-per-ten-thousand or parts-per-million.
 */
@RequiredArgsConstructor
public class FrequencyDistribution<T> implements Distribution<T> {
    private final int cumulativeFrequency;
    private List<Pair<Integer, T>> cumulativeFrequencyDistribution;
    private Random random;

    public FrequencyDistribution(Map<T, Integer> distribution) {
        int cf = 0;
        cumulativeFrequencyDistribution = new ArrayList<>(distribution.size());
        for (Map.Entry<T, Integer> entry : distribution.entrySet()) {
            cf += entry.getValue();
            cumulativeFrequencyDistribution.add(Pair.of(cf, entry.getKey()));
        }
        if (cf != 1000 && cf != 10000 && cf != 1000000) {
            throw new IllegalArgumentException(
                    "Frequencies should be either parts-per-thousand, parts-per-ten-thousand, or parts-per-million. " +
                            "The cumulative frequency should add upto 1000 or 10000 or 1million. Found " + cf);
        }
        cumulativeFrequency = cf;
        random = new Random();
    }

    @Override
    public T sample() {
        int roll = random.nextInt(cumulativeFrequency);
        for (var entry : cumulativeFrequencyDistribution) {
            if (roll < entry.getLeft()) {
                return entry.getRight();
            }
        }
        throw new IllegalStateException(
                "Should not reach here since cumulativeFrequencyDistribution should add to " + cumulativeFrequency);
    }
}
