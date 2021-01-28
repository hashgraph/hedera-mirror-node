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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Generates frequency distribution of values. For a distribution specified by A:10, B:1000, C:100, creates a shuffled
 * array with each element's occurrence count equal to its frequency. sample() returns values from this array.
 */
@RequiredArgsConstructor
public class FrequencyDistribution<T> implements Distribution<T> {
    private List<T> randomizedSamples;
    private int index;
    private int totalFrequency;

    public FrequencyDistribution(Map<T, Integer> distribution) {
        index = -1;
        totalFrequency = 0;
        for (Map.Entry<T, Integer> entry : distribution.entrySet()) {
            totalFrequency += entry.getValue();
        }
        randomizedSamples = new ArrayList<>(totalFrequency);
        for (Map.Entry<T, Integer> entry : distribution.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                randomizedSamples.add(entry.getKey());
            }
        }
        Collections.shuffle(randomizedSamples);
    }

    @Override
    public T sample() {
        index = (index + 1) % totalFrequency;
        return randomizedSamples.get(index);
    }
}
