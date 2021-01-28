package com.hedera.datagenerator.common;
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

import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.Data;

import com.hedera.datagenerator.sampling.NumberDistributionConfig;

@Data
@Named
public class TopicTransactionProperties {

    /**
     * Relative frequency of topic type transactions among all generated transactions.
     */
    private int frequency;

    /**
     * Relative frequency of CONSENSUSCREATETOPIC transactions
     */
    private int createsFrequency;

    /**
     * Relative frequency of CONSENSUSDELETETOPIC transactions
     */
    private int deletesFrequency;

    /**
     * Relative frequency of CONSENSUSUPDATETOPIC transactions
     */
    private int updatesFrequency;

    /**
     * Relative frequency of CONSENSUSSUBMITMESSAGE transactions
     */
    private int submitMessageFrequency;

    /**
     * When generating transactions, first 'numSeedTopics' number of transactions will be of type CONSENSUSCREATETOPIC
     * only. This is to seed the system with some topics for deletes/updates/submitMessage.
     */
    private int numSeedTopics;

    private final NumberDistributionConfig messageSize = new NumberDistributionConfig();

    void initDistributions() {
        messageSize.initDistribution();
    }
}
