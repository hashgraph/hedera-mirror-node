package com.hedera.datagenerator;
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

import java.time.Duration;
import java.time.Instant;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.datagenerator.sampling.NumberDistributionConfig;

/**
 * Set of common properties between 'domain' and 'proto' drivers.
 */
@Data
@Named
@ConfigurationProperties("hedera.mirror.datagenerator")
public class DataGeneratorProperties {
    /**
     * Start time for the transactions. Used for consensus timestamp field.
     */
    private long startTimeSec = Instant.EPOCH.getEpochSecond();

    /**
     * Transactions will be generated for time period from startTimeSec to startTimeSec + totalDuration.
     */
    private Duration totalDuration = Duration.ofHours(1);

    private NumberDistributionConfig transactionsPerSecond = new NumberDistributionConfig();

    private Duration balancesFileDuration = Duration.ofMinutes(15);

    @PostConstruct
    void initDistributions() {
        transactionsPerSecond.initDistribution();
    }
}
