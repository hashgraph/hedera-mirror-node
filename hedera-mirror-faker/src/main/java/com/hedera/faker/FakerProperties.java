package com.hedera.faker;
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

import com.hedera.faker.sampling.NumberDistributionConfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import javax.annotation.PostConstruct;
import javax.inject.Named;

/**
 * Set of common properties between 'domain' and 'proto' drivers.
 */
@Data
@Named
@ConfigurationProperties("faker")
public class FakerProperties {
    private NumberDistributionConfig transactionsPerSecond = new NumberDistributionConfig();

    /** Start time for the fake transactions. Used for consensus timestamp field. */
    private int startTimeSec = 1577836800;  // 1 Jan 2020, 00:00:00

    /** Fake transactions will be generated for time period from startTimeSec to startTimeSec + totalTimeSec. */
    private int totalTimeSec = 3600 * 24;  // 1 hr

    private int balancesFileDurationSec = 15 * 60;  // 15 min

    @PostConstruct
    void initDistributions() {
        transactionsPerSecond.initDistribution();
    }
}
