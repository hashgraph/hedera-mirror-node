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

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Configuration class to allow users to specify particular kind of distribution to use when generating fake data for
 * some field. This leverages Spring boot's nested configuration binding. If no distribution is specified, throws
 * runtime error during application start.
 */
@Data
public class StringDistributionConfig implements Distribution<String> {

    /**
     * Builds FrequencyDistribution<String> Key can be any string. Value should be a number between 1-1000 and denotes
     * frequency distribution of the key. Values should add up to 1000.
     */
    private Map<String, Integer> frequency = new HashMap<>();

    /**
     * Builds ConstantValueDistribution<String>
     */
    private String constant;

    private Distribution<String> distribution;

    public void initDistribution() {
        if (frequency.size() != 0) {
            distribution = new FrequencyDistribution<>(frequency);
        }
        if (constant != null) {
            distribution = new ConstantValueDistribution<>(constant);
        }
        throw new IllegalArgumentException("Specify one of 'constant' or 'frequency'");
    }

    @Override
    public String sample() {
        return distribution.sample();
    }
}
