package com.hedera.faker.common;
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

import javax.annotation.PostConstruct;
import javax.inject.Named;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.faker.sampling.NumberDistributionConfig;

/**
 * Fields' generators and properties used by CryptoTransactionGenerator.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Named
@ConfigurationProperties("faker.transaction.crypto")
public class CryptoTransactionProperties {

    private final NumberDistributionConfig numTransferLists = new NumberDistributionConfig();

    /**
     * CRYPTOCREATEACCOUNT transactions per thousand crypto transactions
     */
    private int createsPerThousand;

    /**
     * CRYPTOTRANSFER transactions per thousand crypto transactions
     */
    private int transfersPerThousand;

    /**
     * CRYPTOUPDATEACCOUNT transactions per thousand crypto transactions
     */
    private int updatesPerThousand;

    /**
     * CRYPTODELETE transactions per thousand crypto transactions
     */
    private int deletesPerThousand;

    /**
     * When generating transactions, first 'numSeedAccounts' number of transactions will be of type CRYPTOCREATEACCOUNT
     * only. This is to seed the system with some accounts so crypto transfer lists can sample receiver accounts ids.
     */
    private int numSeedAccounts;

    @PostConstruct
    void initDistributions() {
        numTransferLists.initDistribution();
    }
}
