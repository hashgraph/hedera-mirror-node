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

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * Configuration class to allow users to specify particular kind of distribution to use when generating test data for
 * some field. This leverages Spring boot's nested configuration binding. If no distribution is specified, defaults to
 * constant value of 0.
 */
@Data
public class NumberDistributionConfig implements Distribution<Long> {
    /**
     * Builds FrequencyDistribution<Long> Key can be any Long. Value should be a number between 1-1000 and denotes
     * frequency distribution of the key. Values should add up to 1000.
     */
    private Map<Long, Integer> frequency = new HashMap<>();
    /**
     * Builds RandomDistributionFromRange
     */
    private long rangeMin = 0;
    private long rangeMax = 0;
    /**
     * Builds ConstantValueDistribution<Long>.
     */
    private long constant;
    private Distribution<Long> distribution;

    public NumberDistributionConfig() {
        constant = 0;
    }

    public NumberDistributionConfig(long defaultValue) {
        constant = defaultValue;
    }

    public void initDistribution() {
        if (frequency.size() != 0) {
            distribution = new FrequencyDistribution<>(frequency);
        } else if (rangeMin != rangeMax) {
            distribution = new RandomDistributionFromRange(rangeMin, rangeMax);
        } else {
            distribution = new ConstantValueDistribution<>(constant);
        }
    }

    @Override
    public Long sample() {
        return distribution.sample();
    }
}
